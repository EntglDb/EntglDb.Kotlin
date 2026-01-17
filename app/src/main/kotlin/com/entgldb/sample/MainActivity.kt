package com.entgldb.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.entgldb.sample.ui.theme.EntglDbTheme
import com.entgldb.sample.ui.MainScreen
import com.entgldb.sample.ui.TodoListScreen
import com.entgldb.sample.ui.ConflictDemoScreen

class MainActivity : ComponentActivity() {
    
    private val app: EntglDbApplication
        get() = application as EntglDbApplication
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            EntglDbTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            MainScreen(
                                app = app,
                                onNavigateToTodoLists = {
                                    navController.navigate("todoLists")
                                },
                                onNavigateToConflictDemo = {
                                    navController.navigate("conflictDemo")
                                }
                            )
                        }
                        
                        composable("todoLists") {
                            TodoListScreen(
                                app = app,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        
                        composable("conflictDemo") {
                            ConflictDemoScreen(
                                app = app,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
