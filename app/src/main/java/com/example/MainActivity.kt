package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.ui.AppNavigationWrapper
import com.example.ui.AppViewModel
import com.example.ui.AppViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize local-first architecture securely
        val database = AppDatabase.getDatabase(this)
        val repository = AppRepository(database.appDao())
        
        val viewModel: AppViewModel by viewModels {
            AppViewModelFactory(repository)
        }

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                AppNavigationWrapper(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
