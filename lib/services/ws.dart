import 'dart:async';
import 'dart:convert';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'package:web_socket_channel/status.dart' as ws_status;
import '../config.dart';
import 'token_store.dart';

// Коды, при которых реконнект бессмысленен — см. AGENTS.md
const noReconnectCodes = {4001, 4003, 4004, 4009, 4029};

class WsClient {
  final String path; // например 'chat/<uuid>/' или 'notifications/'
  final void Function(Map<String, dynamic> event) onEvent;
  final void Function(int? code)? onClose;
  final void Function()? onReconnect;

  WebSocketChannel? _channel;
  StreamSubscription? _sub;
  Timer? _retryTimer;
  bool _closedByUser = false;
  bool _hadConnection = false;

  WsClient({required this.path, required this.onEvent, this.onClose, this.onReconnect});

  Future<void> connect() async {
    _closedByUser = false;
    final token = await TokenStore.access;
    if (token == null) {
      onClose?.call(4001);
      return;
    }
    final uri = Uri.parse('${AppConfig.wsBase}/$path?token=$token');
    try {
      _channel = WebSocketChannel.connect(uri);
      await _channel!.ready;
      _hadConnection = true;
      if (_hadConnection && _retryTimer != null) {
        // после реконнекта — попросить перечитать состояние
        onReconnect?.call();
      }
      _sub = _channel!.stream.listen(
        (data) {
          try {
            final decoded = jsonDecode(data as String) as Map<String, dynamic>;
            onEvent(decoded);
          } catch (_) {}
        },
        onDone: () => _handleClose(_channel?.closeCode),
        onError: (_) => _handleClose(_channel?.closeCode),
      );
    } catch (_) {
      _scheduleReconnect();
    }
  }

  void _handleClose(int? code) {
    _sub?.cancel();
    _sub = null;
    _channel = null;
    onClose?.call(code);
    if (_closedByUser) return;
    if (code != null && noReconnectCodes.contains(code)) return;
    _scheduleReconnect();
  }

  void _scheduleReconnect() {
    _retryTimer?.cancel();
    _retryTimer = Timer(const Duration(seconds: 2), connect);
  }

  void send(Map<String, dynamic> payload) {
    if (_channel != null) {
      _channel!.sink.add(jsonEncode(payload));
    } else {
      throw StateError('Socket not connected');
    }
  }

  bool get isConnected => _channel != null;

  void close() {
    _closedByUser = true;
    _retryTimer?.cancel();
    _sub?.cancel();
    _channel?.sink.close(ws_status.normalClosure);
    _channel = null;
  }
}
