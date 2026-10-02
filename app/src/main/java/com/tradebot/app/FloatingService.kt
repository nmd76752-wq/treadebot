package com.tradebot.app

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import java.util.Locale

class FloatingService : Service() {
    private var mp: MediaProjection? = null
    private var ir: ImageReader? = null
    private var vd: VirtualDisplay? = null
    private var last: Image? = null
    private var tf = 1
    private var nm: NotificationManager? = null
    private val h = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    private fun pi(a: String) = PendingIntent.getService(this, a.hashCode(),
        Intent(this, FloatingService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun act(icon: Int, title: String, a: String) = Notification.Action.Builder(icon, title, pi(a)).build()

    private fun sp() = getSharedPreferences("stats", MODE_PRIVATE)

    private fun statText(): String {
        val w = sp().getInt("w", 0); val l = sp().getInt("l", 0); val t = w + l
        return if (t == 0) "খাতা খালি" else "জিত $w | হার $l | সঠিক ${w * 100 / t}%"
    }

    private val maxS = 2
    private fun rp() = getSharedPreferences("risk", MODE_PRIVATE)
    private fun balNow(): Double = rp().getString("bal", "100")?.toDoubleOrNull() ?: 100.0
    private fun amtNow(): Double = rp().getString("amt", "1")?.toDoubleOrNull() ?: 1.0
    private fun payNow(): Double = rp().getString("pay", "90")?.toDoubleOrNull() ?: 90.0
    private fun stakeNow(): Double = minOf(amtNow() * Math.pow(2.0, sp().getInt("streak", 0).toDouble()), balNow())

    private fun riskText(): String {
        val st = sp().getInt("streak", 0)
        val s = stakeNow()
        return String.format(Locale.US, "🔁 মার্টিনগেল ধাপ: %d/%d\nস্টেক: \$%.2f | জিতলে +\$%.2f | হারলে −\$%.2f\n💰 ব্যালেন্স: \$%.2f", st + 1, maxS + 1, s, s * payNow() / 100, s, balNow())
    }

    private fun cycle() {
        val l = listOf(1, 2, 3, 5, 15)
        tf = l[(l.indexOf(tf) + 1) % l.size]
    }

    private fun note(): Notification =
        Notification.Builder(this, "bot").setContentTitle("Trade Bot চালু — সময়: $tf মিনিট")
            .setContentText(statText())
            .setSmallIcon(R.drawable.ic_bot).setOngoing(true)
            .addAction(act(android.R.drawable.ic_media_play, "স্ক্যান", "SCAN"))
            .addAction(act(android.R.drawable.ic_menu_recent_history, "সময়: ${tf}মি", "TF"))
            .addAction(act(android.R.drawable.ic_delete, "বন্ধ", "STOP")).build()

    private fun record(win: Boolean) {
        val k = if (win) "w" else "l"
        val stake0 = stakeNow()
        val nb = if (win) balNow() + stake0 * payNow() / 100 else balNow() - stake0
        rp().edit().putString("bal", String.format(Locale.US, "%.2f", maxOf(nb, 0.0))).apply()
        val ns = if (win) 0 else sp().getInt("streak", 0) + 1
        sp().edit().putInt("streak", if (ns > maxS) 0 else ns).apply()
        sp().edit().putInt(k, sp().getInt(k, 0) + 1).apply()
        nm?.cancel(2)
        nm?.notify(1, note())
        nm?.notify(3, Notification.Builder(this, "res")
            .setContentTitle(if (win) "✅ জিত সেভ হলো" else "❌ হার সেভ হলো")
            .setContentText(statText()).setSmallIcon(R.drawable.ic_bot).setTimeoutAfter(8000).build())
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        when (i?.action) {
            "SCAN" -> { scan(); return START_NOT_STICKY }
            "STOP" -> { stopSelf(); return START_NOT_STICKY }
            "TF" -> { cycle(); nm?.notify(1, note()); return START_NOT_STICKY }
            "WIN" -> { record(true); return START_NOT_STICKY }
            "LOSS" -> { record(false); return START_NOT_STICKY }
            "SKIP" -> { nm?.cancel(2); return START_NOT_STICKY }
        }
        val n0 = getSystemService(NotificationManager::class.java)
        nm = n0
        n0.createNotificationChannel(NotificationChannel("bot", "Bot", NotificationManager.IMPORTANCE_LOW))
        n0.createNotificationChannel(NotificationChannel("res", "Signal", NotificationManager.IMPORTANCE_HIGH))
        startForeground(1, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val code = i?.getIntExtra("code", 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) i?.getParcelableExtra("data", Intent::class.java)
            else @Suppress("DEPRECATION") i?.getParcelableExtra("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mp = m.getMediaProjection(code, data)
        mp?.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, h)
        startCapture()
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val dm = resources.displayMetrics
        val w = dm.widthPixels; val hh = dm.heightPixels
        val reader = ImageReader.newInstance(w, hh, PixelFormat.RGBA_8888, 3)
        ir = reader
        vd = mp?.createVirtualDisplay("cap", w, hh, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, h)
        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            last?.close()
            last = img
        }, h)
    }

    private fun show(t: String, track: Boolean) {
        val body = if (track) t + "\n" + riskText() else t
        val b = Notification.Builder(this, "res").setContentTitle(body.lines().first())
            .setContentText(body.lines().drop(1).joinToString(" | "))
            .setStyle(Notification.BigTextStyle().bigText(if (track) body + "\n\nট্রেড শেষে ফলাফল চাপুন\n" + statText() else body))
            .setSmallIcon(R.drawable.ic_bot)
        if (track) {
            b.addAction(act(android.R.drawable.ic_input_add, "✅ জিতলাম", "WIN"))
            b.addAction(act(android.R.drawable.ic_delete, "❌ হারলাম", "LOSS"))
            b.addAction(act(android.R.drawable.ic_menu_close_clear_cancel, "বাদ", "SKIP"))
        } else b.setTimeoutAfter(30000)
        nm?.notify(2, b.build())
    }

    private fun entryText(): String {
        val c = java.util.Calendar.getInstance()
        val mins = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
        val secs = c.get(java.util.Calendar.SECOND)
        var nxt = (mins / tf + 1) * tf
        var left = (nxt - mins) * 60 - secs
        if (left < 10) { nxt += tf; left += tf * 60 }
        val hh = (nxt / 60) % 24; val mm = nxt % 60
        return String.format(Locale.US, "⏰ এন্ট্রি টাইম: %02d:%02d (বাকি %d মিনিট %d সেকেন্ড)", hh, mm, left / 60, left % 60)
    }

    private fun scan() {
        h.postDelayed({
            val img = last
            if (img == null) { show("❌ ক্যাপচার হয়নি, আবার চেষ্টা করুন", false); return@postDelayed }
            try {
                val pl = img.planes[0]
                val pad = pl.rowStride - pl.pixelStride * img.width
                val bmp = Bitmap.createBitmap(img.width + pad / pl.pixelStride, img.height, Bitmap.Config.ARGB_8888)
                pl.buffer.rewind()
                bmp.copyPixelsFromBuffer(pl.buffer)
                Thread {
                    val ls = Analyzer.analyze(bmp).lines()
                    val sig = ls[0].startsWith("⬆") || ls[0].startsWith("⬇")
                    val r = if (sig) (listOf("${ls[0]} — $tf মিনিটের জন্য") + ls.drop(1)).joinToString("\n") else ls.joinToString("\n")
                    h.post { show(if (sig) r.replaceFirst("\n", "\n" + entryText() + "\n") else r, sig) }
                }.start()
            } catch (e: Exception) {
                show("❌ ছবি পড়া যায়নি, আবার চেষ্টা করুন", false)
            }
        }, 900L)
    }

    override fun onDestroy() {
        last?.close(); vd?.release(); ir?.close(); mp?.stop()
        super.onDestroy()
    }
}
