package gr.misha.xypna

import android.content.Context
import android.graphics.Color

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

object Palette {
    val ACCENT = Color.parseColor("#FF8A3D")
    val BG = Color.parseColor("#121214")
    val CARD = Color.parseColor("#1E1E22")
    val TEXT = Color.WHITE
    val MUTED = Color.parseColor("#9A9AA3")
    val DIM = Color.parseColor("#5A5A62")
    val RED = Color.parseColor("#FF5A5A")
    val GREEN = Color.parseColor("#4CD08A")
    val WARN = Color.parseColor("#3A2A14")
}
