# AGENTS.md — контекст для AI-ассистентов и инженеров

> Файл описывает текущее состояние Flutter-клиента: архитектуру, контракт бэкенда, соглашения
> и известные дефекты. Обновлять при значимых изменениях. Краткая версия для человека — [README.md](README.md).

## 1. Что это за проект

Клиент к Django-бэкенду [`lost-dream-messenger`](../lost-dream-messenger) (монорепозиторий:
Django в корне, Vue-фронтенд в `frontend/`). Бэкенд — **единственный источник правды о контракте**.
Любые сомнения о формате ответа, статус-коде, имени поля или кадре WebSocket решаются чтением
исходников бэкенда (`messenger/views.py`, `messenger/serializers.py`, `messenger/consumers.py`,
`messenger/routing.py`, `messenger/ratelimit.py`, `config/asgi.py`, `config/settings.py`), а не
комментариев в этом клиенте.

История: до Flutter здесь было нативное Android-приложение на Kotlin/Compose (Jetpack Compose,
Hilt, Retrofit + kotlinx.serialization, OkHttp WebSocket, DataStore). Оно удалено из рабочего
дерева, но сохранено в git: `git show 433543b --stat`, каталог `app/`. Kotlin-версия корректнее
текущей в трёх местах, где Dart-переписывание наступило на те же грабли: порядок сообщений внутри
страницы, заголовок `Origin` для WS-handshake, поведение личного канала уведомлений. При порче
логики сверяться с ней быстрее, чем с бэкендом.

Текущее состояние: **прототип**. Компилируется, основной сценарий «войти → список чатов → открыть
чат → отправить» частично работает, но WebSocket-слой не поднимается (см. дефект №1), а несколько
экранов ведут себя неправильно.

## 2. Окружение и команды

| | |
|---|---|
| Flutter | 3.47.5 stable, Dart 3.13.4 |
| pubspec `environment.sdk` | `^3.13.4` |
| Зависимости (разрешённые версии) | `http` 1.6.0, `web_socket_channel` 3.0.3, `provider` 6.1.5+1, `shared_preferences` 2.5.5, `intl` 0.20.3 (объявлен, не используется), `flutter_lints` 6.0.0 |

```bash
flutter pub get
flutter analyze          # сейчас падает: test/widget_test.dart → MyApp не определён
flutter test             # то же
flutter run -d <device>  # хост бэкенда правится в lib/config.dart
```

Бэкенд поднимается отдельно: `cd ../lost-dream-messenger && docker compose up`
(Postgres 18, Redis 7, Daphne на `:8000`, Vite-фронтенд на `:5173`).

`lib/config.dart:5` — `AppConfig.host` как compile-time константа: эмулятор Android `10.0.2.2:8000`,
симулятор iOS/macOS `127.0.0.1:8000`, реальное устройство — IP машины в LAN. `--dart-define` не
поддерживается (см. TODO).

## 3. Карта файлов

```
lib/
├── config.dart                  # AppConfig: host, apiBase (http://host/api/v1), wsBase (ws://host/ws)
├── main.dart                    # MultiProvider(AuthState, ChatState) → MaterialApp → _Boot
│                                # _Boot: bootstrap() решает, показать LoginScreen или ChatsScreen
├── models/                      # Плоские DTO с фабриками fromJson, без codegen
│   ├── user.dart                # User(id, phone, email?, firstName?, lastName?) + displayName
│   ├── me.dart                  # Me = User + lastSeen? (парсится, не отображается)
│   ├── message.dart             # Message(id, chat, sender, text, createdAt, isRead) + copyWith(isRead)
│   └── chat.dart                # ChatType{private,group}, ChatListItem(+copyWith, displayName),
│                                # ChatMember(user, isAdmin), ChatDetail(members, myIsAdmin)
├── services/
│   ├── api.dart                 # Api — синглтон. _request(method, path): 401 → refresh → один повтор.
│   │                            # ApiException(statusCode, message), _extractError — DRF detail / {field:[...]}
│   ├── token_store.dart         # TokenStore: статические access/refresh/save/clear поверх SharedPreferences
│   └── ws.dart                  # WsClient(path, onEvent, onClose, onReconnect): connect/ready/listen,
│                                # ретрай через 2 с, noReconnectCodes = {4001,4003,4004,4009,4029}
├── state/
│   ├── auth_state.dart          # me, loading, error; bootstrap/login/register/updateProfile/logout
│   └── chat_state.dart          # chats, currentChatDetail, messages, selectedChatId, onlineUsers,
│                                # wsStatus, пагинация истории; сокет чата + сокет уведомлений
└── screens/
    ├── login_screen.dart        # Вход/регистрация в одном экране, переключение _isRegister
    ├── chats_screen.dart        # Список чатов, FAB «новый чат», иконки профиля и выхода
    ├── chat_screen.dart         # Лента, поле ввода, шапка со статусом WS, вход в участников группы
    ├── new_chat_screen.dart     # Поиск пользователей, режим «личный / групповой», чипы выбранных
    ├── group_members_screen.dart# Участники группы с ролью и кнопкой удаления
    └── profile_screen.dart      # PATCH своих полей (имя, фамилия, email, телефон)
test/widget_test.dart            # Шаблонный тест счётчика — не компилируется (см. дефект №15)
android/ ios/ macos/ windows/ linux/ web/   # Сгенерированные `flutter create` цели
```

