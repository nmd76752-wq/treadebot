package com.tradebot.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var st: TextView
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        root.addView(TextView(this).apply { text = "ট্রেড স্ক্যানার বট"; textSize = 20f })
        st = TextView(this).apply { textSize = 16f; setPadding(0, 24, 0, 24) }
        root.addView(st)
        root.addView(Button(this).apply { text = "বট চালু করুন"; setOnClickListener { begin() } })
        root.addView(Button(this).apply {
            text = "রেজাল্ট খাতা রিসেট"
            setOnClickListener { getSharedPreferences("stats", MODE_PRIVATE).edit().clear().apply(); st.text = stat() }
        })
        setContentView(root)
    }
    override fun onResume() { super.onResume(); st.text = stat() }
    private fun stat(): String {
        val s = getSharedPreferences("stats", MODE_PRIVATE)
        val w = s.getInt("w", 0); val l = s.getInt("l", 0); val t = w + l
        return if (t == 0) "রেজাল্ট খাতা খালি" else "জিত $w | হার $l | মোট $t | সঠিক ${w * 100 / t}%"
    }
    private fun begin() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3)
        } else askCapture()
    }
    override fun onRequestPermissionsResult(req: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(req, p, r)
        askCapture()
    }
    private fun askCapture() {
        val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(m.createScreenCaptureIntent(), 2)
    }
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 2 && res == RESULT_OK && data != null) {
            startForegroundService(Intent(this, FloatingService::class.java).putExtra("code", res).putExtra("data", data))
            finish()
        }
    }
}
