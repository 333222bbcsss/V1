package com.yourname.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
// ExamRecord is now defined in its own file (ExamRecord.kt or Model.kt)
// Ensure the import points to the correct package if it's different.
import com.yourname.myapplication.ExamRecord

class ResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result) // Ensure R.layout.activity_result and its IDs exist

        // Retrieve data from Intent
        // Option 1: Retrieve individual strings (as in original code)
        // val name = intent.getStringExtra("name")
        // val major = intent.getStringExtra("major")
        // val specificMajor = intent.getStringExtra("specificMajor")
        // val correctRateBasedOnTotal = intent.getStringExtra("correctRateBasedOnTotal")
        // val correctRateBasedOnAnswered = intent.getStringExtra("correctRateBasedOnAnswered")

        // Option 2: Retrieve the entire ExamRecord object (Recommended)
        val examRecord = intent.getParcelableExtra<ExamRecord>("examRecord")
        val totalQuestions = intent.getIntExtra("totalQuestions", 0)
        val correctAnswers = intent.getIntExtra("correctAnswers", 0)
        val answeredQuestions = intent.getIntExtra("answeredQuestions", 0)

        val resultTextView = findViewById<TextView>(R.id.result_text) // Correct rate based on total
        val realResultTextView = findViewById<TextView>(R.id.real_result_text) // Correct rate based on answered
        val nameTextView = findViewById<TextView>(R.id.name_text)
        val majorTextView = findViewById<TextView>(R.id.major_text)
        val tvSpecificMajor = findViewById<TextView>(R.id.tv_specific_major)
        val tvScoreDetails = findViewById<TextView>(R.id.tv_score_details) // For total, correct, answered

        if (examRecord != null) {
            resultTextView.text = "总正确率: ${examRecord.correctRateBasedOnTotal ?: "N/A"}"
            realResultTextView.text = "已答正确率: ${examRecord.correctRateBasedOnAnswered ?: "N/A"}"
            nameTextView.text = "姓名: ${examRecord.name ?: "N/A"}"
            majorTextView.text = "考试专业: ${examRecord.major ?: "N/A"}"
            tvSpecificMajor.text = "具体专业: ${examRecord.specificMajor ?: "N/A"}"
            tvScoreDetails.text = "总题数: $totalQuestions, 答对: $correctAnswers, 已答: $answeredQuestions"

            saveExamRecordToPreferences(examRecord)
        } else {
            // Handle case where examRecord is null, though it shouldn't happen if passed correctly
            resultTextView.text = "错误: 未能加载考试结果"
            // Potentially use the individual string extras as a fallback if Option 1 was used by ExamActivity
        }

        val btnReturnToLogin = findViewById<Button>(R.id.btn_return_to_login)
        btnReturnToLogin.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            intent.putExtra("reset_form", true) // To clear login form in MainActivity
            startActivity(intent)
            finish()
        }
    }

    private fun saveExamRecordToPreferences(recordToSave: ExamRecord) {
        val sharedPreferences = getSharedPreferences("ExamRecordsPrefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val jsonRecords = sharedPreferences.getString("records", null)
        val type = object : TypeToken<MutableList<ExamRecord>>() {}.type
        val records: MutableList<ExamRecord> = if (jsonRecords != null) {
            gson.fromJson(jsonRecords, type)
        } else {
            mutableListOf()
        }

        // Add the new record at the beginning of the list
        records.add(0, recordToSave)

        // Keep only the latest, e.g., 10 records
        while (records.size > 10) {
            records.removeAt(records.size - 1)
        }

        val updatedJsonRecords = gson.toJson(records)
        sharedPreferences.edit().putString("records", updatedJsonRecords).apply()
    }
}