Навигация — только `Navigator.push(MaterialPageRoute(...))` из `chats_screen.dart` и
`chat_screen.dart`. Именованных маршрутов, `go_router`, deep links и state restoration нет.
`ChatScreen` получает `chatId` конструктором, но данные берёт из глобального `ChatState`
(`selectedChatId`), то есть состояние чата живёт вне маршрута.

## 4. Архитектура

**Состояние.** Два `ChangeNotifier` в `MultiProvider` (`lib/main.dart:10-18`), оба создаются
при старте приложения, до авторизации. Экраны читают через `context.watch<T>()` (пересборка) и
`context.read<T>()` (вызов методов). Отдельных ViewModel на экран нет, DI нет: `Api` — синглтон
(`lib/services/api.dart:19-21`), `TokenStore` — статические методы.

**Загрузка сессии.** `_Boot` (`lib/main.dart:36-65`) хранит результат `auth.bootstrap()` в локальном
`_authed` и не слушает `AuthState` дальше: после `logout()` дерево не перестраивается на
`LoginScreen` — отсюда дефект №4.

**REST.** `Api._request` собирает заголовки (`Content-Type`, `Authorization: Bearer` из
`TokenStore`), при 401 делает `POST /auth/refresh/` и повторяет исходный запрос ровно один раз
(`retryOn401: false`); если refresh не принят — токены стираются. Методы проверяют конкретный
ожидаемый статус-код и иначе бросают `ApiException` с текстом из `_extractError` (DRF `detail`
или первое сообщение валидации поля).

**WebSocket.** Один класс `WsClient` на оба канала, различаются только `path`:
`chat/<uuid>/` и `notifications/`. Токен передаётся query-параметром `?token=<jwt>` (заголовки в
WS-handshake бэкенд не читает). Жизненный цикл:

- сокет чата открывается в `ChatState.selectChat()` (`lib/state/chat_state.dart:44-60`) после
  загрузки первой страницы, деталей и `markRead`; закрывается в `closeChat()` и `dispose()`;
- сокет уведомлений открывается в `ChatsScreen.initState()` (`lib/screens/chats_screen.dart:18-23`)
  и закрывается при выходе из аккаунта;
- переподключение — фиксированные 2 с, кроме кодов `noReconnectCodes`
  (`lib/services/ws.dart:9`): там ретрай только продлевал бы блокировку по лимитам.

**Поток события.** Кадр WS → `WsClient.onEvent` → `ChatState._handleChatEvent` (события открытого
чата: сообщение без `type`, `user_status`, `initial_presence`, `messages_read`) или
`_handleNotification` (личный канал: `new_message`, `chat_read`, `chat_deleted`, `chat_renamed`,
`member_removed`) → мутация списков → `notifyListeners()` → пересборка экранов.

**Отправка.** `ChatState.sendMessage` (`lib/state/chat_state.dart:109-126`): если сокет жив —
`{'text': ...}` в WS и возврат `null` (своё сообщение придёт эхом из broadcast группы), иначе
`POST /chats/{id}/send/` и добавление в ленту. Экран по возврату `null`/не-`null` решает,
скроллить ли ленту — отсюда дефект №12.

