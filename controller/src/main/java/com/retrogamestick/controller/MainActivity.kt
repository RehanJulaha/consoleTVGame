package com.retrogamestick.controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
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
import retrofit.gamestick.network.InputFrame

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
        supervisorJob.cancel()
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
    androidx.compose.runtime.LaunchedEffect(isConnected) {
        if (isConnected) {
            while (isConnected) {
                val frame = InputFrame(
                    playerSlot = myPlayerSlot,
                    frameId = 0,
                    timestampUs = System.currentTimeMillis() * 1000,
                    state = retrofit.gamestick.network.InputState(
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
    
    androidx.compose.material3.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
        androidx.compose.material3.Surface(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            color = androidx.compose.material3.MaterialTheme.colorScheme.background
        ) {}
        
        if (!isConnected) {
            // Connecting screen
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.Text(
                    text = "Searching for RetroGameStick...",
                    fontSize = 24.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
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
    
    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        // Player indicator
        androidx.compose.material3.Text(
            text = "PLAYER ${myPlayerSlot + 1}",
            fontSize = 24.sp,
            color = playerColor,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        
        // Main gamepad area
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(32.dp)
        ) {
            // D-Pad (Left)
            DPadZone(
                state = dPadState,
                color = playerColor,
                modifier = androidx.compose.ui.Modifier.size(200.dp)
            )
            
            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.weight(1f))
            
            // Action Buttons (Right)
            ActionButtons(
                state = buttonsState,
                color = playerColor,
                modifier = androidx.compose.ui.Modifier.size(200.dp)
            )
        }
        
        // Shoulder buttons
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(32.dp)
        ) {
            ShoulderButton(
                label = "L1",
                pressed = buttonsState.value.l,
                onPress = { buttonsState.value = buttonsState.value.copy(l = true) },
                onRelease = { buttonsState.value = buttonsState.value.copy(l = false) },
                color = playerColor
            )
            
            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.weight(1f))
            
            ShoulderButton(
                label = "R1",
                pressed = buttonsState.value.r,
                onPress = { buttonsState.value = buttonsState.value.copy(r = true) },
                onRelease = { buttonsState.value = buttonsState.value.copy(r = false) },
                color = playerColor
            )
        }
        
        // Start/Select
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(32.dp)
        ) {
            SystemButton(
                label = "SELECT",
                pressed = buttonsState.value.select,
                onPress = { buttonsState.value = buttonsState.value.copy(select = true) },
                onRelease = { buttonsState.value = buttonsState.value.copy(select = false) }
            )
            
            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.weight(1f))
            
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
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .background(Color(0xFF2C2C2C), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
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
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            // Up
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .size(60.dp, 30.dp)
                    .background(
                        color = if (state.value.direction in listOf(DPadDirection.UP, DPadDirection.UP_LEFT, DPadDirection.UP_RIGHT)) color.copy(alpha = 0.8f) else color.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                    )
            )
            
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                // Left
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .size(30.dp, 60.dp)
                        .background(
                            color = if (state.value.direction in listOf(DPadDirection.LEFT, DPadDirection.UP_LEFT, DPadDirection.DOWN_LEFT)) color.copy(alpha = 0.8f) else color.copy(alpha = 0.3f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(start = 8.dp)
                        )
                )
                
                // Center
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .size(60.dp)
                        .background(color = color.copy(alpha = 0.2f))
                )
                
                // Right
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .size(30.dp, 60.dp)
                        .background(
                            color = if (state.value.direction in listOf(DPadDirection.RIGHT, DPadDirection.UP_RIGHT, DPadDirection.DOWN_RIGHT)) color.copy(alpha = 0.8f) else color.copy(alpha = 0.3f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(end = 8.dp)
                        )
                )
            }
            
            // Down
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .size(60.dp, 30.dp)
                    .background(
                        color = if (state.value.direction in listOf(DPadDirection.DOWN, DPadDirection.DOWN_LEFT, DPadDirection.DOWN_RIGHT)) color.copy(alpha = 0.8f) else color.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
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
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        val positions = listOf(
            androidx.compose.ui.geometry.Offset(100f, 40f),   // Y (top)
            androidx.compose.ui.geometry.Offset(140f, 80f),   // B (right)
            androidx.compose.ui.geometry.Offset(100f, 120f),  // A (bottom)
            androidx.compose.ui.geometry.Offset(60f, 80f)     // X (left)
        )
        
        val buttonNames = listOf("Y", "B", "A", "X")
        val buttonStates = listOf(
            state.value.y, state.value.b, state.value.a, state.value.x
        )
        val buttonColors = listOf(
            Color(0xFFFFEB3B), Color(0xFFF44336), Color(0xFF4CAF50), Color(0xFF2196F3)
        )
        
        positions.forEachIndexed { i, pos ->
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .offset(x = pos.x.dp, y = pos.y.dp)
                    .size(80.dp)
                    .background(
                        color = if (buttonStates[i]) buttonColors[i] else color.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(40.dp)
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
                androidx.compose.material3.Text(
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
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .width(100.dp)
            .height(50.dp)
            .background(
                color = if (pressed) color.copy(alpha = 0.8f) else color.copy(alpha = 0.3f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
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
        androidx.compose.material3.Text(
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
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .width(80.dp)
            .height(40.dp)
            .background(
                color = if (pressed) Color(0xFF4CAF50) else Color(0xFF666666),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
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
        androidx.compose.material3.Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}

data class DPadState(
    var center: Offset = Offset.Zero,
    var current: Offset = Offset.Zero,
    var active: Boolean = false
) {
    val direction: DPadDirection
        get() {
            val dx = current.x - center.x
            val dy = current.y - center.y
            val dist = Math.hypot(dx.toDouble(), dy.toDouble())
            
            if (dist < 20.0) return DPadDirection.NONE
            
            val angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble()))
            return when {
                angle >= -22.5 && angle < 22.5 -> DPadDirection.RIGHT
                angle >= 22.5 && angle < 67.5 -> DPadDirection.UP_RIGHT
                angle >= 67.5 && angle < 112.5 -> DPadDirection.UP
                angle >= 112.5 && angle < 157.5 -> DPadDirection.UP_LEFT
                angle >= 157.5 || angle < -157.5 -> DPadDirection.LEFT
                angle >= -157.5 && angle < -112.5 -> DPadDirection.DOWN_LEFT
                angle >= -112.5 && angle < -67.5 -> DPadDirection.DOWN
                angle >= -67.5 && angle < -22.5 -> DPadDirection.DOWN_RIGHT
                else -> DPadDirection.NONE
            }
        }
}

enum class DPadDirection {
    NONE, UP, DOWN, LEFT, RIGHT, UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT
}

data class ButtonsState(
    var a: Boolean = false,
    var b: Boolean = false,
    var x: Boolean = false,
    var y: Boolean = false,
    var l: Boolean = false,
    var r: Boolean = false,
    var start: Boolean = false,
    var select: Boolean = false
)

@Composable
fun ActionButtons(
    state: androidx.compose.runtime.MutableState<ButtonsState>,
    color: Color,
    modifier: androidx.compose.ui.Modifier
) {
    // NeoGeo 4-button layout: A B C D (or A B X Y)
    androidx.compose.foundation.layout.Box(modifier = modifier) {
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
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .offset(x = pos.x.dp, y = pos.y.dp)
                    .size(80.dp)
                    .background(
                        color = if (buttonStates[i]) buttonColors[i] else color.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(40.dp)
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
                androidx.compose.material3.Text(
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
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .width(100.dp)
            .height(50.dp)
            .background(
                color = if (pressed) color.copy(alpha = 0.8f) else color.copy(alpha = 0.3f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
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
        androidx.compose.material3.Text(
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
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .width(80.dp)
            .height(40.dp)
            .background(
                color = if (pressed) Color(0xFF4CAF50) else Color(0xFF666666),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
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
        androidx.compose.material3.Text(
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