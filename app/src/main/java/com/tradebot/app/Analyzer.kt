package com.tradebot.app

import android.graphics.Bitmap
import android.graphics.Color

object Analyzer {
    private fun kind(c: Int): Int {
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        if (g > 140 && g > r + 60 && g > b + 40) return 1
        if (r > 190 && r > g + 90 && r > b + 70) return -1
        return 0
    }

    fun analyze(src: Bitmap): String {
        val w = src.width; val h = src.height
        val y0 = (h * 0.15).toInt(); val y1 = (h * 0.75).toInt()
        val px = IntArray(w)
        val topY = IntArray(w) { -1 }; val botY = IntArray(w) { -1 }
        val col = IntArray(w); val cnt = IntArray(w)
        for (y in y0 until y1) {
            src.getPixels(px, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val k = kind(px[x]); if (k == 0) continue
                if (topY[x] < 0) topY[x] = y
                botY[x] = y; col[x] += k; cnt[x]++
            }
        }
        val closes = ArrayList<Double>(); val dirs = ArrayList<Int>()
        var x = 0
        while (x < w) {
            if (cnt[x] < 3) { x++; continue }
            var e = x
            while (e + 1 < w && cnt[e + 1] >= 3) e++
            if (e - x + 1 >= 3) {
                val tops = (x..e).map { topY[it] }.sorted()
                val bots = (x..e).map { botY[it] }.sorted()
                val d = if ((x..e).sumOf { col[it] } > 0) 1 else -1
                closes.add(-(if (d > 0) tops[tops.size / 2] else bots[bots.size / 2]).toDouble())
                dirs.add(d)
            }
            x = e + 1
        }
        if (closes.size < 15) return "👀 আমি কিছু দেখছি না — ট্রেডিং চার্ট ওপেন করুন"
        val c = closes.takeLast(60)
        val fast = ema(c, 5); val slow = ema(c, 13); val rsi = rsi(c, 14)
        val up = fast > slow
        val sig = when {
            up && rsi < 70 -> "⬆️ UP (কল)"
            !up && rsi > 30 -> "⬇️ DOWN (পুট)"
            else -> "⏸️ WAIT (পরিষ্কার সিগনাল নেই)"
        }
        return "$sig\nRSI: ${rsi.toInt()} | ট্রেন্ড: ${if (up) "ঊর্ধ্বমুখী" else "নিম্নমুখী"}\nক্যান্ডেল পড়া হয়েছে: ${closes.size}টি"
    }

    private fun ema(v: List<Double>, n: Int): Double {
        val k = 2.0 / (n + 1); var e = v[0]
        for (i in 1 until v.size) e = v[i] * k + e * (1 - k)
        return e
    }

    private fun rsi(v: List<Double>, n: Int): Double {
        var gain = 0.0; var loss = 0.0
        for (i in v.size - n until v.size) { val d = v[i] - v[i - 1]; if (d > 0) gain += d else loss -= d }
        return if (loss == 0.0) 100.0 else 100 - 100 / (1 + gain / loss)
    }
}
