import 'user.dart';

class Message {
  final String id;
  final String chat;
  final User sender;
  final String text;
  final DateTime createdAt;
  final bool isRead;

  Message({
    required this.id, required this.chat, required this.sender,
    required this.text, required this.createdAt, required this.isRead,
  });

  factory Message.fromJson(Map<String, dynamic> j) => Message(
        id: j['id'],
        chat: j['chat'],
        sender: User.fromJson(j['sender']),
        text: j['text'],
        createdAt: DateTime.parse(j['created_at']).toLocal(),
        isRead: j['is_read'] ?? false,
      );

  Message copyWith({bool? isRead}) => Message(
        id: id, chat: chat, sender: sender, text: text,
        createdAt: createdAt, isRead: isRead ?? this.isRead,
      );
}
