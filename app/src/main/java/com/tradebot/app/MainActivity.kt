package com.tradebot.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private val BG = Color.parseColor("#0B1220")
    private val CARD = Color.parseColor("#E6131D2E")
    private val LINE = Color.parseColor("#22304A")
    private val TXT = Color.parseColor("#E6EDF7")
    private val MUTE = Color.parseColor("#8696AD")
    private val ACC = Color.parseColor("#2F6BFF")
    private val GRN = Color.parseColor("#22C55E")
    private val RED = Color.parseColor("#EF4444")

    private lateinit var accV: TextView
    private lateinit var cntV: TextView
    private lateinit var noteV: TextView
    private lateinit var stakeV: TextView
    private lateinit var detV: TextView
    private lateinit var eb: EditText
    private lateinit var ep: EditText
    private lateinit var er: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun box(fill: Int, r: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(r).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun tv(t: String, sp: Float, c: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sp; setTextColor(c)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = box(CARD, 16, LINE)
        setPadding(dp(18), dp(16), dp(18), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
    }

    private fun field(label: String, key: String, def: String): Pair<LinearLayout, EditText> {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }
        }
        col.addView(tv(label, 12f, MUTE))
        val e = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(getSharedPreferences("risk", MODE_PRIVATE).getString(key, def))
            setTextColor(TXT); textSize = 17f
            background = box(BG, 10, LINE)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setSelectAllOnFocus(true)
        }
        col.addView(e)
        return Pair(col, e)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(40), dp(18), dp(28))
        }
        val hd = LinearLayout(this).apply { gravity = 16 }
        val lid = resources.getIdentifier("ic_launcher", "mipmap", packageName)
        if (lid != 0) hd.addView(ImageView(this).apply {
            setImageResource(lid)
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(12) }
        })
        hd.addView(tv("Trade Scanner", 26f, TXT, true))
        root.addView(hd)
        root.addView(tv("চার্ট স্ক্যান করুন, সিগনাল দেখুন, ঝুঁকি মাপুন", 13f, MUTE).apply { setPadding(0, dp(6), 0, dp(18)) })

        val sc = card()
        sc.addView(tv("রেজাল্ট খাতা", 13f, MUTE))
        accV = tv("—", 40f, TXT, true); sc.addView(accV)
        cntV = tv("", 14f, TXT); sc.addView(cntV)
        noteV = tv("", 12f, MUTE).apply { setPadding(0, dp(6), 0, 0) }; sc.addView(noteV)
        root.addView(sc)

        root.addView(Button(this).apply {
            text = "বট চালু করুন"; setTextColor(Color.WHITE); textSize = 16f; isAllCaps = false
            background = box(ACC, 14); stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(14) }
            setOnClickListener { begin() }
        })

        val rc = card()
        rc.addView(tv("রিস্ক ক্যালকুলেটর", 13f, MUTE).apply { setPadding(0, 0, 0, dp(10)) })
        val row = LinearLayout(this)
        val f1 = field("ব্যালেন্স (\$)", "bal", "100")
        val f2 = field("পেআউট (%)", "pay", "90")
        val f3 = field("ঝুঁকি (%)", "risk", "2")
        eb = f1.second; ep = f2.second; er = f3.second
        row.addView(f1.first); row.addView(f2.first); row.addView(f3.first)
        rc.addView(row)
        stakeV = tv("", 30f, TXT, true).apply { setPadding(0, dp(14), 0, 0) }
        detV = tv("", 14f, MUTE).apply { setPadding(0, dp(4), 0, 0); setLineSpacing(0f, 1.25f) }
        rc.addView(stakeV); rc.addView(detV)
        root.addView(rc)

        root.addView(Button(this).apply {
            text = "রেজাল্ট খাতা রিসেট"; setTextColor(MUTE); isAllCaps = false
            background = null; stateListAnimator = null
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setMessage("রেজাল্ট খাতা মুছে ফেলবেন?")
                    .setPositiveButton("মুছুন") { _, _ ->
                        getSharedPreferences("stats", MODE_PRIVATE).edit().clear().apply(); refresh()
                    }
                    .setNegativeButton("না", null).show()
            }
        })

        val fl = FrameLayout(this).apply { setBackgroundColor(BG) }
        val bid = resources.getIdentifier("bg_main", "drawable", packageName)
        if (bid != 0) {
            fl.addView(ImageView(this).apply { setImageResource(bid); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1))
            fl.addView(View(this).apply { setBackgroundColor(Color.parseColor("#B30B1220")) }, FrameLayout.LayoutParams(-1, -1))
        }
        fl.addView(ScrollView(this).apply { addView(root) }, FrameLayout.LayoutParams(-1, -1))
        setContentView(fl)

        val tw = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { refresh() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        listOf(eb, ep, er).forEach { it.addTextChangedListener(tw) }
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun f(d: Double) = String.format(Locale.US, "%.2f", d)

    private fun refresh() {
        val s = getSharedPreferences("stats", MODE_PRIVATE)
        val w = s.getInt("w", 0); val l = s.getInt("l", 0); val t = w + l
        val bal = eb.text.toString().toDoubleOrNull()
        val pay = ep.text.toString().toDoubleOrNull()
        val risk = er.text.toString().toDoubleOrNull()
        val be = if (pay != null && pay > 0) 100 / (1 + pay / 100) else 52.6

        if (t == 0) {
            accV.text = "—"; accV.setTextColor(TXT)
            cntV.text = "জিত 0   হার 0   মোট 0"
            noteV.text = "স্ক্যানের পর জিতলাম/হারলাম চাপলে এখানে জমা হবে।"
        } else {
            val rate = w * 100.0 / t
            accV.text = String.format(Locale.US, "%.1f%%", rate)
            accV.setTextColor(if (t < 30) TXT else if (rate > be) GRN else RED)
            cntV.text = "জিত $w   হার $l   মোট $t"
            noteV.text = if (t < 30) "৩০টার কম ট্রেড, এই হার এখনো ভরসাযোগ্য না"
                else "ব্রেক-ইভেন ${f(be)}%, আপনি " + (if (rate > be) "উপরে আছেন" else "নিচে আছেন")
        }

        if (bal == null || pay == null || risk == null || bal <= 0 || pay <= 0 || risk <= 0 || risk >= 100) {
            stakeV.text = "—"; detV.text = "সংখ্যাগুলো ঠিকভাবে লিখুন"; return
        }
        getSharedPreferences("risk", MODE_PRIVATE).edit()
            .putString("bal", eb.text.toString()).putString("pay", ep.text.toString()).putString("risk", er.text.toString()).apply()
        val stake = bal * risk / 100
        val profit = stake * pay / 100
        val half = Math.ceil(Math.log(0.5) / Math.log(1 - risk / 100)).toInt()
        stakeV.text = "স্টেক \$${f(stake)}"
        var o = "জিতলে লাভ  +\$${f(profit)}\nহারলে লস  −\$${f(stake)}\nব্রেক-ইভেন হার  ${f(be)}%\nব্যালেন্স অর্ধেক হতে টানা $half হার"
        if (risk > 5) o += "\n⚠️ ঝুঁকি ৫%-এর বেশি"
        detV.text = o
        detV.setTextColor(MUTE)
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
