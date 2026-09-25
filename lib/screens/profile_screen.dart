import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../state/auth_state.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});
  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  late final TextEditingController _phone;
  late final TextEditingController _email;
  late final TextEditingController _firstName;
  late final TextEditingController _lastName;

  @override
  void initState() {
    super.initState();
    final me = context.read<AuthState>().me;
    _phone = TextEditingController(text: me?.phone ?? '');
    _email = TextEditingController(text: me?.email ?? '');
    _firstName = TextEditingController(text: me?.firstName ?? '');
    _lastName = TextEditingController(text: me?.lastName ?? '');
  }

  @override
  void dispose() {
    _phone.dispose(); _email.dispose();
    _firstName.dispose(); _lastName.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final auth = context.read<AuthState>();
    final ok = await auth.updateProfile({
      'phone': _phone.text.trim(),
      'email': _email.text.trim(),
      'first_name': _firstName.text.trim(),
      'last_name': _lastName.text.trim(),
    });
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(ok ? 'Профиль обновлён' : (auth.error ?? 'Ошибка'))),
      );
      if (ok) Navigator.pop(context);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Профиль')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(controller: _firstName,
                decoration: const InputDecoration(labelText: 'Имя')),
            const SizedBox(height: 12),
            TextField(controller: _lastName,
                decoration: const InputDecoration(labelText: 'Фамилия')),
            const SizedBox(height: 12),
            TextField(controller: _email,
                decoration: const InputDecoration(labelText: 'Email')),
            const SizedBox(height: 12),
            TextField(controller: _phone,
                decoration: const InputDecoration(
                  labelText: 'Телефон',
                  helperText: 'Телефон — логин, вход будет по новому номеру',
                )),
            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              child: FilledButton(onPressed: _save, child: const Text('Сохранить')),
            ),
          ],
        ),
      ),
    );
  }
}
