package com.retrogamestick.controller

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.awaitPointerEventScope
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerMove
import androidx.compose.ui.input.pointer.pointerUp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.BoxScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retro.gamestick.network.InputFrame
import retro.gamestick.network.InputState

class MainActivity : ComponentActivity() {
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + supervisorJob)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ControllerScreen()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
fun ControllerScreen() {
    val isConnected by remember { mutableStateOf(false) }
    val myPlayerSlot by remember { mutableStateOf(-1) }
    val sessionId by remember { mutableStateOf("") }
    
    val dPadState = remember { mutableStateOf(DPadState()) }
    val buttonsState = remember { mutableStateOf(ButtonsState()) }
    
    // Simple 60fps input loop (stub)
    LaunchedEffect(isConnected) {
        if (isConnected) {
            while (isConnected) {
                val frame = InputFrame(
                    playerSlot = myPlayerSlot,
                    frameId = 0,
                    timestampUs = System.currentTimeMillis() * 1000,
                    state = InputState(
                        buttons = buildButtonBitmask(buttonsState.value),
                        lx = dPadX(dPadState.value),
                        ly = dPadY(dPadState.value),
                        rx = 0,
                        ry = 0
                    )
                )
                // networkManager.sendInputFrame(frame) // TODO
                kotlinx.coroutines.delay(16) // ~60fps
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {}
        
        if (!isConnected) {
            // Connecting screen
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Searching for RetroGameStick...",
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = androidx.compose.foundation.layout.Modifier.height(16.dp))
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else {
            // Gamepad layout
            GamepadLayout(
                myPlayerSlot = myPlayerSlot,
                dPadState = dPadState,
                buttonsState = buttonsState
            )
        }
    }
}

@Composable
fun GamepadLayout(
    myPlayerSlot: Int,
    dPadState: androidx.compose.runtime.MutableState<DPadState>,
    buttonsState: androidx.compose.runtime.MutableState<ButtonsState>
) {
    val playerColor = if (myPlayerSlot == 0) Color(0xFF2196F3) else Color(0xFFE91E63)
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Player indicator
        Text(
            text = "PLAYER ${myPlayerSlot + 1}",
            fontSize = 24.sp,
            color = playerColor,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        
        // Main gamepad area
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // D-Pad (Left)
            DPadZone(
                state = dPadState,
                color = playerColor,
                modifier = Modifier.size(200.dp)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Action Buttons (Right)
            ActionButtons(
                state = buttonsState,
                color = playerColor,
                modifier = Modifier.size(200.dp)
            )
        }
        
        // Shoulder buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            ShoulderButton(
                label = "L1",
                pressed = buttonsState.value.l,
                onPress = { buttonsState.value = buttonsState.value.copy(l = true) },
                onRelease = { buttonsState.value = buttonsState.value.copy(l = false) },
                color = playerColor
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            ShoulderButton(
                label = "R1",
                pressed = buttonsState.value.r,
                onPress = { buttonsState.value = buttonsState.value.copy(r = true) },
                onRelease = { buttonsState.value = buttonsState.value.copy(r = false) },
                color = playerColor
            )
        }
        
        // Start/Select
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            SystemButton(
                label = "SELECT",
                pressed = buttonsState.value.select,
                onPress = { buttonsState.value = buttonsState.value.copy(select = true) },
                onRelease = { buttonsState.value = buttonsState.value.copy(select = false) }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            SystemButton(
                label = "START",
                pressed = buttonsState.value.start,
                onPress = { buttonsState.value = buttonsState.value.copy(start = true) },
                onRelease = { buttonsState.value = buttonsState.value.copy(start = false) }
            )
        }
    }
}

@Composable
fun DPadZone(
    state: androidx.compose.runtime.MutableState<DPadState>,
    color: Color,
    modifier: androidx.compose.ui.Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF2C2C2C), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireView = true)
                        state.value = state.value.copy(
                            center = Offset(down.position.x, down.position.y),
                            current = Offset(down.position.x, down.position.y),
                            active = true
                        )
                        
                        while (true) {
                            val event = awaitPointerEvent()
                            val move = event.changes.firstOrNull { it.positionChanged() }
                            if (move != null) {
                                state.value = state.value.copy(
                                    current = Offset(move.position.x, move.position.y),
                                    active = true
                                )
                            }
                            if (event.changes.any { !it.pressed }) {
                                state.value = state.value.copy(active = false)
                                break
                            }
                        }
                    }
                }
            }
    ) {
        // D-Pad visual
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Up
            Box(
                modifier = Modifier
                    .size(60.dp, 30.dp)
                    .background(
                        color = if (state.value.direction in listOf(DPadDirection.UP, DPadDirection.UP_LEFT, DPadDirection.UP_RIGHT)) color.copy(alpha = 0.8f) else color.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                    )
            )
            
            Row(
                horizontalArrangement = Arrangement.Center
            ) {
                // Left
                Box(
                    modifier = Modifier
                        .size(30.dp, 60.dp)
                        .background(
                            color = if (state.value.direction in listOf(DPadDirection.LEFT, DPadDirection.UP_LEFT, DPadDirection.DOWN_LEFT)) color.copy(alpha = 0.8f) else color.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(start = 8.dp)
                        )
                )
                
                // Center
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(color = color.copy(alpha = 0.2f))
                )
                
                // Right
                Box(
                    modifier = Modifier
                        .size(30.dp, 60.dp)
                        .background(
                            color = if (state.value.direction in listOf(DPadDirection.RIGHT, DPadDirection.UP_RIGHT, DPadDirection.DOWN_RIGHT)) color.copy(alpha = 0.8f) else color.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(end = 8.dp)
                        )
                )
            }
            
            // Down
            Box(
                modifier = Modifier
                    .size(60.dp, 30.dp)
                    .background(
                        color = if (state.value.direction in listOf(DPadDirection.DOWN, DPadDirection.DOWN_LEFT, DPadDirection.DOWN_RIGHT)) color.copy(alpha = 0.8f) else color.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                    )
            )
        }
    }
}

