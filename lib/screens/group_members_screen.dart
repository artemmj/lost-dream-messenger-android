import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/chat.dart';
import '../services/api.dart';
import '../state/chat_state.dart';

class GroupMembersScreen extends StatelessWidget {
  final String chatId;
  const GroupMembersScreen({required this.chatId, super.key});

  @override
  Widget build(BuildContext context) {
    final chat = context.watch<ChatState>();
    final detail = chat.currentChatDetail;

    return Scaffold(
      appBar: AppBar(title: const Text('Участники')),
      body: detail == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              children: [
                for (final m in detail.members)
                  ListTile(
                    title: Text(m.user.displayName),
                    subtitle: Text(m.user.phone),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (m.isAdmin)
                          const Chip(label: Text('админ')),
                        if (m.user.id != context.read<ChatState>().selectedChatId)
                          IconButton(
                            icon: const Icon(Icons.remove_circle_outline),
                            onPressed: detail.myIsAdmin
                                ? () => _remove(context, m.user.id)
                                : null,
                          ),
                      ],
                    ),
                  ),
              ],
            ),
    );
  }

  Future<void> _remove(BuildContext context, String userId) async {
    try {
      await Api().removeMember(chatId, userId);
      await context.read<ChatState>().loadChatDetails(chatId);
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.toString())),
        );
      }
    }
  }
}
