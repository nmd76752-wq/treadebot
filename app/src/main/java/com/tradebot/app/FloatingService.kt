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
    private lateinit var wm: WindowManager
    private lateinit var box: LinearLayout
    private lateinit var out: TextView
    private var mp: MediaProjection? = null
    private var ir: ImageReader? = null
    private var vd: VirtualDisplay? = null
    private val h = Handler(Looper.getMainLooper())
    @Volatile private var want: ((Bitmap) -> Unit)? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("bot", "Bot", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "bot").setContentTitle("Trade Bot চলছে").setSmallIcon(android.R.drawable.ic_dialog_info).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val code = i?.getIntExtra("code", 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) i?.getParcelableExtra("data", Intent::class.java)
            else @Suppress("DEPRECATION") i?.getParcelableExtra("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mp = m.getMediaProjection(code, data)
        mp?.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, h)
        startCapture()
        showBubble()
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

    private fun scan() {
        box.visibility = View.INVISIBLE
        h.postDelayed({
            want = { bmp ->
                Thread {
                    val r = Analyzer.analyze(bmp)
                    h.post { out.text = r; box.visibility = View.VISIBLE }
                }.start()
            }
            h.postDelayed({
                if (want != null) { want = null; out.text = "❌ ক্যাপচার হয়নি, আবার চেষ্টা করুন"; box.visibility = View.VISIBLE }
            }, 3000)
        }, 300)
    }

    private fun showBubble() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(225, 20, 20, 30))
            setPadding(24, 24, 24, 24)
        }
        out = TextView(this).apply { text = "🤖 প্রস্তুত (এখানে ধরে টানুন)"; setTextColor(Color.WHITE); textSize = 15f }
        val row = LinearLayout(this)
        row.addView(Button(this).apply { text = "স্ক্যান"; setOnClickListener { scan() } })
        row.addView(Button(this).apply { text = "বন্ধ"; setOnClickListener { stopSelf() } })
        box.addView(out); box.addView(row)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.START; p.x = 20; p.y = 200
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
        out.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY }
                MotionEvent.ACTION_MOVE -> { p.x = sx + (e.rawX - tx).toInt(); p.y = sy + (e.rawY - ty).toInt(); wm.updateViewLayout(box, p) }
            }
            true
        }
        wm.addView(box, p)
    }

    override fun onDestroy() {
        try { wm.removeView(box) } catch (_: Exception) {}
        vd?.release(); ir?.close(); mp?.stop()
        super.onDestroy()
    }
}
