package com.helios.assistant

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.helios.assistant.data.Turn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class LocalAiEngine {
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var loadedPath: String? = null

    suspend fun reply(modelPath: String, history: List<Turn>, prompt: String): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val activeEngine = if (engine != null && loadedPath == modelPath) {
                    engine!!
                } else {
                    engine?.close()
                    Engine(
                        EngineConfig(
                            modelPath = modelPath,
                            backend = Backend.GPU(),
                            cacheDir = File(modelPath).parentFile?.resolve("litert-cache")?.apply { mkdirs() }?.absolutePath
                        )
                    ).also {
                        it.initialize()
                        engine = it
                        loadedPath = modelPath
                    }
                }

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
                            "Responda naturalmente, de forma direta e curta. " +
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
