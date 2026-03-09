package com.mrshudson.android.data.remote

import com.mrshudson.android.data.remote.dto.ConversationDto
import com.mrshudson.android.data.remote.dto.CreateConversationRequest
import com.mrshudson.android.data.remote.dto.CreateConversationResponse
import com.mrshudson.android.data.remote.dto.MessageDto
import com.mrshudson.android.data.remote.dto.SendMessageRequest
import com.mrshudson.android.data.remote.dto.SendMessageResponse
import com.mrshudson.android.data.remote.dto.TtsRequest
import com.mrshudson.android.data.remote.dto.TtsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 聊天相关 API 接口
 * 定义消息发送、历史查询、会话管理等接口
 */
interface ChatApi {

    /**
     * 发送消息
     * 向 AI 发送消息并获取回复
     *
     * @param request 发送消息请求，包含消息内容和会话ID
     * @return AI 回复的消息
     */
    @POST("chat/send")
    suspend fun sendMessage(
        @Body request: SendMessageRequest
    ): Response<ResultDto<SendMessageResponse>>

    /**
     * 获取会话历史消息
     * 获取指定会话的消息历史
     *
     * @param conversationId 会话ID
     * @param limit 限制返回消息数量，默认50
     * @return 消息列表
     */
    @GET("chat/history")
    suspend fun getHistory(
        @Query("conversation_id") conversationId: String,
        @Query("limit") limit: Int = 50
    ): Response<ResultDto<List<MessageDto>>>

    /**
     * 获取会话列表
     * 获取当前用户的所有会话
     *
     * @return 会话列表
     */
    @GET("chat/conversations")
    suspend fun getConversations(): Response<ResultDto<List<ConversationDto>>>

    /**
     * 创建新会话
     * 创建一个新的聊天会话
     *
     * @param request 创建会话请求
     * @return 新创建的会话信息
     */
    @POST("chat/conversations")
    suspend fun createConversation(
        @Body request: CreateConversationRequest
    ): Response<ResultDto<CreateConversationResponse>>

    /**
     * 删除会话
     * 删除指定的会话
     *
     * @param conversationId 要删除的会话ID
     * @return 删除结果
     */
    @DELETE("chat/conversations/{id}")
    suspend fun deleteConversation(
        @Path("id") conversationId: String
    ): Response<ResultDto<Unit>>

    /**
     * 语音合成（TTS）
     * 将文本转换为语音
     *
     * @param request TTS请求，包含要转换的文本和参数
     * @return 生成的音频URL
     */
    @POST("chat/tts")
    suspend fun textToSpeech(
        @Body request: TtsRequest
    ): Response<ResultDto<TtsResponse>>
}
