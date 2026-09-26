import 'package:flutter/foundation.dart';
import '../models/me.dart';
import '../services/api.dart';
import '../services/token_store.dart';

class AuthState extends ChangeNotifier {
  Me? _me;
  bool _loading = false;
  String? _error;

  Me? get me => _me;
  bool get loading => _loading;
  String? get error => _error;
  bool get isAuthenticated => _me != null;

  Future<bool> bootstrap() async {
    final token = await TokenStore.access;
    if (token == null) return false;
    try {
      _me = await Api().me();
      notifyListeners();
      return true;
    } catch (_) {
      await TokenStore.clear();
      return false;
    }
  }

  Future<bool> login(String phone, String password) async {
    _loading = true; _error = null; notifyListeners();
    try {
      await Api().login(phone, password);
      _me = await Api().me();
      return true;
    } on ApiException catch (e) {
      _error = e.message;
      return false;
    } finally {
      _loading = false; notifyListeners();
    }
  }

  Future<bool> register({
    required String phone,
    required String password,
    required String passwordConfirm,
    String? email,
    String? firstName,
    String? lastName,
  }) async {
    _loading = true; _error = null; notifyListeners();
    try {
      await Api().register(
        phone: phone, password: password, passwordConfirm: passwordConfirm,
        email: email, firstName: firstName, lastName: lastName,
      );
      // После регистрации сразу логинимся
      await Api().login(phone, password);
      _me = await Api().me();
      return true;
    } on ApiException catch (e) {
      _error = e.message;
      return false;
    } finally {
      _loading = false; notifyListeners();
    }
  }

  Future<bool> updateProfile(Map<String, dynamic> payload) async {
    try {
      _me = await Api().updateMe(payload);
      _error = null;
      notifyListeners();
      return true;
    } on ApiException catch (e) {
      _error = e.message;
      notifyListeners();
      return false;
    }
  }

  /// Выход из аккаунта.
  ///
  /// Очищает токены, сбрасывает состояние пользователя и очищает все данные чатов,
  /// чтобы предотвратить дальнейшие запросы к бэкенду с просроченными токенами.
  Future<void> logout() async {
    await TokenStore.clear();
    _me = null;
    notifyListeners();
  }
}
