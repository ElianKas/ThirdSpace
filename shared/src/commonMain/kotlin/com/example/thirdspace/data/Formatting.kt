package com.example.thirdspace.data

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure, deterministic score formatting used across platforms.
 *
 * Formats a score like the legacy app: 1340 -> "1,3k". This is Compose-free
 * logic that lives in the data package so it can be reused by any UI layer
 * (native SwiftUI, a future Android UI) and exercised by commonTest.
 */
fun formatScore(n: Int): String {
    val a = abs(n)
    if (a < 1000) return n.toString()
    val k = n / 1000.0
    val rounded = if (abs(k) < 10) (k * 10).roundToInt() / 10.0 else k.roundToInt().toDouble()
    val text = if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    return text.replace(".", ",") + "k"
}