## 5. Контракт бэкенда

Префикс REST — `/api/v1`. Аутентификация — `Authorization: Bearer <access>`, JWT SimpleJWT
(в payload только `user_id`, поэтому профиль всегда догружается `GET /users/me/`).
Пагинация `PageNumberPagination`, страница 50, ответ `{count, next, previous, results}`.

### REST

| Метод | Путь | Статус | Клиент |
|-------|------|--------|--------|
| POST | `/auth/register/` | 201, `{user, access, refresh}` | `Api.register` |
| POST | `/auth/login/` | 200, `{access, refresh}`, поле `phone` | `Api.login` |
| POST | `/auth/refresh/` | 200, `{access}` | `Api._refreshToken` |
| GET | `/users/me/` | 200, `Me` | `Api.me` |
| PATCH | `/users/me/` | 200, `Me`; поля опциональны, `email` допускает пустую строку, телефон нормализуется | `Api.updateMe` |
| GET | `/users/search/?q=` | 200, страница `User`, исключает себя, лимит 20 | `Api.searchUsers` |
| GET | `/chats/?page=` | 200, страница `ChatListItem` с `last_message`, `interlocutor`, `unread_count` | `Api.listChats` |
| POST | `/chats/` | 201, `ChatDetail`; для GROUP обязательны `name` и допустим `member_ids` | `Api.createGroupChat` |
| POST | `/chats/private/` | 200 или 201, `ChatDetail`; идемпотентно для пары | `Api.createPrivateChat` |
| GET | `/chats/{id}/` | 200, `ChatDetail` с `members[].is_admin` и `my_is_admin` | `Api.chatDetail` |
| PATCH | `/chats/{id}/` | 200, `ChatDetail`; только GROUP и только админ | `Api.renameChat` |
| DELETE | `/chats/{id}/` | 204 | `Api.deleteChat` |
| GET | `/chats/{id}/messages/?page=` | 200, страница `Message`; **страница 1 — последние 50, но внутри страницы порядок по возрастанию времени** (`views.py` сериализует `page[::-1]`) | `Api.messages` |
| POST | `/chats/{id}/send/` | 201, `Message` | `Api.sendMessage` |
| POST | `/chats/{id}/read/` | 200, `{"unread_count": 0}`; сдвигает курсор `Membership.last_read_at` | `Api.markRead` |
| POST | `/chats/{id}/add-member/` | 201; только GROUP, только админ | `Api.addMember` |
| POST | `/chats/{id}/remove-member/` | 200; админ удаляет других, участник — себя; единственного админа удалить нельзя | `Api.removeMember` |

Прочтение — курсор на участнике, а не read-receipt на сообщение: `Message.is_read` остаётся
глобальным флагом, поэтому галочка ✓✓ означает «кто-то прочитал», а не «собеседник прочитал».

### WebSocket

| Endpoint | Назначение |
|----------|------------|
| `ws/chat/<uuid>/?token=<jwt>` | real-time одного чата (открыт, пока чат выбран) |
| `ws/notifications/?token=<jwt>` | личный канал: события по всем чатам, presence, `last_seen` |

Кадры канала чата (сервер → клиент):

| `type` | Payload | Обработка в клиенте |
|--------|---------|---------------------|
| *(нет)* | `{id, chat, sender{id,phone,first_name,last_name}, text, created_at, is_read}` | `_handleChatEvent`, ветка `null` |
| `user_status` | `{user_id, status: online\|offline}` | `onlineUsers` (не отображается) |
| `initial_presence` | `{user_ids: [...]}` — снимок при подключении | `onlineUsers` (не отображается) |
| `messages_read` | `{reader_id}` — приходит и самому читателю | помечает все сообщения прочитанными |
| `{error: "..."}` | пустой текст, длина > 5000, анти-флуд | **не обрабатывается** (дефект №9) |

Клиент → сервер: только `{"text": "..."}`.

Кадры личного канала:

