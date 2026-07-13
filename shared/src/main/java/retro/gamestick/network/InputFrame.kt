package retro.gamestick.network

data class InputFrame(
    val playerSlot: Int = 0,
    val frameId: Long = 0,
    val timestampUs: Long = 0,
    val state: InputState = InputState()
)

data class InputState(
    val buttons: Int = 0,
    val lx: Short = 0,
    val ly: Short = 0,
    val rx: Short = 0,
    val ry: Short = 0
)