import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/chat.dart';
import '../state/auth_state.dart';
import '../state/chat_state.dart';
import 'chat_screen.dart';
import 'new_chat_screen.dart';
import 'profile_screen.dart';

class ChatsScreen extends StatefulWidget {
  const ChatsScreen({super.key});
  @override
  State<ChatsScreen> createState() => _ChatsScreenState();
}

class _ChatsScreenState extends State<ChatsScreen> {
  @override
  void initState() {
    super.initState();
    final chat = context.read<ChatState>();
    chat.loadChats();
    chat.openNotificationsSocket();
  }

  @override
  Widget build(BuildContext context) {
    final chat = context.watch<ChatState>();
    final auth = context.watch<AuthState>();
    return Scaffold(
      appBar: AppBar(
        title: Text(auth.me?.firstName ?? auth.me?.phone ?? 'Чаты'),
        actions: [
          IconButton(
            icon: const Icon(Icons.person),
            onPressed: () => Navigator.push(context,
                MaterialPageRoute(builder: (_) => const ProfileScreen())),
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () async {
              chat.closeNotificationsSocket();
              chat.closeChat();
              await auth.logout();
              if (context.mounted) {
                Navigator.of(context).pushAndRemoveUntil(
                  MaterialPageRoute(builder: (_) => const _RedirectToLogin()),
                  (_) => false,
                );
              }
            },
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: chat.loadChats,
        child: chat.chats.isEmpty
            ? ListView(children: const [
                SizedBox(height: 200),
                Center(child: Text('Чатов пока нет')),
              ])
            : ListView.builder(
                itemCount: chat.chats.length,
                itemBuilder: (_, i) {
                  final c = chat.chats[i];
                  return _ChatTile(chat: c);
                },
              ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => Navigator.push(context,
            MaterialPageRoute(builder: (_) => const NewChatScreen())),
        child: const Icon(Icons.add),
      ),
    );
  }
}

class _ChatTile extends StatelessWidget {
  final ChatListItem chat;
  const _ChatTile({required this.chat});

  @override
  Widget build(BuildContext context) {
    final preview = chat.lastMessage?.text ?? 'Нет сообщений';
    return ListTile(
      title: Text(chat.displayName,
          maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text(preview, maxLines: 1, overflow: TextOverflow.ellipsis),
      trailing: chat.unreadCount > 0
          ? CircleAvatar(
              radius: 12,
              child: Text(chat.unreadCount > 99 ? '99+' : '${chat.unreadCount}',
                  style: const TextStyle(fontSize: 10)),
            )
          : null,
      onTap: () {
        context.read<ChatState>().selectChat(chat.id);
        Navigator.push(context, MaterialPageRoute(
          builder: (_) => ChatScreen(chatId: chat.id),
        ));
      },
    );
  }
}

class _RedirectToLogin extends StatelessWidget {
  const _RedirectToLogin();
  @override
  Widget build(BuildContext context) => const SizedBox.shrink();
}
