package com.helios.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.helios.assistant.data.*
import com.helios.assistant.automation.DeviceActions
import com.helios.assistant.voice.SpeechController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    private lateinit var dao: ChatDao
    private lateinit var speech: SpeechController
    private lateinit var actions: DeviceActions
    private lateinit var localAi: LocalAiEngine
    private var voiceState by mutableStateOf("Pronto")
    private var lastReply by mutableStateOf("")
    private var modelState by mutableStateOf("Modelo local não instalado")
    private var modelDownloadProgress by mutableStateOf(0f)
    private val requestMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.listen() else voiceState = "Permissão de microfone negada"
    }
    private val pickModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importModel(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dao = HeliosDatabase.get(this).chatDao()
        actions = DeviceActions(this)
        localAi = LocalAiEngine()
        val modelFile = File(filesDir, "models/helios-model.litertlm")
        if (modelFile.exists()) modelState = "Modelo instalado • ${modelFile.length() / (1024 * 1024)} MB"
        speech = SpeechController(this, { text -> handleUserText(text) }, { state -> voiceState = state })
        setContent {
            val messages by dao.observeAll().collectAsState(initial = emptyList())
            HeliosScreen(
                messages = messages,
                state = voiceState,
                modelStatus = modelState,
                downloadProgress = modelDownloadProgress,
                onSend = { handleUserText(it) },
                onMic = {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech.listen()
                    else requestMic.launch(Manifest.permission.RECORD_AUDIO)
                },
                onStop = { speech.stop() },
                onRepeat = { if (lastReply.isNotBlank()) speech.speak(lastReply) },
                onPickModel = { pickModel.launch(arrayOf("*/*")) },
                onDownloadModel = { downloadRecommendedModel() },
                onClearHistory = { clearConversationHistory() }
            )
        }
    }

    private fun importModel(uri: Uri) {
        lifecycleScope.launch {
            modelState = "Importando modelo…"
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val name = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                    } ?: "modelo"
                    require(name.endsWith(".litertlm", ignoreCase = true)) { "Escolha um arquivo .litertlm compatível." }
                    val folder = File(filesDir, "models").apply { mkdirs() }
                    val temp = File(folder, "helios-model.tmp")
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Não foi possível abrir o arquivo selecionado." }
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                    val target = File(folder, "helios-model.litertlm")
                    if (target.exists()) target.delete()
                    check(temp.renameTo(target)) { "Não foi possível salvar o modelo." }
                    target
                }
            }
            result.onSuccess { file ->
                modelState = "Modelo instalado • ${file.length() / (1024 * 1024)} MB"
                respond("Modelo local importado. Na próxima mensagem vou inicializar a IA no próprio celular.")
            }.onFailure {
                modelState = "Falha ao importar modelo"
                respond("Não consegui importar o modelo: ${it.message ?: "arquivo inválido"}.")
            }
        }
    }


    private fun downloadRecommendedModel() {
        lifecycleScope.launch {
            modelState = "Preparando download do Qwen3…"
            modelDownloadProgress = 0f
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val folder = File(filesDir, "models").apply { mkdirs() }
                    val temp = File(folder, "helios-model.download")
                    val url = URL("https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm")
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20000
                        readTimeout = 60000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "Helios-Android")
                    }
                    try {
                        connection.connect()
                        check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                            "Servidor retornou HTTP ${connection.responseCode}"
                        }
                        val total = connection.contentLengthLong
                        if (total > 0) check(total in (250L * 1024 * 1024)..(450L * 1024 * 1024)) {
                            "O tamanho recebido não corresponde ao modelo esperado."
                        }
                        var copied = 0L
                        connection.inputStream.use { input ->
                            FileOutputStream(temp).use { output ->
                                val buffer = ByteArray(1024 * 1024)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    copied += count
                                    if (total > 0) {
                                        val progress = (copied.toFloat() / total).coerceIn(0f, 1f)
                                        runOnUiThread { modelDownloadProgress = progress }
                                    }
                                }
                            }
                        }
                        check(temp.length() >= 250L * 1024 * 1024) {
                            "Download incompleto. Tente novamente com uma conexão estável."
                        }
                        val target = File(folder, "helios-model.litertlm")
                        if (target.exists()) target.delete()
                        check(temp.renameTo(target)) { "Não foi possível finalizar a instalação do modelo." }
                        target
                    } finally {
                        connection.disconnect()
                    }
                }
            }
            result.onSuccess { file ->
                modelDownloadProgress = 1f
                modelState = "Qwen3 instalado • ${file.length() / (1024 * 1024)} MB"
                voiceState = "IA local pronta"
            }.onFailure {
                modelState = "Falha no download: ${it.message ?: "erro desconhecido"}"
                File(filesDir, "models/helios-model.download").delete()
            }
        }
    }

    private fun handleUserText(raw: String) {
        val text = raw.trim()
        if (text.isBlank()) return
        val normalized = text.lowercase(Locale("pt", "BR"))
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized == "tchau helios" || normalized == "tchau elios") {
            speech.endSession()
            voiceState = "Sessão encerrada"
            return
        }
        lifecycleScope.launch {
            dao.insert(ChatMessage(role = "user", content = text))
            val lower = text.lowercase(Locale("pt", "BR"))
            when {
                lower.contains("que horas") || lower.contains("horário") ->
                    respond("Agora são ${SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date())}.")
                lower.contains("que dia") || lower.contains("data de hoje") ->
                    respond("Hoje é ${SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("pt", "BR")).format(Date())}.")
                lower.contains("abra o youtube") || lower == "youtube" -> {
                    runCatching { actions.openYouTube() }
                    respond("Abrindo o YouTube.")
                }
                lower.startsWith("pesquise ") || lower.startsWith("pesquisar ") || lower.startsWith("pesquisa ") -> {
                    val q = text.substringAfter(' ')
                    actions.searchWeb(q)
                    respond("Abrindo uma pesquisa sobre $q.")
                }
                lower.contains("configurações") -> {
                    actions.openSettings()
                    respond("Abrindo as configurações do Android.")
                }
                else -> {
                    val modelFile = File(filesDir, "models/helios-model.litertlm")
                    if (!modelFile.exists()) {
                        respond("A IA local ainda não tem um modelo instalado. Abra Configurações e toque em IMPORTAR MODELO para selecionar um arquivo .litertlm.")
                    } else {
                        voiceState = "IA LOCAL PROCESSANDO"
                        val history = dao.recent().reversed().dropLast(1).takeLast(4).map { Turn(it.role, it.content) }
                        runCatching { localAi.reply(modelFile.absolutePath, history, text) }
                            .onSuccess { respond(it) }
                            .onFailure { respond("Não consegui iniciar ou executar o modelo local. Confira se o arquivo é compatível. Detalhe: ${it.message ?: "erro desconhecido"}") }
                    }
                }
            }
        }
    }

    private fun clearConversationHistory() {
        lifecycleScope.launch {
            dao.clear()
            lastReply = ""
            voiceState = "Histórico apagado"
        }
    }

    private suspend fun respond(text: String) {
        lastReply = text
        dao.insert(ChatMessage(role = "assistant", content = text))
        voiceState = "Respondendo"
        speech.speak(text)
        voiceState = "Pronto"
    }

    override fun onDestroy() {
        speech.close()
        localAi.close()
        super.onDestroy()
    }
}

