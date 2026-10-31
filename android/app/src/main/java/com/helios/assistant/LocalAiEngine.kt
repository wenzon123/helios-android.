package com.helios.assistant

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.helios.assistant.data.Turn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAiEngine {
    suspend fun reply(modelPath: String, history: List<Turn>, prompt: String): String =
        withContext(Dispatchers.IO) {
            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU()
            )
            Engine(config).use { engine ->
                engine.initialize()
                val initialMessages = history.mapNotNull { turn ->
                    when (turn.role) {
                        "user" -> Message.user(turn.content)
                        "assistant", "model" -> Message.model(turn.content)
                        else -> null
                    }
                }
                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(
                        "Você é HÉLIOS, um assistente pessoal em português brasileiro. " +
                            "Seja natural, claro, cuidadoso e objetivo. " +
                            "Não invente informações e avise quando não souber."
                    ),
                    initialMessages = initialMessages
                )
                engine.createConversation(conversationConfig).use { conversation ->
                    conversation.sendMessage(prompt).text.trim()
                }
            }
        }

    fun close() = Unit
}
