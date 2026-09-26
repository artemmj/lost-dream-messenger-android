import 'dart:async';
import 'dart:convert';
// IOWebSocketChannel нужен для нативных платформ (Android/iOS/macOS/Linux/Windows),
// чтобы передать заголовок Origin в handshake. На web-платформе заголовки WS недоступны.
import 'package:web_socket_channel/io.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'package:web_socket_channel/status.dart' as ws_status;
import '../config.dart';
import 'token_store.dart';

// Коды закрытия, при которых повторное подключение бессмысленно:
// 4001 — невалидный JWT, 4003 — исключён из чата, 4004 — чат удалён,
// 4009 — превышен лимит одновременных соединений, 4029 — слишком частые подключения.
// См. AGENTS.md раздел 5 «Коды закрытия».
const noReconnectCodes = {4001, 4003, 4004, 4009, 4029};

class WsClient {
  /// Путь к endpoint без схемы и хоста, например 'chat/<uuid>/' или 'notifications/'
  final String path;

  /// Колбэк для каждого полученного кадра от сервера
  final void Function(Map<String, dynamic> event) onEvent;

  /// Вызывается при закрытии соединения с кодом причины
  final void Function(int? code)? onClose;

  /// Вызывается после успешного переподключения (не первого подключения!)
  final void Function()? onReconnect;

  /// Вызывается сразу после успешного handshake, когда соединение открыто.
  /// Нужен для обновления статуса в UI — см. дефект №3 в AGENTS.md.
  final void Function()? onOpen;

  WebSocketChannel? _channel;
  StreamSubscription? _sub;
  Timer? _retryTimer;
  bool _closedByUser = false;
  bool _hadConnection = false;

  WsClient({
    required this.path,
    required this.onEvent,
    this.onClose,
    this.onReconnect,
    this.onOpen,
  });

  /// Подключается к WebSocket-серверу.
  ///
  /// Для нативных платформ (Android/iOS/macOS/Linux/Windows) используется IOWebSocketChannel
  /// с заголовком Origin, который требует бэкенд (AllowedHostsOriginValidator).
  /// Без этого заголовка Django отвечает 403 до accept(), и клиент уходит в бесконечный ретрай.
  ///
  /// На web-платформе заголовки WebSocket-handshake недоступны браузером, поэтому
  /// используется обычный WebSocketChannel.connect. Для работы на web нужно настроить
  /// CORS_ALLOWED_ORIGINS и ALLOWED_HOSTS на бэкенде.
  Future<void> connect() async {
    _closedByUser = false;
    final token = await TokenStore.access;
    if (token == null) {
      // Нет токена — сразу сообщаем о закрытии с кодом «неавторизован»
      onClose?.call(4001);
      return;
    }

    // Формируем URI с токеном в query-параметре (заголовки в WS бэкенд не читает)
    final uri = Uri.parse('${AppConfig.wsBase}/$path?token=$token');

    try {
      // На нативных платформах добавляем заголовок Origin, иначе бэкенд вернёт 403.
      // AppConfig.host должен входить в ALLOWED_HOSTS Django (по умолчанию там 10.0.2.2).
      // На web этот конструктор недоступен — там заголовки игнорируются браузером.
      _channel = IOWebSocketChannel.connect(
        uri,
        headers: {'Origin': 'http://${AppConfig.host}'},
      );

      // ready завершается, когда handshake прошёл и соединение открыто
      await _channel!.ready;

      // Помечаем, что было успешное подключение (нужно для отличия первого подключения от реконнекта)
      _hadConnection = true;

      // Если это реконнект (был разрыв и сработал таймер), уведомляем подписчика,
      // чтобы он перезагрузил состояние чата и список чатов
      if (_hadConnection && _retryTimer != null) {
        onReconnect?.call();
      }

      // Подписываемся на поток сообщений от сервера
      _sub = _channel!.stream.listen(
        (data) {
          try {
            // Парсим JSON-кадр и передаём обработчику состояния
            final decoded = jsonDecode(data as String) as Map<String, dynamic>;
            onEvent(decoded);
          } catch (_) {
            // Некорректный JSON тихо игнорируется — дефект №15 в AGENTS.md
          }
        },
        // onDone вызывается при нормальном закрытии соединения
        onDone: () => _handleClose(_channel?.closeCode),
        // onError — при сетевых ошибках (разрыв, таймаут)
        onError: (_) => _handleClose(_channel?.closeCode),
      );
    } catch (_) {
      // Ошибка подключения (403, сеть недоступна, DNS) — планируем реконнект
      _scheduleReconnect();
    }
  }

  /// Обрабатывает закрытие соединения.
  ///
  /// Отменяет подписку на поток, обнуляет канал и уведомляет подписчика о коде закрытия.
  /// Если соединение закрыто пользователем (_closedByUser) или код входит в noReconnectCodes,
  /// реконнект не планируется — это окончательное закрытие.
  /// В остальных случаях (обрыв сети, таймаут сервера) через 2 секунды будет попытка переподключения.
  void _handleClose(int? code) {
    _sub?.cancel();
    _sub = null;
    _channel = null;

    // Уведомляем ChatState о закрытии — он обновит wsStatus и, возможно, удалит чат
    onClose?.call(code);

    // Если пользователь сам вызвал close() или сервер вернул «фатальный» код — не реконнектим
    if (_closedByUser) return;
    if (code != null && noReconnectCodes.contains(code)) return;

    // Во всех остальных случаях пробуем переподключиться через фиксированный интервал
    _scheduleReconnect();
  }

  /// Планирует повторное подключение через 2 секунды.
  ///
  /// Фиксированная задержка выбрана для простоты; в продакшене нужен экспоненциальный backoff.
  /// Таймер отменяется при явном close() или новом подключении.
  void _scheduleReconnect() {
    _retryTimer?.cancel();
    _retryTimer = Timer(const Duration(seconds: 2), connect);
  }

  /// Отправляет JSON-кадр в WebSocket.
  ///
  /// Клиент отправляет только {"text": "..."} — бэкенд сам добавит id, chat, sender, created_at, is_read
  /// и вернёт полное сообщение обратно через broadcast-группу чата (эхо).
  /// Если канал ещё не открыт (_channel == null), бросает StateError.
  void send(Map<String, dynamic> payload) {
    if (_channel != null) {
      _channel!.sink.add(jsonEncode(payload));
    } else {
      throw StateError('Socket not connected');
    }
  }

  /// Возвращает true, если WebSocket-канал создан (но не обязательно открыт).
  ///
  /// Для точной проверки использования смотрите wsStatus в ChatState.
  bool get isConnected => _channel != null;

  /// Закрывает соединение нормально (код 1000) и отменяет все таймеры реконнекта.
  ///
  /// После этого WsClient можно выбросить — повторное использование невозможно.
  void close() {
    _closedByUser = true;
    _retryTimer?.cancel();
    _sub?.cancel();
    _channel?.sink.close(ws_status.normalClosure);
    _channel = null;
  }
}
