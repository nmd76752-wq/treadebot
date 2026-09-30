package com.tradebot.app

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.*

class FloatingService : Service() {
    private var wm: WindowManager? = null
    private var box: LinearLayout? = null
    private var out: TextView? = null
    private var mp: MediaProjection? = null
    private var ir: ImageReader? = null
    private var vd: VirtualDisplay? = null
    private var notif = false
    private var nm: NotificationManager? = null
    private val h = Handler(Looper.getMainLooper())
    @Volatile private var want: ((Bitmap) -> Unit)? = null

    override fun onBind(i: Intent?): IBinder? = null

    private fun pi(a: String) = PendingIntent.getService(this, a.hashCode(),
        Intent(this, FloatingService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        when (i?.action) {
            "SCAN" -> { scan(); return START_NOT_STICKY }
            "STOP" -> { stopSelf(); return START_NOT_STICKY }
        }
        val n0 = getSystemService(NotificationManager::class.java)
        nm = n0
        n0.createNotificationChannel(NotificationChannel("bot", "Bot", NotificationManager.IMPORTANCE_LOW))
        n0.createNotificationChannel(NotificationChannel("res", "Signal", NotificationManager.IMPORTANCE_HIGH))
        notif = i?.getBooleanExtra("notif", false) ?: false
        val b = Notification.Builder(this, "bot").setContentTitle("Trade Bot চালু")
            .setContentText(if (notif) "Quotex চার্টে গিয়ে স্ক্যান চাপুন" else "ভাসমান বট চলছে")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true)
        if (notif) {
            b.addAction(Notification.Action.Builder(android.R.drawable.ic_media_play, "স্ক্যান", pi("SCAN")).build())
            b.addAction(Notification.Action.Builder(android.R.drawable.ic_delete, "বন্ধ", pi("STOP")).build())
        }
        startForeground(1, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val code = i?.getIntExtra("code", 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) i?.getParcelableExtra("data", Intent::class.java)
            else @Suppress("DEPRECATION") i?.getParcelableExtra("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mp = m.getMediaProjection(code, data)
        mp?.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, h)
        startCapture()
        if (!notif) showBubble()
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val dm = resources.displayMetrics
        val w = dm.widthPixels; val hh = dm.heightPixels
        val reader = ImageReader.newInstance(w, hh, PixelFormat.RGBA_8888, 2)
        ir = reader
        vd = mp?.createVirtualDisplay("cap", w, hh, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, h)
        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            val cb = want
            if (cb != null) {
                want = null
                val pl = img.planes[0]
                val pad = pl.rowStride - pl.pixelStride * w
                val bmp = Bitmap.createBitmap(w + pad / pl.pixelStride, hh, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(pl.buffer)
                cb(bmp)
            }
            img.close()
        }, h)
    }

    private fun show(t: String) {
        if (notif) {
            val n = Notification.Builder(this, "res").setContentTitle(t.lines().first())
                .setContentText(t.lines().drop(1).joinToString(" | "))
                .setStyle(Notification.BigTextStyle().bigText(t))
                .setSmallIcon(android.R.drawable.ic_dialog_info).setTimeoutAfter(30000).build()
            nm?.notify(2, n)
        } else {
            out?.text = t; box?.visibility = View.VISIBLE
        }
    }

    private fun scan() {
        if (!notif) box?.visibility = View.INVISIBLE
        h.postDelayed({
            want = { bmp ->
                Thread {
                    val r = Analyzer.analyze(bmp)
                    h.post { show(r) }
                }.start()
            }
            h.postDelayed({ if (want != null) { want = null; show("❌ ক্যাপচার হয়নি, আবার চেষ্টা করুন") } }, 3000)
        }, if (notif) 900L else 300L)
    }

    private fun showBubble() {
        val w0 = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = w0
        val bx = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(225, 20, 20, 30))
            setPadding(24, 24, 24, 24)
        }
        box = bx
        val tv = TextView(this).apply { text = "🤖 প্রস্তুত (এখানে ধরে টানুন)"; setTextColor(Color.WHITE); textSize = 15f }
        out = tv
        val row = LinearLayout(this)
        row.addView(Button(this).apply { text = "স্ক্যান"; setOnClickListener { scan() } })
        row.addView(Button(this).apply { text = "বন্ধ"; setOnClickListener { stopSelf() } })
        bx.addView(tv); bx.addView(row)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.START; p.x = 20; p.y = 200
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY }
                MotionEvent.ACTION_MOVE -> { p.x = sx + (e.rawX - tx).toInt(); p.y = sy + (e.rawY - ty).toInt(); w0.updateViewLayout(bx, p) }
            }
            true
        }
        w0.addView(bx, p)
    }

    override fun onDestroy() {
        try { box?.let { wm?.removeView(it) } } catch (_: Exception) {}
        vd?.release(); ir?.close(); mp?.stop()
        super.onDestroy()
    }
}
