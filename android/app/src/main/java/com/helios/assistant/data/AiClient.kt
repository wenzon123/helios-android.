package com.helios.assistant.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class Turn(val role: String, val content: String)
data class ChatRequest(val messages: List<Turn>, val personality: String = "Você é HÉLIOS, assistente em português brasileiro. Seja natural, objetivo e transparente.")
data class ChatResponse(val reply: String, val provider: String? = null)
interface HeliosApi { @POST("/v1/chat") suspend fun chat(@Body request: ChatRequest): ChatResponse }
class AiClient(baseUrl: String) {
    private val api = Retrofit.Builder().baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/").addConverterFactory(GsonConverterFactory.create()).build().create(HeliosApi::class.java)
    suspend fun reply(messages: List<Turn>): ChatResponse = api.chat(ChatRequest(messages))
}