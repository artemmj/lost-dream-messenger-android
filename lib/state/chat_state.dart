import 'dart:async';
import 'package:flutter/foundation.dart';
import '../models/chat.dart';
import '../models/message.dart';
import '../services/api.dart';
import '../services/ws.dart';

const int messagesPageSize = 50;

class ChatState extends ChangeNotifier {
  final Api _api = Api();

  List<ChatListItem> chats = [];
  ChatDetail? currentChatDetail;
  List<Message> messages = [];
  String? selectedChatId;

  final Set<String> onlineUsers = {};
  int messagesPage = 1;
  bool hasMoreMessages = true;
  bool isLoadingHistory = false;

  WsClient? _chatSocket;
  String? _wsStatus; // 'connected' | 'connecting' | 'disconnected'

  String? get wsStatus => _wsStatus;

  ChatListItem? get selectedChat {
    if (selectedChatId == null) return null;
    try {
      return chats.firstWhere((c) => c.id == selectedChatId);
    } catch (_) {
      return null;
    }
  }

  Future<void> loadChats() async {
    try {
      chats = await _api.listChats();
      notifyListeners();
    } catch (_) {}
  }

  Future<void> selectChat(String chatId) async {
    selectedChatId = chatId;
    messages = [];
    messagesPage = 1;
    hasMoreMessages = true;
    onlineUsers.clear();
    currentChatDetail = null;
    notifyListeners();

    await Future.wait([
      _loadFirstPage(chatId),
      loadChatDetails(chatId),
      markRead(chatId),
    ]);

    _openChatSocket(chatId);
  }

  Future<void> _loadFirstPage(String chatId) async {
    try {
      final page = await _api.messages(chatId, page: 1);
      // Бэкенд отдаёт последние 50 в обратном порядке — переворачиваем
      messages = page.reversed.toList();
      hasMoreMessages = page.length == messagesPageSize;
      notifyListeners();
    } catch (_) {}
  }

  Future<void> loadChatDetails(String chatId) async {
    try {
      currentChatDetail = await _api.chatDetail(chatId);
      notifyListeners();
    } catch (_) {}
  }

  Future<void> loadOlderMessages() async {
    if (!hasMoreMessages || isLoadingHistory || selectedChatId == null) return;
    isLoadingHistory = true;
    notifyListeners();
    try {
      final next = messagesPage + 1;
      final older = await _api.messages(selectedChatId!, page: next);
      if (older.isEmpty) {
        hasMoreMessages = false;
      } else {
        messages = [...older.reversed, ...messages];
        messagesPage = next;
        hasMoreMessages = older.length == messagesPageSize;
      }
    } catch (_) {}
    isLoadingHistory = false;
    notifyListeners();
  }

  Future<void> markRead(String chatId) async {
    try {
      await _api.markRead(chatId);
      final idx = chats.indexWhere((c) => c.id == chatId);
      if (idx != -1) {
        chats[idx] = chats[idx].copyWith(unreadCount: 0);
        notifyListeners();
      }
    } catch (_) {}
  }

  Future<Message?> sendMessage(String text) async {
    if (selectedChatId == null) return null;
    // Пытаемся через WS
    if (_chatSocket != null && _chatSocket!.isConnected) {
      try {
        _chatSocket!.send({'text': text});
        return null; // придёт через WS-эхо
      } catch (_) {}
    }
    // Fallback на REST
    try {
      final msg = await _api.sendMessage(selectedChatId!, text);
      addMessage(msg);
      return msg;
    } on ApiException {
      rethrow;
    }
  }

  Future<void> deleteChat(String chatId) async {
    await _api.deleteChat(chatId);
    removeChat(chatId);
  }

  Future<void> renameChat(String chatId, String name) async {
    final detail = await _api.renameChat(chatId, name);
    applyRename(chatId, detail.name ?? name);
  }

  void applyRename(String chatId, String name) {
    final idx = chats.indexWhere((c) => c.id == chatId);
    if (idx == -1) { loadChats(); return; }
    chats[idx] = chats[idx].copyWith(name: name);
    if (currentChatDetail?.id == chatId) {
      currentChatDetail = ChatDetail(
        id: currentChatDetail!.id, type: currentChatDetail!.type,
        name: name, members: currentChatDetail!.members,
        myIsAdmin: currentChatDetail!.myIsAdmin,
      );
    }
    notifyListeners();
  }

  void removeChat(String chatId) {
    chats.removeWhere((c) => c.id == chatId);
    if (selectedChatId == chatId) closeChat();
    notifyListeners();
  }

