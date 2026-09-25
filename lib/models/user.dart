class User {
  final String id;
  final String phone;
  final String? email;
  final String? firstName;
  final String? lastName;

  User({
    required this.id,
    required this.phone,
    this.email,
    this.firstName,
    this.lastName,
  });

  factory User.fromJson(Map<String, dynamic> j) => User(
        id: j['id'] as String,
        phone: j['phone'] as String? ?? '',
        email: (j['email'] as String?)?.isEmpty ?? true ? null : j['email'],
        firstName: (j['first_name'] as String?)?.isEmpty ?? true ? null : j['first_name'],
        lastName: (j['last_name'] as String?)?.isEmpty ?? true ? null : j['last_name'],
      );

  String get displayName {
    final name = [firstName, lastName].where((s) => s != null && s.isNotEmpty).join(' ');
    return name.isNotEmpty ? name : phone;
  }
}
