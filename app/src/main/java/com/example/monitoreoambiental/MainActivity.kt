package com.example.monitoreoambiental

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.monitoreoambiental.data.GeoEdgeDatabase
import com.example.monitoreoambiental.data.LocalNodeRepository
import com.example.monitoreoambiental.data.RemoteNodeRepository
import com.example.monitoreoambiental.data.Security
import com.example.monitoreoambiental.ui.DevicesViewModel
import com.example.monitoreoambiental.ui.LoginScreen
import com.example.monitoreoambiental.ui.NodeScreen
import com.example.monitoreoambiental.ui.SupervisorScreen

// CAPA C (version final): login -> segun el rol, modo nodo o modo supervisor.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = GeoEdgeDatabase(this)
        val localRepository = LocalNodeRepository()
        val remoteRepository = RemoteNodeRepository()

        // Dos ViewModels de la MISMA clase: uno por cada fuente de datos
        val nodeFactory = viewModelFactory {
            initializer { DevicesViewModel(localRepository) }
        }
        val supervisorFactory = viewModelFactory {
            initializer { DevicesViewModel(remoteRepository) }
        }
        val nodeViewModel = ViewModelProvider(this, nodeFactory)
            .get("nodo", DevicesViewModel::class.java)
        val supervisorViewModel = ViewModelProvider(this, supervisorFactory)
            .get("supervisor", DevicesViewModel::class.java)

        setContent {
            MaterialTheme {
                val nodeState by nodeViewModel.uiState.collectAsStateWithLifecycle()
                val supervisorState by supervisorViewModel.uiState
                    .collectAsStateWithLifecycle()

                var user by remember { mutableStateOf("") }
                var role by remember { mutableStateOf<String?>(null) }

                when (role) {
                    null -> LoginScreen(database) { authenticatedUser, authenticatedRole ->
                        user = authenticatedUser
                        role = authenticatedRole
                    }
                    Security.ROLE_NODE -> NodeScreen(
                        user, nodeState, localRepository, database
                    ) { role = null }
                    else -> SupervisorScreen(
                        user, supervisorState, remoteRepository, database
                    ) { role = null }
                }
            }
        }
    }
}
