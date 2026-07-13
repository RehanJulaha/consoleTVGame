package com.retrogamestick.console

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.CenterHorizontally
import androidx.compose.ui.Alignment.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LobbyScreen()
                }
            }
        }
    }
}

@Composable
fun LobbyScreen() {
    var p1Connected by remember { mutableStateOf(false) }
    var p2Connected by remember { mutableStateOf(false) }
    var p1Name by remember { mutableStateOf("") }
    var p2Name by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "RETRO GAMESTICK",
            fontSize = 48.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = androidx.compose.foundation.layout.Modifier.height(48.dp))
        
        PlayerCard(
            playerNumber = 1,
            connected = p1Connected,
            name = p1Name,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = androidx.compose.foundation.layout.Modifier.height(24.dp))
        
        PlayerCard(
            playerNumber = 2,
            connected = p2Connected,
            name = p2Name,
            color = MaterialTheme.colorScheme.secondary
        )
        
        Spacer(modifier = androidx.compose.foundation.layout.Modifier.height(48.dp))
        
        Button(
            onClick = { /* Start game */ },
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(64.dp),
            enabled = p1Connected || p2Connected
        ) {
            Text(text = "START GAME", fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        
        Spacer(modifier = androidx.compose.foundation.layout.Modifier.height(24.dp))
        
        if (!p1Connected || !p2Connected) {
            PairingInfoCard()
        }
    }
}

@Composable
fun PlayerCard(
    playerNumber: Int,
    connected: Boolean,
    name: String,
    color: Color
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(120.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (connected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "P$playerNumber",
                    fontSize = 32.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = if (connected) color else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = androidx.compose.foundation.layout.Modifier.width(24.dp))
            
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = if (connected) "CONNECTED" : "WAITING...",
                    fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (connected && name.isNotBlank()) {
                    Text(text = name, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun PairingInfoCard() {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(0.8f),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "PAIR CONTROLLERS",
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = androidx.compose.foundation.layout.Modifier.height(8.dp))
            Text(
                text = "1. Install Controller app on phone\n2. Connect to same WiFi\n3. App will auto-discover this TV\n4. Enter PIN shown on screen",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}