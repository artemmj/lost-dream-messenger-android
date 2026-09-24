package com.example.messenger.data.api

import com.example.messenger.data.dto.ChatDetail
import com.example.messenger.data.dto.ChatList
import com.example.messenger.data.dto.CreateChatBody
import com.example.messenger.data.dto.Message
import com.example.messenger.data.dto.MemberBody
import com.example.messenger.data.dto.Page
import com.example.messenger.data.dto.PrivateChatBody
import com.example.messenger.data.dto.RenameChatBody
import com.example.messenger.data.dto.SendMessageBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApi {
    @GET("chats/")
    suspend fun chats(@Query("page") page: Int? = null): Page<ChatList>

    /** Создатель становится админом; member_ids — только для GROUP */
    @POST("chats/")
    suspend fun createChat(@Body body: CreateChatBody): ChatDetail

    /** Идемпотентно: вернёт существующий личный чат, если он уже есть */
    @POST("chats/private/")
    suspend fun privateChat(@Body body: PrivateChatBody): ChatDetail

    @GET("chats/{id}/")
    suspend fun chat(@Path("id") id: String): ChatDetail

    /**
     * История: страница 1 — последние сообщения, но внутри страницы порядок
     * по возрастанию времени, поэтому разворачивать results не нужно.
     */
    @GET("chats/{id}/messages/")
    suspend fun messages(
        @Path("id") id: String,
        @Query("page") page: Int? = null,
    ): Page<Message>

    @POST("chats/{id}/send/")
    suspend fun send(@Path("id") id: String, @Body body: SendMessageBody): Message

    /** Сдвигает курсор прочтения; ответ — {"unread_count": 0} */
    @POST("chats/{id}/read/")
    suspend fun markRead(@Path("id") id: String)

    @PATCH("chats/{id}/")
    suspend fun rename(@Path("id") id: String, @Body body: RenameChatBody): ChatDetail

    @DELETE("chats/{id}/")
    suspend fun deleteChat(@Path("id") id: String)

    @POST("chats/{id}/add-member/")
    suspend fun addMember(@Path("id") id: String, @Body body: MemberBody)

    @POST("chats/{id}/remove-member/")
    suspend fun removeMember(@Path("id") id: String, @Body body: MemberBody)
}