  void addMessage(Message msg) {
    if (msg.chat != selectedChatId) return;
    if (messages.any((m) => m.id == msg.id)) return;
    messages.add(msg);
    final idx = chats.indexWhere((c) => c.id == msg.chat);
    if (idx != -1) {
      chats[idx] = chats[idx].copyWith(lastMessage: msg);
    }
    notifyListeners();
  }

  void closeChat() {
    selectedChatId = null;
    messages = [];
    currentChatDetail = null;
    onlineUsers.clear();
    _closeChatSocket();
    notifyListeners();
  }

  // ---------- WebSocket ----------

  void _openChatSocket(String chatId) {
    _closeChatSocket();
    _wsStatus = 'connecting';
    notifyListeners();
    final socket = WsClient(
      path: 'chat/$chatId/',
      onEvent: _handleChatEvent,
      onClose: _handleChatClose,
      onReconnect: _handleChatReconnect,
    );
    _chatSocket = socket;
    socket.connect();
  }

  void _closeChatSocket() {
    _chatSocket?.close();
    _chatSocket = null;
    _wsStatus = 'disconnected';
  }

  void _handleChatEvent(Map<String, dynamic> event) {
    final type = event['type'] as String?;
    switch (type) {
      case null:
      case '':
        // новое сообщение — приходит без type
        if (event['id'] != null) {
          final msg = Message.fromJson(event);
          addMessage(msg);
        }
        break;
      case 'user_status':
        final uid = event['user_id'] as String;
        if (event['status'] == 'online') {
          onlineUsers.add(uid);
        } else {
          onlineUsers.remove(uid);
        }
        notifyListeners();
        break;
      case 'initial_presence':
        final ids = (event['user_ids'] as List).cast<String>();
        onlineUsers.addAll(ids);
        notifyListeners();
        break;
      case 'messages_read':
        // обновляем is_read у своих сообщений
        final readerId = event['reader_id'];
        messages = messages.map((m) => m.copyWith(isRead: true)).toList();
        notifyListeners();
        break;
    }
  }

  void _handleChatClose(int? code) {
    _wsStatus = 'disconnected';
    if (code == 4003 || code == 4004) {
      if (selectedChatId != null) removeChat(selectedChatId!);
    }
    notifyListeners();
  }

  void _handleChatReconnect() async {
    _wsStatus = 'connected';
    notifyListeners();
    // перечитываем историю и список чатов, как в AGENTS.md
    if (selectedChatId != null) {
      await _loadFirstPage(selectedChatId!);
    }
    await loadChats();
  }

  void setConnected() {
    _wsStatus = 'connected';
    notifyListeners();
  }

  // ---------- Уведомления (личный канал) ----------

  WsClient? _notificationsSocket;

  void openNotificationsSocket() {
    _notificationsSocket?.close();
    final socket = WsClient(
      path: 'notifications/',
      onEvent: _handleNotification,
    );
    _notificationsSocket = socket;
    socket.connect();
  }

  void closeNotificationsSocket() {
    _notificationsSocket?.close();
    _notificationsSocket = null;
  }

  void _handleNotification(Map<String, dynamic> event) {
    final type = event['type'];
    switch (type) {
      case 'new_message':
        final chatId = event['chat'];
        final unread = event['unread_count'] as int? ?? 0;
        final idx = chats.indexWhere((c) => c.id == chatId);
        if (idx == -1) {
          loadChats();
        } else {
          final msg = event['message'] != null
              ? Message.fromJson(event['message']) : null;
          chats[idx] = chats[idx].copyWith(
            unreadCount: unread, lastMessage: msg,
          );
          // если чат открыт и совпадает — сразу подтверждаем прочтение
          if (chatId == selectedChatId && unread > 0) {
            markRead(chatId);
          }
          notifyListeners();
        }
        break;
      case 'chat_read':
        final chatId = event['chat'];
        final idx = chats.indexWhere((c) => c.id == chatId);
        if (idx != -1) {
          chats[idx] = chats[idx].copyWith(unreadCount: 0);
          notifyListeners();
        }
        break;
      case 'chat_deleted':
        removeChat(event['chat']);
        break;
      case 'chat_renamed':
        applyRename(event['chat'], event['name']);
        break;
      case 'member_removed':
        removeChat(event['chat']);
        break;
    }
  }

  @override
  void dispose() {
    _closeChatSocket();
    closeNotificationsSocket();
    super.dispose();
  }
}
