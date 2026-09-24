package com.example.messenger.data.dto

import kotlinx.serialization.Serializable

const val CHAT_TYPE_PRIVATE = "PRIVATE"
const val CHAT_TYPE_GROUP = "GROUP"

@Serializable
data class TokenPair(val access: String, val refresh: String)

@Serializable
data class RefreshRequest(val refresh: String)

@Serializable
data class RefreshResponse(val access: String)

@Serializable
data class LoginRequest(val phone: String, val password: String)

@Serializable
data class RegisterRequest(
    val phone: String,
    val password: String,
    val password_confirm: String,
    val username: String? = null,
    val email: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
)

/** POST /auth/register/ возвращает токены сразу, профиль — только через /users/me/ */
@Serializable
data class RegisterResponse(
    val access: String,
    val refresh: String,
)

@Serializable
data class Me(
    val id: String,
    val phone: String,
    val email: String = "",
    val first_name: String = "",
    val last_name: String = "",
    val last_seen: String? = null,
)

@Serializable
data class User(
    val id: String,
    val phone: String,
    val email: String = "",
    val first_name: String = "",
    val last_name: String = "",
) {
    val displayName: String
        get() = listOf(first_name, last_name).joinToString(" ").trim().ifBlank { phone }
}

@Serializable
data class ChatMember(
    val id: String,
    val phone: String,
    val email: String = "",
    val first_name: String = "",
    val last_name: String = "",
    val is_admin: Boolean = false,
) {
    val displayName: String
        get() = listOf(first_name, last_name).joinToString(" ").trim().ifBlank { phone }
}

@Serializable
data class Message(
    val id: String,
    val chat: String,
    val sender: User,
    val text: String,
    val created_at: String,
    val is_read: Boolean = false,
)

@Serializable
data class ChatList(
    val id: String,
    val type: String,
    val name: String? = null,
    val created_at: String,
    val last_message: Message? = null,
    val interlocutor: User? = null,
    val unread_count: Int = 0,
) {
    /** Личный чат называется по имени собеседника, групповой — по своему названию */
    val title: String
        get() = if (type == CHAT_TYPE_GROUP) {
            name?.takeIf { it.isNotBlank() } ?: "Группа"
        } else {
            interlocutor?.displayName ?: "Личный чат"
        }
}

@Serializable
data class ChatDetail(
    val id: String,
    val type: String,
    val name: String? = null,
    val members: List<ChatMember> = emptyList(),
    val my_is_admin: Boolean = false,
    val created_at: String,
)

@Serializable
data class Page<T>(
    val count: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T>,
)

@Serializable
data class SendMessageBody(val text: String)

@Serializable
data class PrivateChatBody(val interlocutor_id: String)

@Serializable
data class CreateChatBody(
    val type: String,
    val name: String? = null,
    val member_ids: List<String>? = null,
)

@Serializable
data class RenameChatBody(val name: String)

@Serializable
data class MemberBody(val user_id: String)

@Serializable
data class ProfileUpdate(
    val phone: String? = null,
    val email: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
)