@Composable
private fun HeliosScreen(
    messages: List<ChatMessage>,
    state: String,
    modelStatus: String,
    downloadProgress: Float,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    onStop: () -> Unit,
    onRepeat: () -> Unit,
    onPickModel: () -> Unit,
    onDownloadModel: () -> Unit,
    onClearHistory: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.92f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "core"
    )
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFC928), background = Color(0xFF090806), surface = Color(0xFF17130A))) {
        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("IA local do HÉLIOS") },
                text = {
                    Column {
                        Text("O modelo é armazenado no celular e a conversa é processada localmente. O arquivo não vem dentro do APK.", fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(modelStatus, color = Color(0xFFFFD34E), fontSize = 12.sp)
                        if (modelStatus.startsWith("Baixando") || modelStatus.startsWith("Preparando")) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = downloadProgress, modifier = Modifier.fillMaxWidth(), color = Color(0xFFFFC928))
                            Text("${(downloadProgress * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFFC7B98F))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Você pode baixar o Qwen3 0.6B (aprox. 347 MB) diretamente aqui ou importar outro arquivo .litertlm. O modelo fica salvo no armazenamento privado do aplicativo.", fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("O primeiro download exige internet e espaço livre. Depois, as respostas do modelo são geradas localmente.", fontSize = 11.sp, color = Color(0xFFC7B98F))
                        Spacer(Modifier.height(12.dp))
                        Text("Privacidade: a internet é usada somente quando você solicita o download do modelo. Depois da instalação, a IA responde localmente. O reconhecimento de voz offline depende do suporte do Android no aparelho.", fontSize = 12.sp, color = Color(0xFFC7B98F))
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showSettings = false; onClearHistory() }) {
                            Text("APAGAR HISTÓRICO DA CONVERSA", color = Color(0xFFFF9C9C))
                        }
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(onClick = onDownloadModel) { Text("BAIXAR QWEN3") }
                        TextButton(onClick = { showSettings = false; onPickModel() }) { Text("IMPORTAR") }
                    }
                },
                dismissButton = { TextButton(onClick = { showSettings = false }) { Text("FECHAR") } }
            )
        }
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF171106), Color(0xFF080704)))).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("HÉLIOS", color = Color(0xFFFFD34E), fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text("ASSISTENTE PESSOAL", color = Color(0xFFA88B3C), fontSize = 9.sp, letterSpacing = 2.sp)
                }
                TextButton(onClick = { showSettings = true }) { Text("CONFIGURAÇÕES", color = Color(0xFFEBC03A), fontSize = 10.sp) }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(154.dp).scale(pulse).border(1.dp, Color(0x66FFC928), CircleShape).padding(10.dp).border(1.dp, Color(0xAAFFC928), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(104.dp).background(Brush.radialGradient(listOf(Color(0xFF8A6500), Color(0xFF071A2A), Color.Transparent)), CircleShape).border(2.dp, Color(0xFF4ADFFF), CircleShape), contentAlignment = Alignment.Center) {
                        Text("H", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Light)
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.uppercase(), color = Color(0xFFFFCE32), fontSize = 11.sp, letterSpacing = 3.sp)
                    Text(if (modelStatus.contains("instalado", ignoreCase = true)) "IA LOCAL DISPONÍVEL" else "AGUARDANDO MODELO LOCAL", color = Color(0xFF9D7D28), fontSize = 8.sp)
                }
            }
            Text("CONVERSA", color = Color(0xFFDDB43B), fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages, key = { it.id }) { msg ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (msg.role == "user") Color(0xFF2C220A) else Color(0xFF1A1509)).padding(12.dp)) {
                        Text(if (msg.role == "user") "VOCÊ" else "HÉLIOS", color = if (msg.role == "user") Color(0xFFEAC24D) else Color(0xFFFFD34E), fontSize = 9.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(msg.content, color = Color(0xFFF5EAD0), fontSize = 14.sp)
                    }
                }
                if (messages.isEmpty()) item { Text("Olá. Sou Hélios. Importe um modelo local para começar a conversar com IA offline.", color = Color(0xFFC7B98F), fontSize = 14.sp) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Fale comigo...") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFFFFC928), unfocusedBorderColor = Color(0xFF6D571B)))
                IconButton(onClick = { if (input.isNotBlank()) { onSend(input); input = "" } }) { Icon(Icons.Default.Send, "Enviar", tint = Color(0xFFFFCE32)) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onMic) { Icon(Icons.Default.Mic, "Ouvir") }
                OutlinedButton(onClick = onStop) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(4.dp)); Text("PARAR") }
                TextButton(onClick = onRepeat) { Text("REPETIR RESPOSTA") }
            }
            Text("O microfone só é acionado quando você toca no botão.", color = Color(0xFF8F7839), fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 5.dp))
        }
    }
}