| `type` | Payload | Обработка |
|--------|---------|-----------|
| `new_message` | `{chat, message, unread_count}` | обновляет превью и бейдж; неизвестный чат → `loadChats()` |
| `chat_read` | `{chat}` | бейдж в 0 (прочитано на другом устройстве) |
| `chat_deleted` | `{chat}` | `removeChat` |
| `chat_renamed` | `{chat, name}` | `applyRename` |
| `member_removed` | `{chat}` | `removeChat` (нас исключили) |

Коды закрытия (все — отказ до `accept`):

| Код | Причина | Реакция клиента |
|-----|---------|-----------------|
| 4001 | невалидный/просроченный JWT | не переподключаться |
| 4003 | не участник чата / исключён | не переподключаться, `removeChat` |
| 4004 | чат удалён | не переподключаться, `removeChat` |
| 4009 | кап одновременных соединений (5 на пользователя) | не переподключаться |
| 4029 | частые подключения (>20/мин) | не переподключаться |
| 403 (HTTP, не WS-код) | handshake отклонён `AllowedHostsOriginValidator` | сейчас — бесконечный ретрай (дефект №1) |

### Лимиты бэкенда

Превышение → HTTP 429 + `Retry-After`, тело `{"detail": "Request was throttled..."}` (по-английски).

| Scope | Лимит | Что задевает в клиенте |
|-------|-------|------------------------|
| `anon` | 120/мин на IP | — |
| `user` | 600/мин на пользователя | все авторизованные запросы |
| `auth` | 10/мин на IP | `login`, лишний `login` после `register` (дефект №14) |
| `register` | 5/мин на IP | регистрация |
| `send` | 60/мин | REST-фолбэк отправки |
| `read` | 120/мин | `markRead` при каждом открытии чата |
| `write` | 30/мин | создание/переименование/удаление чата, участники |
| `search` | 20/мин | поиск в `new_chat_screen` — сгорает из-за дефекта №8 |
| `profile` | 20/мин | `PATCH /users/me/` |

WS-лимиты (`messenger/ratelimit.py`): сообщения 10/10 с (иначе кадр `{error}`), подключения 20/60 с
(4029), не более 5 одновременных соединений на пользователя (4009).

**`Origin` обязателен.** `config/asgi.py` оборачивает WS-роутер в `AllowedHostsOriginValidator`;
в `channels/security/websocket.py::OriginValidator.valid_origin` отсутствие заголовка трактуется
как отказ, если в `ALLOWED_HOSTS` нет `*`. Браузер шлёт `Origin` сам, `dart:io` — нет. Хост из
`Origin` должен входить в `ALLOWED_HOSTS` бэкенда (`config/settings.py:8-14`, `10.0.2.2` там есть).

## 6. Соглашения кода

- UI-строки — по-русски, захардкожены (локализации нет); тексты ошибок приходят с бэкенда
  по-английски для 429 и по-русски для业务-ошибок — показываются как есть.
- Модели: обычные классы с `final`-полями и фабрикой `fromJson`, snake_case ключи JSON читаются
  напрямую, `copyWith` только там, где реально нужен. Codegen (`json_serializable`, `freezed`)
  не используется и не добавлять без необходимости.
- Все сетевые вызовы идут через `Api` (синглтон), состояние — через `ChatState`/`AuthState`.
  Экраны не должны дёргать `Api` напрямую; сейчас это нарушено в `new_chat_screen.dart` и
  `group_members_screen.dart`.
- `DateTime.parse(...).toLocal()` — сервер отдаёт ISO-8601 со смещением.
- Линты — дефолтный `flutter_lints`; `analysis_options.yaml` исключает сгенерированные каталоги
  платформ. Стиль существующего кода (короткие строки с несколькими объявлениями, `catch (_) {}`)
  местами линтам не отвечает — не устраивать массовую чистку вместе с функциональной правкой.

## 7. Известные дефекты

Порядок — по важности. Строки указаны на текущее состояние рабочего дерева.

### Критичные (ломают основной сценарий)

1. **WS-handshake без `Origin` → 403.** `lib/services/ws.dart:34` использует
   `WebSocketChannel.connect(uri)`, который заголовки не отправляет; `WebSocketChannel.connect`
   в `web_socket_channel` 3.0.3 параметра `headers` не имеет вовсе — нужен
   `IOWebSocketChannel.connect(uri, headers: {'Origin': 'http://<host>'})` из
   `package:web_socket_channel/io.dart` (на web-цели заголовки недоступны). Сейчас `await ready`
   бросает исключение, `catch (_) { _scheduleReconnect(); }` (`:51-53`) не вызывает `onClose`,
   поэтому статус остаётся `connecting`, а ретраи идут каждые 2 с бесконечно.
