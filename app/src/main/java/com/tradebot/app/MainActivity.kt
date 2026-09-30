package com.tradebot.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private var notif = false
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        root.addView(TextView(this).apply { text = "ট্রেড স্ক্যানার বট\n\nভাসমান বাক্স বন্ধ থাকলে নোটিফিকেশন মোড ব্যবহার করুন।"; textSize = 18f })
        root.addView(Button(this).apply { text = "নোটিফিকেশন মোড"; setOnClickListener { notif = true; begin() } })
        root.addView(Button(this).apply { text = "ভাসমান বট (ওভারলে লাগবে)"; setOnClickListener { notif = false; begin() } })
        setContentView(root)
    }
    private fun begin() {
        if (notif) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3)
            } else askCapture()
        } else if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 1)
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
        if (req == 1 && Settings.canDrawOverlays(this)) askCapture()
        if (req == 2 && res == RESULT_OK && data != null) {
            startForegroundService(Intent(this, FloatingService::class.java).putExtra("code", res).putExtra("data", data).putExtra("notif", notif))
            finish()
        }
    }
}
