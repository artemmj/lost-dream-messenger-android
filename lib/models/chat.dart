import 'message.dart';
import 'user.dart';

enum ChatType { private, group }

ChatType _parseType(String t) => t == 'GROUP' ? ChatType.group : ChatType.private;

class ChatListItem {
  final String id;
  final ChatType type;
  final String? name;
  final DateTime createdAt;
  final Message? lastMessage;
  final User? interlocutor;
  final int unreadCount;

  ChatListItem({
    required this.id, required this.type, this.name,
    required this.createdAt, this.lastMessage,
    this.interlocutor, required this.unreadCount,
  });

  factory ChatListItem.fromJson(Map<String, dynamic> j) => ChatListItem(
        id: j['id'],
        type: _parseType(j['type']),
        name: j['name'],
        createdAt: DateTime.parse(j['created_at']).toLocal(),
        lastMessage: j['last_message'] != null
            ? Message.fromJson(j['last_message']) : null,
        interlocutor: j['interlocutor'] != null
            ? User.fromJson(j['interlocutor']) : null,
        unreadCount: j['unread_count'] ?? 0,
      );

  String get displayName {
    if (type == ChatType.group) return name ?? 'Групповой чат';
    return interlocutor?.displayName ?? 'Личный чат';
  }

  ChatListItem copyWith({int? unreadCount, Message? lastMessage, String? name}) =>
      ChatListItem(
        id: id, type: type,
        name: name ?? this.name,
        createdAt: createdAt,
        lastMessage: lastMessage ?? this.lastMessage,
        interlocutor: interlocutor,
        unreadCount: unreadCount ?? this.unreadCount,
      );
}

class ChatMember {
  final User user;
  final bool isAdmin;
  ChatMember({required this.user, required this.isAdmin});

  factory ChatMember.fromJson(Map<String, dynamic> j) => ChatMember(
        user: User.fromJson(j),
        isAdmin: j['is_admin'] ?? false,
      );
}

class ChatDetail {
  final String id;
  final ChatType type;
  final String? name;
  final List<ChatMember> members;
  final bool myIsAdmin;

  ChatDetail({
    required this.id, required this.type, this.name,
    required this.members, required this.myIsAdmin,
  });

  factory ChatDetail.fromJson(Map<String, dynamic> j) => ChatDetail(
        id: j['id'],
        type: _parseType(j['type']),
        name: j['name'],
        members: (j['members'] as List)
            .map((m) => ChatMember.fromJson(m)).toList(),
        myIsAdmin: j['my_is_admin'] ?? false,
      );
}