2. **Порядок сообщений перевёрнут.** `lib/state/chat_state.dart:66` (`page.reversed`) и `:89`
   (`[...older.reversed, ...]`): бэкенд отдаёт страницу уже по возрастанию времени
   (`messenger/views.py`, `MessageSerializer(page[::-1])`). Лента показывается сверху вниз от
   новых к старым, дозагрузка вставляет старую страницу не с того края. Комментарий на `:65`
   описывает поведение, которого на бэкенде нет.
3. **Статус соединения не обновляется.** `setConnected()` (`lib/state/chat_state.dart:252`) не
   вызывается ниоткуда; `_wsStatus = 'connected'` проставляется только в `_handleChatReconnect`
   (`:243`), то есть после переподключения. Шапка чата показывает «подключение…» даже на живом
   сокете. В `WsClient` нет колбэка `onOpen` — только `onReconnect` с хрупким условием
   `_hadConnection && _retryTimer != null` (`lib/services/ws.dart:36-40`; `_retryTimer` после
   срабатывания не обнуляется).
4. **Выход из аккаунта → пустой экран.** `lib/screens/chats_screen.dart:44-49` пушит
   `_RedirectToLogin`, а тот рендерит `SizedBox.shrink()` (`:106-110`). `LoginScreen` не
   показывается. Правильно — либо пушить `LoginScreen`, либо сделать `_Boot` наблюдателем
   `AuthState` (`lib/main.dart:42-64`) и убрать ручную навигацию из `LoginScreen._submit`
   (`lib/screens/login_screen.dart:52-55`).
5. **Созданный чат не открывается.** `lib/screens/new_chat_screen.dart:39-43` и `:56-63`:
   вызываются `loadChats()` и `selectChat(detail.id)` (открывает сокет чата), затем
   `Navigator.pop()` — пользователь оказывается в списке, хотя `selectedChatId` установлен и
   сокет жив. Нужен `pushReplacement` на `ChatScreen`.
6. **Удаление себя из группы.** `lib/screens/group_members_screen.dart:31` сравнивает
   `m.user.id` с `ChatState.selectedChatId` (id чата) — условие всегда истинно, кнопка удаления
   рисуется в том числе на собственной строке. Сравнивать надо с `AuthState.me?.id`; заодно
   там же отсутствует кнопка «выйти из чата» и обработка правила «единственного админа удалить
   нельзя».
7. **Выбор участников группы теряет состояние.** `_selected` — `Set<User>`
   (`lib/screens/new_chat_screen.dart:17`), а `User` (`lib/models/user.dart`) не переопределяет
   `==`/`hashCode`. Каждый поиск создаёт новые экземпляры, поэтому `_selected.contains(u)` для
   того же человека даёт `false`: чекбоксы сбрасываются, а в `member_ids` могут попасть дубли —
   бэкенд на это отвечает 400 (`validate_member_ids` в `messenger/serializers.py`). Хранить
   множество id или переопределить равенство.

### Функциональные

8. **Поиск без отменяемого дебаунса.** `lib/screens/new_chat_screen.dart:103-106` —
   `Future.delayed` на каждое изменение поля: запросы не отменяются и не схлопываются, при
   наборе имени улетает десяток запросов в scope `search` (20/мин) → 429. Нужен `Timer` с
   `cancel()`.
9. **Кадры `{error: ...}` теряются.** `lib/state/chat_state.dart:200-231`: у error-кадра нет ни
   `type`, ни `id`, поэтому switch не делает ничего. Пустое сообщение, текст длиннее 5000
   символов и срабатывание анти-флуда (10 сообщений/10 с) пользователь не видит — ввод просто
   очищается (`lib/screens/chat_screen.dart:46`).
10. **`messages_read` обрабатывается грубо.** `lib/state/chat_state.dart:225-230` помечает
    прочитанными все сообщения и игнорирует `reader_id` (переменная объявлена и не используется),
    хотя бэкенд рассылает событие и самому читателю.
