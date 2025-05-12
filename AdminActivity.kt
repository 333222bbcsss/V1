package com.yourname.myapplication

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AdminActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make sure this layout matches your revised XML file (e.g., activity_admin_revised.xml)
        setContentView(R.layout.activity_admin) // Or R.layout.activity_admin_revised if you renamed it

        // Views for total exam time
        val etTotalExamTime = findViewById<EditText>(R.id.et_total_exam_time)
        val btnSaveTotalTime = findViewById<Button>(R.id.btn_save_total_time)

        // Views for question order
        val rgQuestionOrder = findViewById<RadioGroup>(R.id.rg_question_order)
        val btnSaveQuestionOrder = findViewById<Button>(R.id.btn_save_question_order)

        // Return button
        val btnReturnToMain = findViewById<Button>(R.id.btn_return_to_main)

        // SharedPreferences for loading and saving settings
        val sharedPref = getSharedPreferences("AdminSettings", Context.MODE_PRIVATE)

        // Load existing settings and populate UI
        // Load total exam time, default to 120 minutes if not set or empty
        val savedTime = sharedPref.getString("total_exam_time_minutes", "120")
        etTotalExamTime.setText(savedTime)

        val currentOrder = sharedPref.getString("question_order", "sequential") // Default to sequential
        if (currentOrder == "random") {
            rgQuestionOrder.check(R.id.rb_order_random) // Assumes R.id.rb_order_random exists in layout
        } else {
            rgQuestionOrder.check(R.id.rb_order_sequential) // Assumes R.id.rb_order_sequential exists in layout
        }

        // Listener for saving total exam time
        btnSaveTotalTime.setOnClickListener {
            var totalExamTime = etTotalExamTime.text.toString().trim()
            if (totalExamTime.isEmpty()) {
                totalExamTime = "120" // Default to 120 if empty
                etTotalExamTime.setText(totalExamTime) // Update UI with default
            }

            // Basic validation to ensure it's a number
            if (!totalExamTime.all { it.isDigit() } || totalExamTime.toIntOrNull() == null) {
                Toast.makeText(this, "请输入有效的总答题时间 (数字)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            with(sharedPref.edit()) {
                putString("total_exam_time_minutes", totalExamTime)
                apply()
            }
            Toast.makeText(this, "总答题时间已保存: $totalExamTime 分钟", Toast.LENGTH_LONG).show()
        }

        // Listener for saving question order
        btnSaveQuestionOrder.setOnClickListener {
            val selectedOrderId = rgQuestionOrder.checkedRadioButtonId
            // Ensure a radio button is actually selected
            if (selectedOrderId == -1) {
                Toast.makeText(this, "请选择出题顺序", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val orderMode = if (selectedOrderId == R.id.rb_order_random) "random" else "sequential"

            with(sharedPref.edit()) {
                putString("question_order", orderMode)
                apply()
            }
            Toast.makeText(this, "出题顺序已保存: ${if (orderMode == "random") "随机" else "顺序"}", Toast.LENGTH_SHORT).show()
        }

        // Listener for return button
        btnReturnToMain.setOnClickListener {
            finish()
        }
    }
}

