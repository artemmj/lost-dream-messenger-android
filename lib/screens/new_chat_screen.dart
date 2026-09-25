import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/user.dart';
import '../services/api.dart';
import '../state/chat_state.dart';

class NewChatScreen extends StatefulWidget {
  const NewChatScreen({super.key});
  @override
  State<NewChatScreen> createState() => _NewChatScreenState();
}

class _NewChatScreenState extends State<NewChatScreen> {
  final _search = TextEditingController();
  final _groupName = TextEditingController();
  List<User> _results = [];
  final Set<User> _selected = {};
  bool _isGroup = false;

  @override
  void dispose() {
    _search.dispose(); _groupName.dispose();
    super.dispose();
  }

  Future<void> _doSearch(String q) async {
    if (q.trim().isEmpty) {
      setState(() => _results = []);
      return;
    }
    try {
      final r = await Api().searchUsers(q.trim());
      setState(() => _results = r);
    } catch (_) {}
  }

  Future<void> _createPrivate(User u) async {
    try {
      final detail = await Api().createPrivateChat(u.id);
      final chat = context.read<ChatState>();
      await chat.loadChats();
      await chat.selectChat(detail.id);
      if (mounted) Navigator.pop(context);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.toString())),
        );
      }
    }
  }

  Future<void> _createGroup() async {
    if (_groupName.text.trim().isEmpty || _selected.isEmpty) return;
    try {
      final detail = await Api().createGroupChat(
        name: _groupName.text.trim(),
        memberIds: _selected.map((u) => u.id).toList(),
      );
      final chat = context.read<ChatState>();
      await chat.loadChats();
      await chat.selectChat(detail.id);
      if (mounted) Navigator.pop(context);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.toString())),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_isGroup ? 'Новый групповой чат' : 'Новый личный чат'),
        actions: [
          TextButton(
            onPressed: () => setState(() => _isGroup = !_isGroup),
            child: Text(_isGroup ? 'Личный' : 'Групповой'),
          ),
        ],
      ),
      body: Column(
        children: [
          if (_isGroup)
            Padding(
              padding: const EdgeInsets.all(12),
              child: TextField(
                controller: _groupName,
                decoration: const InputDecoration(labelText: 'Название группы'),
              ),
            ),
          Padding(
            padding: const EdgeInsets.all(12),
            child: TextField(
              controller: _search,
              decoration: const InputDecoration(
                labelText: 'Поиск по телефону или имени',
                prefixIcon: Icon(Icons.search),
              ),
              onChanged: (v) => Future.delayed(
                const Duration(milliseconds: 300),
                () => _doSearch(v),
              ),
            ),
          ),
          if (_isGroup && _selected.isNotEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Wrap(
                spacing: 6,
                children: _selected.map((u) => Chip(
                  label: Text(u.displayName),
                  onDeleted: () => setState(() => _selected.remove(u)),
                )).toList(),
              ),
            ),
          Expanded(
            child: ListView.builder(
              itemCount: _results.length,
              itemBuilder: (_, i) {
                final u = _results[i];
                return ListTile(
                  title: Text(u.displayName),
                  subtitle: Text(u.phone),
                  trailing: _isGroup
                      ? Checkbox(
                          value: _selected.contains(u),
                          onChanged: (_) => setState(() {
                            if (_selected.contains(u)) {
                              _selected.remove(u);
                            } else {
                              _selected.add(u);
                            }
                          }),
                        )
                      : null,
                  onTap: _isGroup
                      ? () => setState(() {
                            if (_selected.contains(u)) {
                              _selected.remove(u);
                            } else {
                              _selected.add(u);
                            }
                          })
                      : () => _createPrivate(u),
                );
              },
            ),
          ),
          if (_isGroup)
            Padding(
              padding: const EdgeInsets.all(12),
              child: SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: _createGroup,
                  child: const Text('Создать группу'),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
