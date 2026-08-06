package com.taskpluss.widget

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.taskpluss.widget.model.TaskItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskFormActivity : AppCompatActivity() {

    private var selectedDateJalali: String = ""
    private var editingTask: TaskItem? = null
    private val groupKeys = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_form)

        val tvFormTitle = findViewById<TextView>(R.id.tv_form_title)
        val etTitle = findViewById<EditText>(R.id.et_task_title)
        val spinnerPriority = findViewById<Spinner>(R.id.spinner_priority)
        val spinnerGroup = findViewById<Spinner>(R.id.spinner_group)
        val tvDueDate = findViewById<TextView>(R.id.tv_due_date)
        val btnPickDate = findViewById<Button>(R.id.btn_pick_date)
        val btnClearDate = findViewById<Button>(R.id.btn_clear_date)
        val etNotes = findViewById<EditText>(R.id.et_notes)
        val btnSave = findViewById<Button>(R.id.btn_save_task)
        val btnDelete = findViewById<Button>(R.id.btn_delete_task)
        val btnClose = findViewById<Button>(R.id.btn_close)
        val tvStatus = findViewById<TextView>(R.id.tv_add_status)

        val priorityLabels = listOf(
            "بدون اولویت",
            "۱ — بالاترین",
            "۲ — بالا",
            "۳ — متوسط",
            "۴ — پایین",
            "۵ — پایین‌ترین"
        )
        spinnerPriority.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, priorityLabels
        )
        spinnerPriority.setSelection(0)

        val cache = Prefs.loadCache(this)
        groupKeys.clear()
        val groupLabels = mutableListOf<String>()
        groupKeys.add("none")
        groupLabels.add("بدون گروه")
        cache.groups.entries
            .filter { it.key != "none" }
            .forEach {
                groupKeys.add(it.key)
                groupLabels.add(it.value.name)
            }
        spinnerGroup.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, groupLabels
        )

        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        if (!taskId.isNullOrBlank()) {
            editingTask = cache.tasks.find { it.id == taskId }
            val t = editingTask
            if (t != null) {
                tvFormTitle.text = "ویرایش تسک"
                etTitle.setText(t.title)
                val pSel = if (t.priority in 1..5) t.priority else 0
                spinnerPriority.setSelection(pSel)
                val gi = groupKeys.indexOf(if (t.group.isBlank()) "none" else t.group)
                if (gi >= 0) spinnerGroup.setSelection(gi)
                selectedDateJalali = t.date.trim()
                tvDueDate.text = if (selectedDateJalali.isBlank()) "بدون تاریخ" else selectedDateJalali
                etNotes.setText(t.notes)
                btnDelete.visibility = View.VISIBLE
            }
        }

        btnPickDate.setOnClickListener {
            val initial = JalaliUtils.parseJalaliDate(selectedDateJalali)
            JalaliDatePickerDialog.show(this, initial) { y, m, d ->
                selectedDateJalali = JalaliUtils.formatJalaliDate(y, m, d)
                tvDueDate.text = selectedDateJalali
            }
        }

        btnClearDate.setOnClickListener {
            selectedDateJalali = ""
            tvDueDate.text = "بدون تاریخ"
        }

        btnClose.setOnClickListener { finish() }

        btnDelete.setOnClickListener {
            val existing = editingTask ?: return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("حذف تسک")
                .setMessage("آیا از حذف «${existing.title}» مطمئن هستید؟")
                .setPositiveButton("حذف") { _, _ ->
                    val baseUrl = Prefs.webappUrl(this)
                    val token = Prefs.token(this)
                    val appCtx = applicationContext
                    val id = existing.id
                    finish()
                    CoroutineScope(Dispatchers.IO).launch {
                        val result = ApiClient.deleteTask(baseUrl, token, id)
                        if (result.success) {
                            val c = Prefs.loadCache(appCtx)
                            val updated = c.copy(tasks = c.tasks.filter { it.id != id })
                            Prefs.saveCache(appCtx, updated)
                            WidgetRenderer.applyData(appCtx, updated)
                        }
                    }
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

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

            val priority = spinnerPriority.selectedItemPosition.coerceIn(0, 5)
            val groupKey = groupKeys.getOrElse(spinnerGroup.selectedItemPosition) { "none" }
            val notes = etNotes.text?.toString()?.trim().orEmpty()
            val date = selectedDateJalali
            val existing = editingTask
            val appCtx = applicationContext

            finish()

            CoroutineScope(Dispatchers.IO).launch {
                val result = if (existing != null) {
                    ApiClient.updateTaskFull(
                        baseUrl, token,
                        existing.copy(
                            title = title,
                            priority = priority,
                            group = groupKey,
                            date = date,
                            notes = notes
                        )
                    )
                } else {
                    ApiClient.addTask(
                        baseUrl, token, title,
                        priority = priority,
                        group = groupKey,
                        date = date,
                        notes = notes
                    )
                }

                if (result.success) {
                    val c = Prefs.loadCache(appCtx)
                    val tasks = if (existing != null) {
                        c.tasks.map {
                            if (it.id == existing.id) {
                                it.copy(
                                    title = title,
                                    priority = priority,
                                    group = groupKey,
                                    date = date,
                                    notes = notes
                                )
                            } else it
                        }
                    } else {
                        val nt = result.task
                        if (nt != null) listOf(nt) + c.tasks else c.tasks
                    }
                    val updated = c.copy(tasks = tasks, offline = false)
                    Prefs.saveCache(appCtx, updated)
                    WidgetRenderer.applyData(appCtx, updated)
                }
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
    }
}
