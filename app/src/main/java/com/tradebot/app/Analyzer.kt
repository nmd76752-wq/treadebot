package com.tradebot.app

import android.graphics.Bitmap
import android.graphics.Color

object Analyzer {
    private class C(val o: Double, val c: Double, val hi: Double, val lo: Double) {
        val green get() = c > o
        val body get() = Math.abs(c - o)
        val up get() = hi - maxOf(o, c)
        val dn get() = minOf(o, c) - lo
    }

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
        val cs = ArrayList<C>()
        var x = 0
        while (x < w) {
            if (cnt[x] < 3) { x++; continue }
            var e = x
            while (e + 1 < w && cnt[e + 1] >= 3) e++
            if (e - x + 1 >= 3) {
                val st = (x..e).map { topY[it] }.sorted()
                val sb = (x..e).map { botY[it] }.sorted()
                val bt = st[st.size / 2].toDouble(); val bb = sb[sb.size / 2].toDouble()
                val g = (x..e).sumOf { col[it] } > 0
                val hi = -st.first().toDouble(); val lo = -sb.last().toDouble()
                cs.add(if (g) C(-bb, -bt, hi, lo) else C(-bt, -bb, hi, lo))
            }
            x = e + 1
        }
        if (cs.size < 15) return "👀 আমি কিছু দেখছি না — ট্রেডিং চার্ট ওপেন করুন"

        val v = cs.takeLast(60)
        val cl = v.map { it.c }
        val f = ema(cl, 5); val s = ema(cl, 13); val big = ema(cl, 30)
        val r = rsi(cl, 14)
        val price = cl.last()
        val win = v.takeLast(40)
        val top = win.maxOf { it.hi }; val bot = win.minOf { it.lo }
        val rng = maxOf(top - bot, 1.0)
        val nearRes = price > top - rng * 0.10
        val nearSup = price < bot + rng * 0.10

        val p = v[v.size - 2]; val q = v[v.size - 3]
        val bullEng = !q.green && p.green && p.c >= q.o && p.o <= q.c
        val bearEng = q.green && !p.green && p.c <= q.o && p.o >= q.c
        val hammer = p.body >= 1 && p.dn >= 2 * p.body && p.up <= p.body
        val star = p.body >= 1 && p.up >= 2 * p.body && p.dn <= p.body
        val bull = bullEng || hammer
        val bear = bearEng || star

        val upC = listOf(f > s, price > big, r in 40.0..68.0, !nearRes, p.green || bull)
        val dnC = listOf(f < s, price < big, r in 32.0..60.0, !nearSup, !p.green || bear)
        val us = upC.count { it }; val ds = dnC.count { it }
        val revUp = nearSup && bull && r < 45
        val revDn = nearRes && bear && r > 55

        val sig = when {
            revUp -> "⬆️ UP (কল) — সাপোর্টে রিভার্সাল"
            revDn -> "⬇️ DOWN (পুট) — রেজিস্ট্যান্সে রিভার্সাল"
            us == 5 -> "⬆️ UP (কল)"
            ds == 5 -> "⬇️ DOWN (পুট)"
            else -> "⏸️ WAIT (শর্ত মেলেনি: UP $us/5, DOWN $ds/5)"
        }
        val zone = if (nearSup) "সাপোর্টের কাছে" else if (nearRes) "রেজিস্ট্যান্সের কাছে" else "মাঝখানে"
        val pat = listOfNotNull(
            if (bullEng) "বুলিশ এনগালফিং" else null, if (bearEng) "বেয়ারিশ এনগালফিং" else null,
            if (hammer) "হ্যামার" else null, if (star) "শুটিং স্টার" else null
        )
        return "$sig\nRSI: ${r.toInt()} | দাম: $zone\nপ্যাটার্ন: ${if (pat.isEmpty()) "নেই" else pat.joinToString(", ")}\nক্যান্ডেল পড়া: ${cs.size}টি"
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
