package com.example.messenger.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.messenger.data.dto.CHAT_TYPE_GROUP
import com.example.messenger.data.dto.ChatMember
import com.example.messenger.data.dto.Me
import com.example.messenger.data.dto.Message
import com.example.messenger.data.dto.User
import com.example.messenger.ui.formatTime
import kotlinx.coroutines.delay

/** Сколько элементов держим «в запасе», прежде чем догружать следующую страницу истории */
private const val LOAD_OLDER_EDGE = 4

/** Как в веб-клиенте: поиск по полю запускаем с задержкой, а не на каждый символ */
private const val SEARCH_DEBOUNCE_MS = 300L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    me: Me,
    onBack: () -> Unit,
    vm: ChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(chatId) { vm.open(chatId, me.id) }
    DisposableEffect(Unit) { onDispose { vm.close() } }
    LaunchedEffect(vm.chatGone) { if (vm.chatGone) onBack() }

    var showMembers by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val details = vm.details
    val group = details?.type == CHAT_TYPE_GROUP
    val members = details?.members.orEmpty()
    val interlocutor = members.firstOrNull { it.id != me.id }
    val admin = details?.my_is_admin == true
    val onlineInChat = members.count { it.id in vm.online }

    // Прокрутка к началу — он же «к старым» при reverseLayout: следующая страница
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > LOAD_OLDER_EDGE && last >= total - LOAD_OLDER_EDGE) vm.loadOlder()
        }
    }

    // Новый сигнал — прокручиваем, только если и так сидим у нижней кромки
    LaunchedEffect(vm.messages.size) {
        if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Column {
                        Text(
                            when {
                                group -> details?.name?.takeIf { it.isNotBlank() } ?: "Группа"
                                else -> interlocutor?.displayName ?: "Чат"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        Text(
                            when {
                                group -> "$onlineInChat в сети из ${members.size}"
                                interlocutor != null ->
                                    if (interlocutor.id in vm.online) "в сети" else "не в сети"

                                else -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (group) {
                        TextButton(onClick = { showMembers = true }) { Text("Участники") }
                        if (admin) {
                            TextButton(onClick = { showRename = true }) { Text("Имя") }
                        }
                    }
                    // GROUP — только админ, PRIVATE — любой участник
                    if (!group || admin) {
                        TextButton(onClick = {
                            if (confirmDelete) {
                                vm.deleteChat()
                            } else {
                                confirmDelete = true
                            }
                        }) {
                            Text(if (confirmDelete) "Точно?" else "Удалить")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            vm.loadError?.let { ErrorLine(it, vm::dismissErrors) }
            vm.notice?.let { ErrorLine(it, vm::dismissErrors) }

            if (vm.messages.isEmpty() && !vm.loadingHistory) {
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    Text(
                        "Сообщений пока нет",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    val shown = vm.messages.reversed()
                    itemsIndexed(shown, key = { _, m -> m.id }) { index, message ->
                        val previous = shown.getOrNull(index + 1)
                        MessageBubble(
                            message = message,
                            mine = message.sender.id == me.id,
                            // автора подписываем в группе и только у первого сообщения подряд
                            showAuthor = group && message.sender.id != me.id &&
                                previous?.sender?.id != message.sender.id,
                        )
                    }
                    if (vm.loadingHistory) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = vm.input,
                    onValueChange = { vm.input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    maxLines = 5,
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = vm::send) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                }
            }
        }
    }

    if (showMembers) {
        MembersDialog(
            members = members,
            admin = admin,
            online = vm.online,
            me = me,
            query = vm.memberQuery,
            candidates = vm.memberResults,
            onQueryChange = { vm.memberQuery = it },
            onSearch = vm::searchCandidates,
            onAdd = { userId ->
                vm.addMember(userId)
                vm.memberQuery = ""
                vm.memberResults = emptyList()
            },
            onRemove = vm::removeMember,
            onDismiss = { showMembers = false },
        )
    }

    if (showRename) {
        RenameDialog(
            initial = details?.name.orEmpty(),
            onDismiss = { showRename = false },
            onSubmit = { name, fail ->
                vm.rename(name) { error ->
                    if (error == null) showRename = false else fail(error)
                }
            },
        )
    }
}

@Composable
private fun MessageBubble(message: Message, mine: Boolean, showAuthor: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        ) {
            if (showAuthor) {
                Text(
                    message.sender.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = if (mine) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Text(
                buildString {
                    append(formatTime(message.created_at))
                    // ✓✓ — глобальный флаг прочтения, per-user отметки на бэкенде нет
                    if (mine) append(if (message.is_read) "  ✓✓" else "  ✓")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (mine) TextAlign.End else TextAlign.Start,
            )
        }
    }
}

@Composable
private fun ErrorLine(text: String, onDismiss: () -> Unit) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onErrorContainer,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun PresenceDot(online: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(
                if (online) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
            ),
    )
}

@Composable
private fun MembersDialog(
    members: List<ChatMember>,
    admin: Boolean,
    online: Set<String>,
    me: Me,
    query: String,
    candidates: List<User>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val admins = members.count { it.is_admin }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Участники (${members.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 260.dp)) {
                    members.forEach { member ->
                        val self = member.id == me.id
                        // Единственного админа выйти не могут — сервер тоже не даст
                        val onlyAdmin = member.is_admin && admins <= 1
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PresenceDot(online.contains(member.id))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                member.displayName + if (self) " (вы)" else "",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            if (member.is_admin) {
                                Text(
                                    "админ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (if (self) !onlyAdmin else admin) {
                                TextButton(onClick = { onRemove(member.id) }) {
                                    Text(if (self) "Выйти" else "Удалить")
                                }
                            }
                        }
                    }
                }

                if (admin) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        label = { Text("Добавить: телефон или имя") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LaunchedEffect(query) {
                        delay(SEARCH_DEBOUNCE_MS)
                        onSearch()
                    }
                    candidates.forEach { user ->
                        TextButton(
                            onClick = { onAdd(user.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(user.displayName, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Закрыть")
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSubmit: (String, (String) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Название группы", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    TextButton(onClick = { onSubmit(name.trim()) { error = it } }) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}