11. **Пагинация игнорируется.** `Api.listChats` (`lib/services/api.dart:167-172`) всегда берёт
    страницу 1 — чаты дальше первых 50 не видны; `Api.messages` (`:213-218`) выбрасывает `next`,
    поэтому `hasMoreMessages` угадывается по `length == 50` (`lib/state/chat_state.dart:67,91`) и
    врёт, когда сообщений ровно 50.
12. **Лента не прокручивается к новому сообщению.** Автоскролл завязан на `sent != null`
    (`lib/screens/chat_screen.dart:45-55`), а WS-эхо возвращает `null`
    (`lib/state/chat_state.dart:115`). Кроме того, `ListView` не `reverse: true`, а при
    дозагрузке истории (`:33-37`) позиция скролла не сохраняется — список прыгает.
13. **Системный «назад» не закрывает чат.** `closeChat()` вызывается только с крестика в шапке
    (`lib/screens/chat_screen.dart:89-95`); `PopScope` нет, поэтому свайп/кнопка назад оставляет
    сокет открытым и `selectedChatId` установленным.
14. **Регистрация делает лишний вход.** Бэкенд возвращает токены сразу
    (`messenger/views.py:552-580`), `Api.register` их сохраняет (`lib/services/api.dart:138-140`),
    но `AuthState.register` (`lib/state/auth_state.dart:53-59`) затем всё равно вызывает `login()`
    — лишний запрос в scope `auth` (10/мин). Комментарий `lib/services/api.dart:136-137`
    («Register возвращает токены?») устарел и вводит в заблуждение.
15. **Ошибки глушатся.** `catch (_) {}` в `lib/state/chat_state.dart:41,69,76,93,106`,
    `lib/state/auth_state.dart:23`, `lib/screens/new_chat_screen.dart:34`: ни офлайн, ни 429,
    ни 403 не доходят до UI — экран выглядит пустым или устаревшим.
16. **Нет обработки разрыва сети и смены токена в живых сокетах.** После refresh сокеты не
    переподключаются с новым токеном; при 4001 (`_closedByUser == false`, код в
    `noReconnectCodes`) клиент просто молчит, сессия не сбрасывается и экраны не возвращаются
    к логину.

### Гигиена

17. **`test/widget_test.dart:16`** — шаблонный тест счётчика, ссылается на `MyApp`, которого нет
    (`lib/main.dart:20` объявляет `MessengerApp`). Из-за этого `flutter analyze` и `flutter test`
    завершаются ошибкой, а других тестов в проекте нет.
18. **Неиспользуемый импорт** `dart:async` в `lib/state/chat_state.dart:1` (`Timer`/`Completer`
    там не применяются).
19. **Мёртвый код.** Не вызывается: `ChatState.setConnected` (`:252`), `ChatState.renameChat`
    (`:133`), `ChatState.deleteChat` (`:128`), `Api.addMember` (`lib/services/api.dart:231`);
    `ChatState.onlineUsers` (`:18`) заполняется, но не отображается; `Me.lastSeen`
    (`lib/models/me.dart:7`) парсится и не используется; пакет `intl` объявлен в `pubspec.yaml`,
    но не импортирован — время форматируется вручную (`lib/screens/chat_screen.dart:187`).
    Либо доводить до UI, либо удалять.
20. **Строковая типизация там, где есть enum.** `lib/screens/chat_screen.dart:82` —
    `detail?.type.name == 'group'` вместо `== ChatType.group` (мешает отсутствие импорта
    `models/chat.dart`); `wsStatus` — строки `'connected'/'connecting'/'disconnected'`
    (`lib/state/chat_state.dart:24`) вместо enum.

### Платформенные конфиги

21. **Android**: `android/app/src/main/AndroidManifest.xml` без `INTERNET` (разрешение есть
    только в `src/debug/` и `src/profile/`) → release-сборка не выйдет в сеть; для HTTP без TLS
    нужен `android:usesCleartextTraffic="true"` либо `network_security_config`.
22. **macOS**: в `macos/Runner/DebugProfile.entitlements` и `Release.entitlements` нет
    `com.apple.security.network.client` (в стандартном шаблоне Flutter он есть) → исходящие
    соединения блокирует песочница.
