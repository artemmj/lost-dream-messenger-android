package com.example.messenger.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(vm: LoginViewModel = hiltViewModel()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (vm.isRegister) "Регистрация" else "Вход",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = vm.phone,
            onValueChange = { vm.phone = it },
            label = { Text("Телефон") },
            singleLine = true,
            enabled = !vm.loading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = vm.password,
            onValueChange = { vm.password = it },
            label = { Text("Пароль") },
            singleLine = true,
            enabled = !vm.loading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        if (vm.isRegister) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.email,
                onValueChange = { vm.email = it },
                label = { Text("Email (необязательно)") },
                singleLine = true,
                enabled = !vm.loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.firstName,
                onValueChange = { vm.firstName = it },
                label = { Text("Имя (необязательно)") },
                singleLine = true,
                enabled = !vm.loading,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.lastName,
                onValueChange = { vm.lastName = it },
                label = { Text("Фамилия (необязательно)") },
                singleLine = true,
                enabled = !vm.loading,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        vm.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = vm::submit,
            enabled = !vm.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (vm.loading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
            } else {
                Text(if (vm.isRegister) "Создать аккаунт" else "Войти")
            }
        }
        OutlinedButton(
            onClick = vm::toggleMode,
            enabled = !vm.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (vm.isRegister) "Уже есть аккаунт" else "Создать аккаунт")
        }
    }
}
