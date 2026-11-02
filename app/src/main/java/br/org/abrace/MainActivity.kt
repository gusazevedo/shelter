package br.org.abrace

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import br.org.abrace.ui.AbraceApp

class MainActivity : ComponentActivity() {
    private val model: AbraceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AbraceApp(model) }
        if (savedInstanceState == null) intent?.data?.let(model::acceptLink)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let(model::acceptLink)
    }
}
