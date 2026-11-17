package com.farmfamily.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private val viewModel: FarmFamilyViewModel by viewModels()
    private val authCallback = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authCallback.value = intent?.data
        setContent {
            FarmDesignSystem(darkMode = viewModel.darkMode.value) {
                FarmFamilyApp(
                    viewModel = viewModel,
                    initialCallback = authCallback.value,
                    onCallbackHandled = { authCallback.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        authCallback.value = intent.data
    }
}
