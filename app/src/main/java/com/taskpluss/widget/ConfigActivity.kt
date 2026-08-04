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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        val etUrl = findViewById<EditText>(R.id.et_webapp_url)
        val etUser = findViewById<EditText>(R.id.et_username)
        val etPass = findViewById<EditText>(R.id.et_password)
        val spinner = findViewById<Spinner>(R.id.spinner_interval)
        val btnLogin = findViewById<Button>(R.id.btn_login)
        val btnTest = findViewById<Button>(R.id.btn_test)
        val tvStatus = findViewById<TextView>(R.id.tv_status)

        etUrl.setText(Prefs.run { webappUrl })
        etUser.setText(Prefs.run { username })

        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            intervalLabels
        )
        val currentInterval = Prefs.run { intervalMin }
        val idx = intervals.indexOf(currentInterval).coerceAtLeast(0)
        spinner.setSelection(idx)

        btnLogin.setOnClickListener {
            val url = ApiClient.normalizeUrl(etUrl.text?.toString().orEmpty())
            etUrl.setText(url)
            val user = etUser.text?.toString()?.trim().orEmpty()
            val pass = etPass.text?.toString()?.orEmpty() ?: ""
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
                    Prefs.run {
                        webappUrl = url
                        username = user
                        token = result.token!!
                        intervalMin = intervals[spinner.selectedItemPosition]
                    }
                    AlarmHelper.schedule(this@ConfigActivity)
                    tvStatus.text = "ورود موفق — در حال دریافت داده…"
                    withContext(Dispatchers.IO) {
                        WidgetRenderer.fetchAndApply(this@ConfigActivity)
                    }
                    tvStatus.text = "ذخیره و به‌روزرسانی شد"
                    Toast.makeText(this@ConfigActivity, "آماده است", Toast.LENGTH_SHORT).show()
                } else {
                    tvStatus.text = result.message
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
            Prefs.run {
                webappUrl = url
                intervalMin = intervals[spinner.selectedItemPosition]
            }
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
