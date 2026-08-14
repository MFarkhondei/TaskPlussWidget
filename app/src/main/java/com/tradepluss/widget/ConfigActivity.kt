package com.tradepluss.widget

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigActivity : AppCompatActivity() {

    companion object {
        const val DEFAULT_WEBAPP_URL =
            "https://script.google.com/macros/s/AKfycbzo7wgBwhzCxrOgIndTCz3aj1Rjog_rx-IpCi8py-LGPlh6-skS0KgNO6LnxItAGOZE/exec"
    }

    private var testJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        val etUrl = findViewById<TextInputEditText>(R.id.et_webapp_url)
        val etUser = findViewById<TextInputEditText>(R.id.et_username)
        val etPassword = findViewById<TextInputEditText>(R.id.et_password)
        val spinner = findViewById<Spinner>(R.id.spinner_interval)
        val btnSave = findViewById<MaterialButton>(R.id.btn_save)
        val btnTestConnection = findViewById<MaterialButton>(R.id.btn_test_connection)
        val tvStatus = findViewById<TextView>(R.id.tv_status)

        etUrl.setText(Prefs.getUrl(this).ifBlank { DEFAULT_WEBAPP_URL })
        etUser.setText(Prefs.getUser(this))
        etPassword.setText(Prefs.getPassword(this))

        val labels = resources.getStringArray(R.array.refresh_interval_labels)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val currentInterval = Prefs.getIntervalMin(this)
        val idx = Prefs.INTERVAL_OPTIONS.indexOf(currentInterval).let { if (it >= 0) it else 1 }
        spinner.setSelection(idx)

        btnSave.setOnClickListener {
            val url = etUrl.text?.toString()?.trim().orEmpty()
            val user = etUser.text?.toString()?.trim().orEmpty()
            val password = etPassword.text?.toString().orEmpty()
            val interval = Prefs.INTERVAL_OPTIONS.getOrElse(spinner.selectedItemPosition) { 30 }

            if (url.isBlank() || user.isBlank() || password.isBlank()) {
                tvStatus.text = "همه فیلدها الزامی هستند"
                return@setOnClickListener
            }
            if (!url.startsWith("http")) {
                tvStatus.text = "آدرس Web App باید با https شروع شود"
                return@setOnClickListener
            }

            Prefs.save(this, url, user, password, interval)
            UpdateScheduler.schedule(this)
            Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
            WidgetRenderer.applyCache(this, offline = false)
            WidgetUpdateService.start(this)
            val intervalText = if (interval > 0) "هر $interval دقیقه" else "فقط دستی"
            tvStatus.text = "✅ ذخیره شد · به‌روزرسانی خودکار: $intervalText"
        }

        btnTestConnection.setOnClickListener {
            val url = etUrl.text?.toString()?.trim().orEmpty()
            val user = etUser.text?.toString()?.trim().orEmpty()
            val password = etPassword.text?.toString().orEmpty()
            val interval = Prefs.INTERVAL_OPTIONS.getOrElse(spinner.selectedItemPosition) { 30 }

            if (url.isBlank() || user.isBlank() || password.isBlank()) {
                tvStatus.text = "همه فیلدها الزامی هستند"
                return@setOnClickListener
            }
            if (!url.startsWith("http")) {
                tvStatus.text = "آدرس Web App باید با https شروع شود"
                return@setOnClickListener
            }

            testJob?.cancel()
            btnTestConnection.isEnabled = false
            btnTestConnection.text = getString(R.string.testing)
            tvStatus.text = "در حال اتصال..."

            testJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    Prefs.save(this@ConfigActivity, url, user, password, interval)
                    UpdateScheduler.schedule(this@ConfigActivity)

                    val ok = WidgetRenderer.fetchAndApply(this@ConfigActivity)

                    withContext(Dispatchers.Main) {
                        if (ok) {
                            tvStatus.text = getString(R.string.connection_success)
                            Toast.makeText(
                                this@ConfigActivity,
                                "داده روی ویجت اعمال شد",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            tvStatus.text = getString(R.string.connection_failed, "پاسخ نامعتبر یا شبکه")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvStatus.text = getString(R.string.connection_failed, e.message ?: "خطای ناشناخته")
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        btnTestConnection.isEnabled = true
                        btnTestConnection.text = getString(R.string.test_connection)
                    }
                }
            }
        }
    }
}
