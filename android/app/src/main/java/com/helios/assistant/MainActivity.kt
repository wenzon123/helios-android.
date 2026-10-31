package com.helios.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var dao: ChatDao
    private lateinit var speech: SpeechController
    private lateinit var actions: DeviceActions
    private var voiceState by mutableStateOf("Pronto")
    private var lastReply by mutableStateOf("")
    private val preferences by lazy { getSharedPreferences("helios", MODE_PRIVATE) }
    private val requestMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) speech.listen() else voiceState = "Permissão de microfone negada" }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dao = HeliosDatabase.get(this).chatDao()
        actions = DeviceActions(this)
        speech = SpeechController(this, { text -> handleUserText(text) }, { state -> voiceState = state })
        setContent {
            val messages by dao.observeAll().collectAsState(initial = emptyList())
            HeliosScreen(messages, voiceState, lastReply,
                onSend = { handleUserText(it) },
                onMic = { if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech.listen() else requestMic.launch(Manifest.permission.RECORD_AUDIO) },
                onStop = { speech.stop() }, onRepeat = { if (lastReply.isNotBlank()) speech.speak(lastReply) },
                onSettings = { }, initialBackendUrl = preferences.getString("backend_url", "") ?: "", onSaveBackend = { url -> preferences.edit().putString("backend_url", url).apply() })
        }
    }
    private fun handleUserText(raw: String) {
        val text = raw.trim(); if (text.isBlank()) return
        lifecycleScope.launch {
            dao.insert(ChatMessage(role = "user", content = text))
            val lower = text.lowercase(Locale("pt", "BR"))
            when {
                lower.contains("que horas") || lower.contains("horário") -> respond("Agora são ${SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date())}.")
                lower.contains("que dia") || lower.contains("data de hoje") -> respond("Hoje é ${SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("pt", "BR")).format(Date())}.")
                lower.contains("abra o youtube") || lower == "youtube" -> { runCatching { actions.openYouTube() }; respond("Abrindo o YouTube.") }
                lower.startsWith("pesquise ") || lower.startsWith("pesquisar ") || lower.startsWith("pesquisa ") -> { val q = text.substringAfter(' '); actions.searchWeb(q); respond("Abrindo uma pesquisa sobre $q.") }
                lower.contains("configurações") -> { actions.openSettings(); respond("Abrindo as configurações do Android.") }
                else -> {
                    voiceState = "Conectando à IA"
                    val recent = dao.recent().reversed().map { Turn(it.role, it.content) }
                    val base = preferences.getString("backend_url", "") ?: ""
                    if (base.isBlank()) respond("Minha conexão com a IA ainda não foi configurada. Abra Configurações e informe a URL HTTPS do servidor HÉLIOS. A voz já funciona pelo botão do microfone.")
                    else runCatching { AiClient(base).reply(recent) }.onSuccess { respond(it.reply) }.onFailure { respond("Não consegui conectar à inteligência artificial. Verifique a internet e o endereço do backend.") }
                }
            }
        }
    }
    private suspend fun respond(text: String) { lastReply = text; dao.insert(ChatMessage(role = "assistant", content = text)); voiceState = "Respondendo"; speech.speak(text); voiceState = "Pronto" }
    override fun onDestroy() { speech.close(); super.onDestroy() }
}

@Composable
private fun HeliosScreen(messages: List<ChatMessage>, state: String, lastReply: String, onSend: (String) -> Unit, onMic: () -> Unit, onStop: () -> Unit, onRepeat: () -> Unit, onSettings: () -> Unit, initialBackendUrl: String, onSaveBackend: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var backendUrl by remember { mutableStateOf(initialBackendUrl) }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(initialValue = 0.92f, targetValue = 1.06f, animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "core")
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF48D9FF), background = Color(0xFF050B14), surface = Color(0xFF0B1725))) {
        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("Conexão com a IA") },
                text = {
                    Column {
                        Text("Cole aqui a URL HTTPS do servidor HÉLIOS publicado. A chave da IA fica protegida no servidor, nunca no APK.", fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = backendUrl, onValueChange = { backendUrl = it }, label = { Text("URL do backend") }, placeholder = { Text("https://helios-seuapp.onrender.com") }, singleLine = true)
                    }
                },
                confirmButton = { TextButton(onClick = { onSaveBackend(backendUrl.trim().trimEnd('/')); showSettings = false }) { Text("SALVAR") } },
                dismissButton = { TextButton(onClick = { showSettings = false }) { Text("CANCELAR") } }
            )
        }
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF071827), Color(0xFF03070D)))).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("HÉLIOS", color = Color(0xFF8DEBFF), fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp); Text("ASSISTENTE PESSOAL", color = Color(0xFF648A9D), fontSize = 9.sp, letterSpacing = 2.sp) }
                TextButton(onClick = { showSettings = true }) { Text("CONFIGURAÇÕES", color = Color(0xFF75CFE5), fontSize = 10.sp) }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(154.dp).scale(pulse).border(1.dp, Color(0x6648D9FF), CircleShape).padding(10.dp).border(1.dp, Color(0xAA48D9FF), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(104.dp).background(Brush.radialGradient(listOf(Color(0xFF1D789B), Color(0xFF071A2A), Color.Transparent)), CircleShape).border(2.dp, Color(0xFF4ADFFF), CircleShape), contentAlignment = Alignment.Center) {
                        Text("H", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Light)
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.uppercase(), color = Color(0xFF7DE7FF), fontSize = 11.sp, letterSpacing = 3.sp)
                    Text("NÚCLEO ONLINE · VOZ SOB DEMANDA", color = Color(0xFF496C7D), fontSize = 8.sp)
                }
            }
            Text("CONVERSA", color = Color(0xFF6FBBD0), fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = false, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages, key = { it.id }) { msg ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (msg.role == "user") Color(0xFF102638) else Color(0xFF0A1925)).padding(12.dp)) {
                        Text(if (msg.role == "user") "VOCÊ" else "HÉLIOS", color = if (msg.role == "user") Color(0xFF77BBD4) else Color(0xFF69E4FF), fontSize = 9.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(4.dp)); Text(msg.content, color = Color(0xFFD9EDF4), fontSize = 14.sp)
                    }
                }
                if (messages.isEmpty()) item { Text("Olá. Sou Hélios. Posso conversar, abrir aplicativos e pesquisar na internet.", color = Color(0xFF91AEBB), fontSize = 14.sp) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Fale comigo...") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF48D9FF), unfocusedBorderColor = Color(0xFF29495A)))
                IconButton(onClick = { if (input.isNotBlank()) { onSend(input); input = "" } }) { Icon(Icons.Default.Send, "Enviar", tint = Color(0xFF63DFFF)) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onMic) { Icon(Icons.Default.Mic, "Ouvir") }
                OutlinedButton(onClick = onStop) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(4.dp)); Text("PARAR") }
                TextButton(onClick = onRepeat) { Text("REPETIR RESPOSTA") }
            }
            Text("O microfone só é acionado quando você toca no botão.", color = Color(0xFF587484), fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 5.dp))
        }
    }
}