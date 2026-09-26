import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'state/auth_state.dart';
import 'state/chat_state.dart';
import 'screens/login_screen.dart';
import 'screens/chats_screen.dart';

void main() {
  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => AuthState()),
        ChangeNotifierProvider(create: (_) => ChatState()),
      ],
      child: const MessengerApp(),
    ),
  );
}

class MessengerApp extends StatelessWidget {
  const MessengerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Мессенджер',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const _Boot(),
    );
  }
}

class _Boot extends StatefulWidget {
  const _Boot();
  @override
  State<_Boot> createState() => _BootState();
}

class _BootState extends State<_Boot> {
  bool _checking = true;

  @override
  void initState() {
    super.initState();
    _check();
  }

  /// Проверяет сохранённые токены и догружает профиль через bootstrap().
  Future<void> _check() async {
    final auth = context.read<AuthState>();
    await auth.bootstrap();
    if (mounted) setState(() => _checking = false);
  }

  @override
  Widget build(BuildContext context) {
    if (_checking) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    // Следим за AuthState через watch: login/logout вызывают notifyListeners(),
    // и это дерево перестраивается само — ручная навигация между экранами не нужна.
    // Важно: экраны НЕ должны толкать маршруты поверх _Boot (как делал LoginScreen
    // после входа), иначе logout не возвращал бы к форме входа — толкнутый маршрут
    // ChatsScreen оставался бы в стеке поверх перестроенного _Boot.
    final auth = context.watch<AuthState>();
    return auth.isAuthenticated ? const ChatsScreen() : const LoginScreen();
  }
}
