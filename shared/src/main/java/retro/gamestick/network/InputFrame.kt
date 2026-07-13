package retro.gamestick.network

import kotlinx.serialization.Serializable

@Serializable
data class NetworkInputFrame(
    val playerSlot: Int = 0,
    val frameId: Long = 0,
    val timestampUs: Long = 0,
    val state: NetworkInputState = NetworkInputState()
)

@Serializable
data class NetworkInputState(
    val buttons: Int = 0,
    val lx: Short = 0,
    val ly: Short = 0,
    val rx: Short = 0,
    val ry: Short = 0
)