package com.taskpluss.widget

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** سازگاری با PendingIntent ویجت — فرم کامل */
class AddTaskActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, TaskFormActivity::class.java))
        finish()
    }
}
