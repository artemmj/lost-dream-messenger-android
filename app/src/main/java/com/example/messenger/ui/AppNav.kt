package com.example.messenger.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.messenger.data.SessionState
import com.example.messenger.data.dto.Me
import com.example.messenger.ui.chat.ChatScreen
import com.example.messenger.ui.chatlist.ChatListScreen
import com.example.messenger.ui.login.LoginScreen
import com.example.messenger.ui.newchat.NewChatScreen
import com.example.messenger.ui.profile.ProfileScreen
import androidx.hilt.navigation.compose.hiltViewModel

object Route {
    const val CHATS = "chats"
    const val NEW_CHAT = "new-chat"
    const val PROFILE = "profile"
    const val CHAT_ARG = "chatId"
    const val CHAT = "chat/{$CHAT_ARG}"

    fun chat(id: String) = "chat/$id"
}

@Composable
fun AppNav(vm: RootViewModel = hiltViewModel()) {
    when (val state = vm.state.collectAsStateWithLifecycle().value) {
        // Показывать нечего, но и на вход ещё рановато: поток токена только что стартовал
        SessionState.Unknown -> Unit

        SessionState.LoggedOut -> LoginScreen()

        is SessionState.LoggedIn -> MessengerGraph(me = state.me, onLogout = vm::logout)
    }
}

@Composable
private fun MessengerGraph(me: Me, onLogout: () -> Unit) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Route.CHATS) {
        composable(Route.CHATS) {
            ChatListScreen(
                onOpenChat = { id -> nav.navigate(Route.chat(id)) },
                onNewChat = { nav.navigate(Route.NEW_CHAT) },
                onProfile = { nav.navigate(Route.PROFILE) },
                onLogout = onLogout,
            )
        }

        composable(
            route = Route.CHAT,
            arguments = listOf(navArgument(Route.CHAT_ARG) { type = NavType.StringType }),
        ) { entry ->
            ChatScreen(
                chatId = entry.arguments?.getString(Route.CHAT_ARG).orEmpty(),
                me = me,
                onBack = { nav.popBackStack() },
            )
        }

        composable(Route.NEW_CHAT) {
            NewChatScreen(
                // Экран создания чата назад возвращаться не должен: снимаем его до перехода
                onOpenChat = { id ->
                    nav.popBackStack(Route.CHATS, false)
                    nav.navigate(Route.chat(id))
                },
                onBack = { nav.popBackStack() },
            )
        }

        composable(Route.PROFILE) {
            ProfileScreen(onBack = { nav.popBackStack() })
        }
    }
}
