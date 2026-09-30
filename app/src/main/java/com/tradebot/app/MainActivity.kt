package com.tradebot.app

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        root.addView(TextView(this).apply { text = "ট্রেড স্ক্যানার বট\n\nশুরু চাপুন, দুইটা অনুমতি দিন, তারপর Quotex খুলুন।"; textSize = 18f })
        root.addView(Button(this).apply { text = "শুরু করুন"; setOnClickListener { start() } })
        setContentView(root)
    }
    private fun start() {
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 1)
        } else askCapture()
    }
    private fun askCapture() {
        val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(m.createScreenCaptureIntent(), 2)
    }
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 1 && Settings.canDrawOverlays(this)) askCapture()
        if (req == 2 && res == RESULT_OK && data != null) {
            startForegroundService(Intent(this, FloatingService::class.java).putExtra("code", res).putExtra("data", data))
            finish()
        }
    }
}
