package com.yourname.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View // Added import for View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast // Added import for Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.ArrayList

class QuestionActivity : AppCompatActivity() {

    private var currentQuestionIndex = 0
    private lateinit var questionList: ArrayList<Question>

    private lateinit var tvQuestion: TextView
    private lateinit var rgOptions: RadioGroup
    private lateinit var btnSubmit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_question) // Assumes activity_question.xml exists

        questionList = intent.getParcelableArrayListExtra<Question>("questionList") ?: ArrayList()

        tvQuestion = findViewById(R.id.tv_question) // Assumes R.id.tv_question exists
        rgOptions = findViewById(R.id.rg_options) // Assumes R.id.rg_options exists
        btnSubmit = findViewById(R.id.btn_submit) // Assumes R.id.btn_submit exists

        if (questionList.isNotEmpty()) {
            showQuestion()
        } else {
            tvQuestion.text = "没有可用的题目。"
            btnSubmit.isEnabled = false
            Toast.makeText(this, "没有题目可供测试，请联系管理员添加。", Toast.LENGTH_LONG).show()
            // Consider finishing the activity or guiding the user
            // finish()
        }

        btnSubmit.setOnClickListener {
            checkAnswer()
        }
    }

    private fun showQuestion() {
        if (currentQuestionIndex < questionList.size) {
            val question = questionList[currentQuestionIndex]
            tvQuestion.text = "${currentQuestionIndex + 1}. ${question.question}"

            rgOptions.removeAllViews()
            rgOptions.clearCheck()

            val options = listOfNotNull(
                question.optionA?.takeIf { it.isNotBlank() },
                question.optionB?.takeIf { it.isNotBlank() },
                question.optionC?.takeIf { it.isNotBlank() },
                question.optionD?.takeIf { it.isNotBlank() },
                question.optionE?.takeIf { it.isNotBlank() },
                question.optionF?.takeIf { it.isNotBlank() },
                question.optionG?.takeIf { it.isNotBlank() },
                question.optionH?.takeIf { it.isNotBlank() }
            )

            for ((index, optionText) in options.withIndex()) {
                val radioButton = RadioButton(this)
                radioButton.text = "${("A"[0] + index)}. $optionText"
                // IMPORTANT: Using View.generateViewId() is correct for dynamically created views
                // that need unique IDs for state saving or specific targeting if not part of a RadioGroup logic
                // that relies on child index or a simpler ID scheme.
                // For RadioGroup, often IDs are not strictly needed for individual buttons if you iterate children
                // or use `checkedRadioButtonId` which returns the ID of the selected RadioButton.
                // If you were to set specific IDs like R.id.option_a, they would need to be defined in ids.xml or similar.
                radioButton.id = View.generateViewId()
                rgOptions.addView(radioButton)
            }
            btnSubmit.text = if (currentQuestionIndex == questionList.size - 1) "提交" else "下一题"
        } else {
            // All questions answered, navigate to ResultActivity or show summary
            // This part should ideally be handled after the last question is answered and submitted
            // For now, let's assume checkAnswer() handles navigation upon completion.
            // If not, this logic might be premature here.
            Toast.makeText(this, "所有题目已完成!", Toast.LENGTH_SHORT).show()
            // The actual submission and navigation to ResultActivity should be triggered by the submit button
            // on the last question, or a separate summary screen.
        }
    }

    private fun checkAnswer() {
        val selectedRadioButtonId = rgOptions.checkedRadioButtonId
        if (selectedRadioButtonId != -1) {
            // val selectedRadioButton = findViewById<RadioButton>(selectedRadioButtonId)
            // val selectedAnswerLetter = selectedRadioButton.text.toString().substringBefore(".").trim()
            // val correctAnswer = questionList[currentQuestionIndex].correctAnswer
            // Scoring logic would go here.

            currentQuestionIndex++
            if (currentQuestionIndex < questionList.size) {
                showQuestion()
            } else {
                // Last question answered, navigate to ResultActivity
                val intent = Intent(this, ResultActivity::class.java)
                // Pass necessary data to ResultActivity, e.g., score, total questions
                // intent.putExtra("score", calculatedScore)
                // intent.putExtra("totalQuestions", questionList.size)
                // For now, just navigating. Actual data passing needs implementation.
                Toast.makeText(this, "考试完成! 即将显示结果。", Toast.LENGTH_SHORT).show()
                startActivity(intent)
                finish() // Finish QuestionActivity
            }
        } else {
            Toast.makeText(this, "请选择一个答案", Toast.LENGTH_SHORT).show()
        }
    }
}

