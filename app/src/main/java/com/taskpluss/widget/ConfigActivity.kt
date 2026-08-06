package com.taskpluss.widget

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigActivity : AppCompatActivity() {

    private val intervals = listOf(0, 15, 30, 60, 120)
    private val intervalLabels = listOf("فقط دستی", "۱۵ دقیقه", "۳۰ دقیقه", "۶۰ دقیقه", "۱۲۰ دقیقه")

    private val fontSizes = listOf(10f, 11f, 12f, 13f, 14f, 16f)
    private val fontLabels = listOf(
        "۱۰ — خیلی کوچک",
        "۱۱ — کوچک",
        "۱۲ — متوسط",
        "۱۳ — کمی بزرگ",
        "۱۴ — بزرگ",
        "۱۶ — خیلی بزرگ"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        val etUrl = findViewById<EditText>(R.id.et_webapp_url)
        val etUser = findViewById<EditText>(R.id.et_username)
        val etPass = findViewById<EditText>(R.id.et_password)
        val spinner = findViewById<Spinner>(R.id.spinner_interval)
        val spinnerFont = findViewById<Spinner>(R.id.spinner_font_size)
        val btnLogin = findViewById<Button>(R.id.btn_login)
        val btnTest = findViewById<Button>(R.id.btn_test)
        val btnApplyFont = findViewById<Button>(R.id.btn_apply_font)
        val tvStatus = findViewById<TextView>(R.id.tv_status)

        val savedUrl = Prefs.webappUrl(this)
        etUrl.setText(if (savedUrl.isBlank()) ApiClient.DEFAULT_WEBAPP_URL else savedUrl)
        etUser.setText(Prefs.username(this))

        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, intervalLabels
        )
        spinner.setSelection(intervals.indexOf(Prefs.intervalMin(this)).coerceAtLeast(0))

        spinnerFont.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, fontLabels
        )
        val curFont = Prefs.taskFontSp(this)
        val fontIdx = fontSizes.indexOfFirst { kotlin.math.abs(it - curFont) < 0.01f }
            .let { if (it < 0) 1 else it }
        spinnerFont.setSelection(fontIdx)

        btnApplyFont.setOnClickListener {
            val spSize = fontSizes.getOrElse(spinnerFont.selectedItemPosition) { 11f }
            Prefs.setTaskFontSp(this, spSize)
            Prefs.setIntervalMin(this, intervals.getOrElse(spinner.selectedItemPosition) { 30 })
            AlarmHelper.schedule(this)
            WidgetRenderer.applyData(this, Prefs.loadCache(this))
            Toast.makeText(this, "اندازه فونت اعمال شد", Toast.LENGTH_SHORT).show()
            tvStatus.text = "فونت ${spSize.toInt()}sp ذخیره شد"
        }

        btnLogin.setOnClickListener {
            val url = ApiClient.normalizeUrl(etUrl.text?.toString().orEmpty())
            etUrl.setText(url)
            val user = etUser.text?.toString()?.trim().orEmpty()
            val pass = etPass.text?.toString().orEmpty()
            if (url.isBlank() || user.isBlank() || pass.isBlank()) {
                tvStatus.text = "همه فیلدها را پر کنید"
                return@setOnClickListener
            }
            if (!url.contains("/exec")) {
                tvStatus.text =
                    "آدرس باید به /exec ختم شود\nمثال:\nhttps://script.google.com/macros/s/XXXX/exec"
                return@setOnClickListener
            }
            tvStatus.text = "در حال ورود…"
            btnLogin.isEnabled = false

            CoroutineScope(Dispatchers.Main).launch {
                val result = withContext(Dispatchers.IO) {
                    ApiClient.login(url, user, pass)
                }
                if (result.success && !result.token.isNullOrBlank()) {
                    Prefs.setWebappUrl(this@ConfigActivity, url)
                    Prefs.setUsername(this@ConfigActivity, user)
                    Prefs.setToken(this@ConfigActivity, result.token!!)
                    Prefs.setIntervalMin(
                        this@ConfigActivity,
                        intervals[spinner.selectedItemPosition]
                    )
                    Prefs.setTaskFontSp(
                        this@ConfigActivity,
                        fontSizes.getOrElse(spinnerFont.selectedItemPosition) { 11f }
                    )
                    AlarmHelper.schedule(this@ConfigActivity)
                    tvStatus.text = "ورود موفق — در حال بارگذاری…"
                    withContext(Dispatchers.IO) {
                        WidgetRenderer.fetchAndApply(this@ConfigActivity)
                    }
                    tvStatus.text = "آماده است"
                    Toast.makeText(this@ConfigActivity, "آماده است", Toast.LENGTH_SHORT).show()
                } else {
                    tvStatus.text = result.message.ifBlank { "ورود ناموفق" }
                }
                btnLogin.isEnabled = true
            }
        }

        btnTest.setOnClickListener {
            val url = ApiClient.normalizeUrl(etUrl.text?.toString().orEmpty())
            etUrl.setText(url)
            if (url.isBlank()) {
                tvStatus.text = "آدرس Web App را وارد کنید"
                return@setOnClickListener
            }
            Prefs.setWebappUrl(this, url)
            Prefs.setIntervalMin(this, intervals[spinner.selectedItemPosition])
            Prefs.setTaskFontSp(
                this,
                fontSizes.getOrElse(spinnerFont.selectedItemPosition) { 11f }
            )
            AlarmHelper.schedule(this)
            tvStatus.text = "در حال رفرش…"
            CoroutineScope(Dispatchers.Main).launch {
                withContext(Dispatchers.IO) {
                    WidgetRenderer.fetchAndApply(this@ConfigActivity)
                }
                tvStatus.text = "رفرش انجام شد"
            }
        }
    }
}