23. **iOS**: в `ios/Runner/Info.plist` нет `NSLocalNetworkUsageDescription` — нужно для обращения
    к IP в локальной сети (iOS 14+). `10.0.2.2` работает только на эмуляторе Android.
24. **web**: цель нерабочая без правок бэкенда — `CORS_ALLOWED_ORIGINS` (`config/settings.py:207`)
    разрешает только `localhost:5173`/`127.0.0.1:5173`, а браузерный `Origin` с порта Flutter-web
    не входит в `ALLOWED_HOSTS` (снова 403 на WS). Заголовки в `IOWebSocketChannel` на web
    недоступны в принципе.
25. **Имя пакета** `lost_dream_messenger_android` и `description: "A new Flutter project."` в
    `pubspec.yaml:1-3`, тот же `android:label` в манифесте — проект уже кроссплатформенный.

### Безопасность

26. JWT лежит открытым текстом в `SharedPreferences` (`lib/services/token_store.dart`) — для
    прод-сборки нужен `flutter_secure_storage` (Keychain/Keystore). Трафик — HTTP без TLS.
27. `lib/models/user.dart:19-21` — нормализация пустых строк в `null` записана через
    `?.isEmpty ?? true ? null : ...`: работает, но читается как ошибка приоритетов; при правке
    добавить скобки.

## 8. Что не реализовано (по сравнению с бэкендом и Kotlin-версией)

- переименование группы и удаление чата из UI (методы есть в `ChatState`, вызовов нет);
- добавление участника в существующую группу (`Api.addMember`);
- выход из чата самим участником;
- отображение presence: точки «в сети» в шапке чата и в списке участников, счётчик
  «N в сети из M», `last_seen` («был в сети …»);
- пагинация списка чатов и корректный `next` в истории;
- оптимистичная отправка и очередь сообщений при обрыве связи;
- плашки состояния: индикатор соединения в списке чатов, человекочитаемые причины закрытия сокета
  (4003 «вы больше не участник», 4004 «чат удалён», 4029 «слишком частые переподключения») —
  в Kotlin-версии это `closeNotice(code)`;
- пустые состояния и скелетоны загрузки (сейчас `CircularProgressIndicator` только в списке
  участников и при дозагрузке истории);
- поиск по истории, пересылка, вложения — на бэкенде этого тоже нет;
- push-уведомления;
- локализация и темы;
- тесты (unit для `ChatState`/`Api`, widget для экранов) и CI.

## 9. Правила для ассистентов

- Контракт сверять с исходниками бэкенда, а не с комментариями клиента: комментарии здесь уже
  один раз разошлись с реальностью (`lib/state/chat_state.dart:65`, `lib/services/api.dart:136`).
- Бэкенд не править. Если клиент упирается в ограничение бэкенда (CORS, `ALLOWED_HOSTS`,
  `Origin`, лимиты) — фиксировать это в разделе 7 и предлагать правку клиента либо явно
  спрашивать про бэкенд.
- Не добавлять codegen, DI-фреймворки и новые пакеты без явной необходимости: текущий стек
  намеренно плоский (`http` + `provider` + ручной JSON).
- Не вводить `go_router`/именованные маршруты попутно с функциональной правкой.
- Строки UI — по-русски.
- Не запускать сборку, тесты и приложение «для проверки» и не коммитить: это делает пользователь.
  Исключение — если он явно попросил прогнать `flutter analyze`/`flutter test`.
- Не устраивать массовую чистку стиля и линтов вместе с содержательной правкой.

## 10. Порядок работ, если доводить до рабочего состояния

1. Дефекты №1–№3 (`Origin`, порядок сообщений, статус соединения) — без них real-time не работает
   вовсе, а лента отображается неправильно.
2. №4–№7 (выход, открытие созданного чата, удаление себя, выбор участников) — ломаются базовые
   пользовательские сценарии.
3. №8–№16 — поведение под нагрузкой и обратная связь: дебаунс поиска, error-кадры, пагинация,
   скролл, всплывающие ошибки.
4. №17–№20 — анализатор, тесты, мёртвый код.
5. №21–№25 — платформенные конфиги, если нужны не-Android цели.
6. Раздел 8 — функциональные пробелы (presence, переименование, удаление, добавление участников).
