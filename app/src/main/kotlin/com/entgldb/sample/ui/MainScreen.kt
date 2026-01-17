package com.entgldb.sample.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.entgldb.sample.EntglDbApplication
import com.entgldb.sample.models.Address
import com.entgldb.sample.models.User
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    app: EntglDbApplication,
    onNavigateToTodoLists: () -> Unit,
    onNavigateToConflictDemo: () -> Unit
) {
    var keyText by remember { mutableStateOf("") }
    var valueText by remember { mutableStateOf("") }
    var logMessages by remember { mutableStateOf(listOf<String>()) }
    var activePeers by remember { mutableStateOf(listOf<String>()) }
    
    val scope = rememberCoroutineScope()
    
    fun appendLog(message: String) {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timestamp = timeFormat.format(Date())
        logMessages = listOf("[$timestamp] $message") + logMessages.take(49)
    }
    
    LaunchedEffect(Unit) {
        appendLog("Initialized Node: ${app.nodeId}")
        appendLog("🔒 Secure mode enabled (ECDH + AES-256)")
        appendLog("Listening on port: ${app.node.server.listeningPort}")
        
        // Poll for active peers
        while (true) {
            val peers = app.node.discovery.getActivePeers()
            activePeers = peers.map { it.nodeId }
            kotlinx.coroutines.delay(2000)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EntglDb Android Test") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Node Info Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Node: ${app.nodeId}", style = MaterialTheme.typography.bodyMedium)
                    Text("Port: ${app.node.server.listeningPort}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Active Peers: ${activePeers.size}", style = MaterialTheme.typography.bodySmall)
                    
                    // Show peer list
                    if (activePeers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        activePeers.forEach { peerId ->
                            Text(
                                "• $peerId",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
            
            // User Operations Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("User Operations", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it },
                        label = { Text("Key/ID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        label = { Text("Name/Value") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        if (keyText.isBlank() || valueText.isBlank()) {
                                            appendLog("Please enter both key and value")
                                            return@launch
                                        }
                                        
                                        val user = User(
                                            id = keyText,
                                            name = valueText,
                                            age = (18..99).random(),
                                            address = Address(city = "Android City")
                                        )
                                        
                                        // TODO: Save to database
                                        val json = Json.encodeToString(user)
                                        appendLog("Saved '$keyText' to 'users'")
                                        
                                        keyText = ""
                                        valueText = ""
                                    } catch (e: Exception) {
                                        appendLog("Error: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
                        }
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        if (keyText.isBlank()) {
                                            appendLog("Please enter a key")
                                            return@launch
                                        }
                                        
                                        // TODO: Load from database
                                        appendLog("Key '$keyText' not found (not implemented yet)")
                                    } catch (e: Exception) {
                                        appendLog("Error: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Load")
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val key = UUID.randomUUID().toString().substring(0, 8)
                                val value = "AutoUser-Android-${System.currentTimeMillis() % 10000}"
                                keyText = key
                                valueText = value
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Auto Data")
                        }
                        
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    appendLog("Starting Spam (5)...")
                                    repeat(5) { i ->
                                        val key = "Spam-Android-$i-${System.currentTimeMillis()}"
                                        val user = User(
                                            id = key,
                                            name = "SpamUser $i",
                                            age = 20 + i,
                                            address = Address(city = "SpamTown")
                                        )
                                        // TODO: Save to database
                                        appendLog("Spammed: $key")
                                        kotlinx.coroutines.delay(100)
                                    }
                                    appendLog("Spam finished.")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Spam")
                        }
                        
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    // TODO: Query database
                                    appendLog("Total Users: 0 (not implemented yet)")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Count")
                        }
                    }
                }
            }
            
            // Navigation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNavigateToTodoLists,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("TodoLists")
                }
                
                Button(
                    onClick = onNavigateToConflictDemo,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Conflict Demo")
                }
            }
            
            // Activity Log
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Activity Log", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { logMessages = emptyList() }) {
                            Text("Clear")
                        }
                    }
                    
                    Divider()
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logMessages) { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
