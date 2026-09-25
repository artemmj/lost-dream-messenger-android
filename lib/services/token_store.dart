import 'package:shared_preferences/shared_preferences.dart';

class TokenStore {
  static const _kAccess = 'access_token';
  static const _kRefresh = 'refresh_token';

  static Future<void> save(String access, String refresh) async {
    final p = await SharedPreferences.getInstance();
    await p.setString(_kAccess, access);
    await p.setString(_kRefresh, refresh);
  }

  static Future<String?> get access async =>
      (await SharedPreferences.getInstance()).getString(_kAccess);

  static Future<String?> get refresh async =>
      (await SharedPreferences.getInstance()).getString(_kRefresh);

  static Future<void> clear() async {
    final p = await SharedPreferences.getInstance();
    await p.remove(_kAccess);
    await p.remove(_kRefresh);
  }
}
