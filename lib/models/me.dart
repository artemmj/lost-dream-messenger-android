class Me {
  final String id;
  final String phone;
  final String? email;
  final String? firstName;
  final String? lastName;
  final DateTime? lastSeen;

  Me({required this.id, required this.phone, this.email,
      this.firstName, this.lastName, this.lastSeen});

  factory Me.fromJson(Map<String, dynamic> j) => Me(
        id: j['id'],
        phone: j['phone'],
        email: j['email'],
        firstName: j['first_name'],
        lastName: j['last_name'],
        lastSeen: j['last_seen'] != null ? DateTime.parse(j['last_seen']) : null,
      );
}