@Composable
fun ActionButtons(
    state: androidx.compose.runtime.MutableState<ButtonsState>,
    color: Color,
    modifier: androidx.compose.ui.Modifier
) {
    // NeoGeo 4-button layout: A B C D (or A B X Y)
    Box(modifier = modifier) {
        // Button positions in a diamond
        val positions = listOf(
            Offset(100f, 40f),   // Y (top)
            Offset(140f, 80f),   // B (right)
            Offset(100f, 120f),  // A (bottom)
            Offset(60f, 80f)     // X (left)
        )
        
        val buttonNames = listOf("Y", "B", "A", "X")
        val buttonStates = listOf(
            state.value.y, state.value.b, state.value.a, state.value.x
        )
        val buttonColors = listOf(
            Color(0xFFFFEB3B), Color(0xFFF44336), Color(0xFF4CAF50), Color(0xFF2196F3)
        )
        
        positions.forEachIndexed { i, pos ->
            Box(
                modifier = Modifier
                    .offset(x = pos.x.dp, y = pos.y.dp)
                    .size(80.dp)
                    .background(
                        color = if (buttonStates[i]) buttonColors[i] else color.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(40.dp)
                    )
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireView = true)
                                // Set button pressed
                                when (i) {
                                    0 -> state.value = state.value.copy(y = true)
                                    1 -> state.value = state.value.copy(b = true)
                                    2 -> state.value = state.value.copy(a = true)
                                    3 -> state.value = state.value.copy(x = true)
                                }
                                
                                awaitPointerEvent { it.changes.any { !it.pressed } }
                                
                                // Release button
                                when (i) {
                                    0 -> state.value = state.value.copy(y = false)
                                    1 -> state.value = state.value.copy(b = false)
                                    2 -> state.value = state.value.copy(a = false)
                                    3 -> state.value = state.value.copy(x = false)
                                }
                            }
                        }
                    }
            ) {
                Text(
                    text = buttonNames[i],
                    fontSize = 20.sp,
                    color = Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ShoulderButton(
    label: String,
    pressed: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    color: Color
) {
    Box(
        modifier = Modifier
            .width(100.dp)
            .height(50.dp)
            .background(
                color = if (pressed) color.copy(alpha = 0.8f) else color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireView = true)
                        onPress()
                        awaitPointerEvent { it.changes.any { !it.pressed } }
                        onRelease()
                    }
                }
            }
            .align(Alignment.Center)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}

@Composable
fun SystemButton(
    label: String,
    pressed: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(80.dp)
            .height(40.dp)
            .background(
                color = if (pressed) Color(0xFF4CAF50) else Color(0xFF666666),
                shape = RoundedCornerShape(6.dp)
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireView = true)
                        onPress()
                        awaitPointerEvent { it.changes.any { !it.pressed } }
                        onRelease()
                    }
                }
            }
            .align(Alignment.Center)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}

fun buildButtonBitmask(state: ButtonsState): Int {
    var bits = 0
    if (state.a) bits = bits or (1 shl 8)   // A
    if (state.b) bits = bits or (1 shl 0)   // B
    if (state.x) bits = bits or (1 shl 9)   // X
    if (state.y) bits = bits or (1 shl 1)   // Y
    if (state.l) bits = bits or (1 shl 10)  // L
    if (state.r) bits = bits or (1 shl 11)  // R
    if (state.start) bits = bits or (1 shl 3) // Start
    if (state.select) bits = bits or (1 shl 2) // Select
    return bits
}

fun dPadX(state: DPadState): Short {
    return when (state.direction) {
        DPadDirection.RIGHT, DPadDirection.UP_RIGHT, DPadDirection.DOWN_RIGHT -> 32767
        DPadDirection.LEFT, DPadDirection.UP_LEFT, DPadDirection.DOWN_LEFT -> -32768
        else -> 0
    }
}

fun dPadY(state: DPadState): Short {
    return when (state.direction) {
        DPadDirection.UP, DPadDirection.UP_LEFT, DPadDirection.UP_RIGHT -> -32768
        DPadDirection.DOWN, DPadDirection.DOWN_LEFT, DPadDirection.DOWN_RIGHT -> 32767
        else -> 0
    }
}