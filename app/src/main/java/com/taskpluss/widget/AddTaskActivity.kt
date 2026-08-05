package com.taskpluss.widget

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddTaskActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_task)

        val etTitle = findViewById<EditText>(R.id.et_task_title)
        val btnSave = findViewById<Button>(R.id.btn_save_task)
        val btnClose = findViewById<Button>(R.id.btn_close)
        val tvStatus = findViewById<TextView>(R.id.tv_add_status)

        btnClose.setOnClickListener { goHome() }

        btnSave.setOnClickListener {
            val title = etTitle.text?.toString()?.trim().orEmpty()
            if (title.isBlank()) {
                tvStatus.text = "عنوان را وارد کنید"
                return@setOnClickListener
            }
            val baseUrl = Prefs.webappUrl(this)
            val token = Prefs.token(this)
            if (baseUrl.isBlank() || token.isBlank()) {
                tvStatus.text = "ابتدا از تنظیمات وارد شوید"
                return@setOnClickListener
            }

            tvStatus.text = "در حال ذخیره…"
            btnSave.isEnabled = false
            btnClose.isEnabled = false

            val appCtx = applicationContext
            CoroutineScope(Dispatchers.Main).launch {
                val result = withContext(Dispatchers.IO) {
                    ApiClient.addTask(baseUrl, token, title)
                }
                val newTask = result.task
                if (result.success && newTask != null) {
                    withContext(Dispatchers.IO) {
                        val cache = Prefs.loadCache(appCtx)
                        val updated = cache.copy(
                            tasks = listOf(newTask) + cache.tasks,
                            offline = false
                        )
                        Prefs.saveCache(appCtx, updated)
                        WidgetRenderer.applyData(appCtx, updated)
                    }
                    Toast.makeText(this@AddTaskActivity, "تسک اضافه شد", Toast.LENGTH_SHORT).show()
                    goHome()
                } else {
                    tvStatus.text = result.message.ifBlank { "خطا در افزودن" }
                    btnSave.isEnabled = true
                    btnClose.isEnabled = true
                }
            }
        }
    }

    private fun goHome() {
        finish()
        moveTaskToBack(true)
    }
}
