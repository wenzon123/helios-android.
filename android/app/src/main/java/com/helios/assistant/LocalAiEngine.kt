package com.helios.assistant

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.helios.assistant.data.Turn
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalAiEngine {
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var loadedPath: String? = null
    private var activeBackend: String = "GPU"

    private fun loadEngineLocked(modelPath: String): Engine {
        if (engine != null && loadedPath == modelPath) return engine!!

        engine?.close()
        engine = null
        loadedPath = null

        val cachePath = File(modelPath).parentFile
            ?.resolve("litert-cache")
            ?.apply { mkdirs() }
            ?.absolutePath

        // Tenta GPU primeiro. Se o aparelho/driver não suportar OpenCL,
        // fecha a tentativa e inicializa em CPU para não deixar a IA inutilizável.
        val gpuAttempt = runCatching {
            Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    cacheDir = cachePath
                )
            ).also { it.initialize() }
        }

        val ready = gpuAttempt.getOrElse {
            Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = cachePath
                )
            ).also { it.initialize() }.also { activeBackend = "CPU" }
        }

        if (activeBackend != "CPU") activeBackend = "GPU"
        engine = ready
        loadedPath = modelPath
        return ready
    }

    suspend fun prepare(modelPath: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadEngineLocked(modelPath)
            activeBackend
        }
    }

    suspend fun reply(modelPath: String, history: List<Turn>, prompt: String): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val activeEngine = loadEngineLocked(modelPath)
                val initialMessages = history.mapNotNull { turn ->
                    when (turn.role) {
                        "user" -> Message.user(turn.content)
                        "assistant", "model" -> Message.model(turn.content)
                        else -> null
                    }
                }
                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(
                        "Você é HÉLIOS, assistente pessoal em português brasileiro. " +
                            "Responda naturalmente, de forma direta e útil, sem enrolação. " +
                            "Não invente fatos; admita quando não souber."
                    ),
                    initialMessages = initialMessages
                )
                activeEngine.createConversation(conversationConfig).use { conversation ->
                    conversation.sendMessage(prompt).toString().trim()
                }
            }
        }

    fun close() {
        engine?.close()
        engine = null
        loadedPath = null
    }
}
