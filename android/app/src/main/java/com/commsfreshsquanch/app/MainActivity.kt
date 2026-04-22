package com.commsfreshsquanch.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.commsfreshsquanch.app.ui.CfsApp
import com.commsfreshsquanch.app.ui.CfsViewModel
import com.commsfreshsquanch.app.ui.CfsViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: CfsViewModel by viewModels {
        CfsViewModelFactory(application as CfsApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { CfsApp(viewModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "commsfreshsquanch" && uri.host == "callback") {
            viewModel.handleSpotifyCallback(uri)
        }
    }
}
