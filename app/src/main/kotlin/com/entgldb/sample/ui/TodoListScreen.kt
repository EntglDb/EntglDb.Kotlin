package com.entgldb.sample.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.entgldb.sample.EntglDbApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    app: EntglDbApplication,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TodoLists") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "TodoList management will be implemented here",
                style = MaterialTheme.typography.bodyLarge
            )
            
            Text(
                "Features:\n" +
                "• Create/view todo lists\n" +
                "• Add/complete/delete items\n" +
                "• Test recursive merge strategy",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
