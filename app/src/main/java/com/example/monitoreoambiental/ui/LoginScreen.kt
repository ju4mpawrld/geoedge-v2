package com.example.monitoreoambiental.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.monitoreoambiental.data.GeoEdgeDatabase
import com.example.monitoreoambiental.data.Security

// CAPA B: login contra SQLite local. El rol que devuelve la base decide
// a que pantalla se entra (minimo privilegio).
@Composable
fun LoginScreen(
    database: GeoEdgeDatabase,
    onAuthenticated: (user: String, role: String) -> Unit
) {
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var failedAttempts by remember { mutableStateOf(0) }
    var lockedUntil by remember { mutableStateOf(0L) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text("GeoEdge", style = MaterialTheme.typography.headlineLarge)
            Text("Monitoreo de derrumbes en túneles mineros")
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Usuario") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val now = System.currentTimeMillis()

                    // Resistencia a fuerza bruta: bloqueo temporal
                    if (now < lockedUntil) {
                        val remaining = ((lockedUntil - now) / 1000) + 1
                        message = "Acceso bloqueado. Espere $remaining segundos."
                        return@Button
                    }
                    if (user.isBlank() || password.isBlank()) {
                        message = "Ingrese usuario y contraseña."
                        return@Button
                    }

                    val role = database.authenticate(user, password)

                    if (role == null) {
                        failedAttempts += 1
                        if (failedAttempts >= Security.MAX_ATTEMPTS) {
                            lockedUntil = now + Security.LOCKOUT_MILLIS
                            failedAttempts = 0
                            message = "Demasiados intentos. Bloqueado 30 segundos."
                        } else {
                            // Mismo mensaje si el usuario no existe o la clave es mala
                            val left = Security.MAX_ATTEMPTS - failedAttempts
                            message = "Credenciales incorrectas. Intentos restantes: $left"
                        }
                        password = ""
                        return@Button
                    }

                    failedAttempts = 0
                    message = ""
                    val authenticatedUser = user.trim()
                    password = ""
                    onAuthenticated(authenticatedUser, role)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ingresar")
            }

            if (message.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(message, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Demostración:\n" +
                    "nodo / Nodo123  →  equipo del túnel\n" +
                    "supervisor / Super123  →  equipo en superficie"
            )
        }
    }
}
