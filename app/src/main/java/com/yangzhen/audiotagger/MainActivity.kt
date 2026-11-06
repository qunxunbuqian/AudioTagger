package com.yangzhen.audiotagger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.yangzhen.audiotagger.ui.AudioTaggerTheme
import com.yangzhen.audiotagger.ui.TaggerApp

class MainActivity : ComponentActivity() {

    private val viewModel: TaggerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudioTaggerTheme {
                TaggerApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermission()
    }
}
