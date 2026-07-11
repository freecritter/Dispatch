package com.freecritter.dispatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import com.freecritter.dispatch.ui.nav.DispatchNav
import com.freecritter.dispatch.ui.theme.DispatchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DispatchTheme {
                Surface {
                    DispatchNav(
                        repository = (application as DispatchApp).repository,
                        keyManager = (application as DispatchApp).keyManager,
                    )
                }
            }
        }
    }
}