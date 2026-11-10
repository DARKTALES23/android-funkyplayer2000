package com.funkyplayer2000

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Keeps the last samples that went to the speakers so the visualizer can draw them.
 * The player thread writes (see TapProcessor); the screen reads. No microphone permission needed.
 */
object AudioTap {
    const val N = 2048

    private val buf = FloatArray(N)
    private var pos = 0
    private val lock = Any()
    private val window = FloatArray(N) { (0.5 - 0.5 * cos(2.0 * PI * it / (N - 1))).toFloat() }

    fun feed(samples: java.nio.ShortBuffer, channels: Int) {
        if (channels <= 0) return
        val frames = samples.remaining() / channels
        synchronized(lock) {
            for (f in 0 until frames) {
                var s = 0
                for (c in 0 until channels) s += samples.get(f * channels + c)
                buf[pos] = s / (channels * 32768f)
                pos = (pos + 1) % N
            }
        }
    }

    /** The last N samples, oldest first. */
    fun snapshot(): FloatArray = synchronized(lock) {
        val out = FloatArray(N)
        System.arraycopy(buf, pos, out, 0, N - pos)
        System.arraycopy(buf, 0, out, N - pos, pos)
        out
    }

    private fun edge(i: Int, bands: Int): Int = (2.0 * 350.0.pow(i.toDouble() / bands)).toInt()

    /** Spectrum in `bands` logarithmic bands, each 0..1 (same scale as the desktop player). */
    fun spectrum(bands: Int): FloatArray {
        val x = snapshot()
        val re = FloatArray(N) { x[it] * window[it] }
        val im = FloatArray(N)
        fft(re, im)
        val half = N / 2
        val out = FloatArray(bands)
        for (b in 0 until bands) {
            val a = edge(b, bands)
            val c = max(edge(b + 1, bands), a + 1)
            var m = 0f
            for (k in a until min(c, half)) m = max(m, hypot(re[k], im[k]))
            val mag = m / (N / 4f)
            val db = 20f * log10(mag + 1e-6f)
            out[b] = ((db + 70f) / 70f).coerceIn(0f, 1f)
        }
        return out
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
                val h = len / 2
                for (k in 0 until h) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + h] * cr - im[i + k + h] * ci
                    val vi = re[i + k + h] * ci + im[i + k + h] * cr
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + h] = ur - vr
                    im[i + k + h] = ui - vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
