package com.yourname.myapplication

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.SimpleDateFormat
import java.util.*

// OnQuestionClickListener interface should be defined if QuestionCardAdapter uses it.
// Assuming it's defined in QuestionCardAdapter.kt or another shared file.
// interface OnQuestionClickListener { fun onQuestionClick(index: Int) }
class ExamActivity : AppCompatActivity(), OnQuestionClickListener { // Implement if needed by adapter
    private val minTextSizeMultiplier = 0.8f
    private val maxTextSizeMultiplier = 1.5f
    private val textSizeStep = 0.1f

    private var studentName: String? = null
    private var studentMajor: String? = null
    private var studentSpecificMajor: String? = null

    private var questionCardDialog: AlertDialog? = null
    private var settingsDialog: AlertDialog? = null

    private var questionList = mutableListOf<Question>()
    private var userAnswers = mutableListOf<MutableList<Int>>() // Stores selected option indices
    private var currentQuestionIndex = 0

    private lateinit var tvQuestion: TextView
    private lateinit var btnPrevious: Button
    private lateinit var btnNext: Button
    private lateinit var tvCountdown: TextView
    private lateinit var tvQuestionType: TextView
    private lateinit var llOptions: LinearLayout
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var tvQuestionNumber: TextView
    private lateinit var navigationButtonsLayout: LinearLayout
    private lateinit var scrollView: ScrollView

    private lateinit var sharedPreferences: SharedPreferences
    private var currentTextSizeMultiplier = 1.0f
    private var showNavigationButtons = true
    private var autoNextQuestion = false
    private var countDownTimer: CountDownTimer? = null
    private var examDurationMillis: Long = 30 * 60 * 1000 // Default 30 minutes

    private var correctCount = 0
    private var answeredCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exam) // Ensure R.layout.activity_exam and all IDs exist

        studentName = intent.getStringExtra("name")
        studentMajor = intent.getStringExtra("major")
        studentSpecificMajor = intent.getStringExtra("specificMajor")

        tvQuestion = findViewById(R.id.tv_question)
        btnPrevious = findViewById(R.id.btn_previous)
        btnNext = findViewById(R.id.btn_next)
        tvCountdown = findViewById(R.id.tv_countdown)
        tvQuestionType = findViewById(R.id.tv_question_type)
        llOptions = findViewById(R.id.ll_options)
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        tvQuestionNumber = findViewById(R.id.tv_question_number)
        navigationButtonsLayout = findViewById(R.id.navigation_buttons_layout)
        scrollView = findViewById(R.id.scrollView) // Ensure this ID exists in your layout

        sharedPreferences = getSharedPreferences("ExamSettings", MODE_PRIVATE)
        loadSettings()

        // 读取管理员设置的考试时间和出题顺序
        val adminSharedPref = getSharedPreferences("AdminSettings", Context.MODE_PRIVATE)
        examDurationMillis = adminSharedPref.getString("total_exam_time_minutes", "30")?.toInt()?.times(60 * 1000)?.toLong() ?: 30 * 60 * 1000
        val questionOrder = adminSharedPref.getString("question_order", "sequential") ?: "sequential"

        if (studentSpecificMajor != null) {
            loadQuestionsFromDatabase(studentSpecificMajor!!)
        } else {
            Toast.makeText(this, "未指定具体专业，无法加载题目", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (questionList.isEmpty()) {
            Toast.makeText(this, "题库中没有找到 '$studentSpecificMajor' 的题目", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 根据出题顺序设置对题目列表进行处理
        if (questionOrder == "random") {
            reorderQuestionsRandomly()
        }

        userAnswers.clear()
        userAnswers.addAll(List(questionList.size) { mutableListOf<Int>() })

        showQuestion(currentQuestionIndex)
        applyTextSize()
        applyNavigationButtonVisibility()
        startCountdown(examDurationMillis)

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_submit -> {
                    confirmSubmitExam()
                    true
                }
                R.id.navigation_card -> {
                    showQuestionCardDialog()
                    true
                }
                R.id.navigation_settings -> {
                    showSettingsDialog()
                    true
                }
                else -> false
            }
        }

        btnPrevious.setOnClickListener {
            if (currentQuestionIndex > 0) {
                saveCurrentAnswer(isNavigating = true)
                currentQuestionIndex--
                showQuestion(currentQuestionIndex)
            }
        }

        btnNext.setOnClickListener {
            if (currentQuestionIndex < questionList.size - 1) {
                saveCurrentAnswer(isNavigating = true)
                currentQuestionIndex++
                showQuestion(currentQuestionIndex)
            }
        }
    }

    private fun loadQuestionsFromDatabase(specificMajor: String) {
        val dbHelper = ExamDbHelper(this) // Use the corrected ExamDbHelper
        val db = dbHelper.readableDatabase
        val projection = arrayOf(
            ExamDbHelper.COLUMN_ID, ExamDbHelper.COLUMN_QUESTION, ExamDbHelper.COLUMN_OPTION_A,
            ExamDbHelper.COLUMN_OPTION_B, ExamDbHelper.COLUMN_OPTION_C, ExamDbHelper.COLUMN_OPTION_D,
            ExamDbHelper.COLUMN_OPTION_E, ExamDbHelper.COLUMN_OPTION_F, ExamDbHelper.COLUMN_OPTION_G,
            ExamDbHelper.COLUMN_OPTION_H, ExamDbHelper.COLUMN_CORRECT_ANSWER,
            ExamDbHelper.COLUMN_ACTUAL_QUESTION_TYPE, ExamDbHelper.COLUMN_QUESTION_TYPE
        )
        // COLUMN_QUESTION_TYPE in DB stores the specificMajor
        val selection = "${ExamDbHelper.COLUMN_QUESTION_TYPE} = ?"
        val selectionArgs = arrayOf(specificMajor)

        db.query(ExamDbHelper.TABLE_QUESTIONS, projection, selection, selectionArgs, null, null, "${ExamDbHelper.COLUMN_ID} ASC")?.use { cursor ->
            questionList.clear()
            while (cursor.moveToNext()) {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(ExamDbHelper.COLUMN_ID))
                val questionText = cursor.getString(cursor.getColumnIndexOrThrow(ExamDbHelper.COLUMN_QUESTION))
                val optionA = cursor.getString(cursor.getColumnIndexOrThrow(ExamDbHelper.COLUMN_OPTION_A))
                val optionB = cursor.getString(cursor.getColumnIndexOrThrow(ExamDbHelper.COLUMN_OPTION_B))
                val optionC = cursor.getString(cursor.getColumnIndexOrThrow(ExamDbHelper.COLUMN_OPTION_C))
                val optionD = cursor.getString(cursor.getColumnIndexOrThrow(ExamDbHelper.COLUMN_OPTION_D))
                val optionE = cursor.getString(cursor.getColumnIndexOrThrow(ExamDbHelper.COLUMN_OPTION_E))
                val optionF = cursor.getString(cursor.getColumnIndexOrThrow(ExamDbHelper.COLUMN_OPTION_F))
                val optionG = cursor.getString(cursor.getColumnIndexOrThrow(ExamDbHelper.COLUMN_OPTION_G))
                val optionH = cursor.getString(cursor.getColumnIndexOrThrow(ExamDbHelper.COLUMN_OPTION_H))
                val correctAnswer = cursor.getString(cursor.getColumnIndexOrThrow(ExamDbHelper.COLUMN_CORRECT_ANSWER))
                val actualQuestionTypeStr = cursor.getString(cursor.getColumnIndexOrThrow(ExamDbHelper.COLUMN_ACTUAL_QUESTION_TYPE))

                val questionTypeEnum = when (actualQuestionTypeStr) {
                    "单选题" -> QuestionType.SINGLE_CHOICE
                    "多选题" -> QuestionType.MULTI_CHOICE
                    "判断题" -> QuestionType.JUDGMENT
                    else -> QuestionType.SINGLE_CHOICE // Default or handle error
                }
                questionList.add(Question(id, questionText, optionA, optionB, optionC, optionD, optionE, optionF, optionG, optionH, correctAnswer, questionTypeEnum, actualQuestionTypeStr))
            }
        }
        Log.d("ExamActivity", "Loaded ${questionList.size} questions for '$specificMajor' from database")
        db.close()
    }

    private fun reorderQuestionsRandomly() {
        val singleChoiceQuestions = mutableListOf<Question>()
        val multiChoiceQuestions = mutableListOf<Question>()
        val judgmentQuestions = mutableListOf<Question>()

        // 分离不同类型的题目
        for (question in questionList) {
            when (question.questionType) {
                QuestionType.SINGLE_CHOICE -> singleChoiceQuestions.add(question)
                QuestionType.MULTI_CHOICE -> multiChoiceQuestions.add(question)
                QuestionType.JUDGMENT -> judgmentQuestions.add(question)
            }
        }

        // 对不同类型的题目进行随机排序
        singleChoiceQuestions.shuffle()
        multiChoiceQuestions.shuffle()
        judgmentQuestions.shuffle()

        // 合并排序后的题目
        questionList.clear()
        questionList.addAll(singleChoiceQuestions)
        questionList.addAll(multiChoiceQuestions)
        questionList.addAll(judgmentQuestions)
    }

    private fun showQuestion(index: Int) {
        if (index < 0 || index >= questionList.size) return
        currentQuestionIndex = index
        val question = questionList[index]

        tvQuestion.text = "${index + 1}. ${question.question}"
        tvQuestionType.text = question.actualQuestionType
        llOptions.removeAllViews()

        val currentAnswers = userAnswers[index]

        when (question.questionType) {
            QuestionType.SINGLE_CHOICE, QuestionType.JUDGMENT -> {
                val radioGroup = RadioGroup(this)
                val options = if (question.questionType == QuestionType.JUDGMENT) {
                    listOf("正确", "错误") // Options for judgment
                } else {
                    listOfNotNull(question.optionA, question.optionB, question.optionC, question.optionD, question.optionE, question.optionF, question.optionG, question.optionH).filter { it.isNotBlank() }
                }

                options.forEachIndexed { optionIndex, optionText ->
                    val radioButton = RadioButton(this)
                    radioButton.text = if (question.questionType == QuestionType.JUDGMENT) optionText else "${('A' + optionIndex)}. $optionText"
                    radioButton.id = optionIndex // Use index as ID
                    radioGroup.addView(radioButton)
                    if (currentAnswers.contains(optionIndex)) {
                        radioButton.isChecked = true
                    }
                    radioButton.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            saveCurrentAnswer()
                            if (autoNextQuestion && currentQuestionIndex < questionList.size - 1) {
                                btnNext.performClick()
                            }
                        }
                    }
                }
                llOptions.addView(radioGroup)
            }
            QuestionType.MULTI_CHOICE -> {
                val options = listOfNotNull(question.optionA, question.optionB, question.optionC, question.optionD, question.optionE, question.optionF, question.optionG, question.optionH).filter { it.isNotBlank() }
                options.forEachIndexed { optionIndex, optionText ->
                    val checkBox = CheckBox(this)
                    checkBox.text = "${('A' + optionIndex)}. $optionText"
                    checkBox.id = optionIndex // Use index as ID
                    llOptions.addView(checkBox)
                    if (currentAnswers.contains(optionIndex)) {
                        checkBox.isChecked = true
                    }
                    checkBox.setOnCheckedChangeListener { _, _ -> saveCurrentAnswer() }
                }
            }
        }
        tvQuestionNumber.text = "${index + 1}/${questionList.size}"
        btnPrevious.isEnabled = index > 0
        btnNext.isEnabled = index < questionList.size - 1
        applyTextSize()
        scrollView.post { scrollView.scrollTo(0, 0) } // Scroll to top
    }

    private fun saveCurrentAnswer(isNavigating: Boolean = false) {
        Log.d("ExamDebug", "saveCurrentAnswer START for q_idx: $currentQuestionIndex, isNavigating: $isNavigating. llOptions child count: ${llOptions.childCount}")
        if (currentQuestionIndex < 0 || currentQuestionIndex >= questionList.size) {
            Log.d("ExamDebug", "saveCurrentAnswer: Invalid currentQuestionIndex $currentQuestionIndex. List size ${questionList.size}. Aborting.")
            return
        }
        val question = questionList[currentQuestionIndex]
        Log.d("ExamDebug", "saveCurrentAnswer for Q: ${question.question.take(20)} (Type: ${question.questionType})")

        if (currentQuestionIndex >= userAnswers.size) {
            Log.e("ExamDebug", "Error: userAnswers list too small. Index: $currentQuestionIndex, Size: ${userAnswers.size}")
            return
        }

        userAnswers[currentQuestionIndex].clear()

        when (question.questionType) {
            QuestionType.SINGLE_CHOICE, QuestionType.JUDGMENT -> {
                if (llOptions.childCount > 0) {
                    val radioGroup = llOptions.getChildAt(0) as? RadioGroup
                    if (radioGroup != null) {
                        Log.d("ExamDebug", "SINGLE/JUDGMENT: Found RadioGroup. Child count: ${radioGroup.childCount}. CheckedId from RG: ${radioGroup.checkedRadioButtonId}")
                        var foundChecked = false
                        for (i in 0 until radioGroup.childCount) {
                            val radioButton = radioGroup.getChildAt(i) as? RadioButton
                            if (radioButton != null) {
                                Log.d("ExamDebug", "SINGLE/JUDGMENT: RadioButton $i, ID: ${radioButton.id}, Text: '${radioButton.text}', isChecked: ${radioButton.isChecked}")
                                if (radioButton.isChecked) {
                                    userAnswers[currentQuestionIndex].add(radioButton.id)
                                    Log.d("ExamDebug", ">>> SAVED SINGLE/JUDGMENT for q_idx $currentQuestionIndex. User choice ID: ${radioButton.id}. Current answers for this q: ${userAnswers[currentQuestionIndex]}")
                                    foundChecked = true
                                    break
                                }
                            } else {
                                Log.d("ExamDebug", "SINGLE/JUDGMENT: Child $i of RadioGroup is not a RadioButton.")
                            }
                        }
                        if (!foundChecked) {
                            Log.d("ExamDebug", "SINGLE/JUDGMENT: No RadioButton found checked by iterating in RadioGroup for q_idx $currentQuestionIndex.")
                        }
                    } else {
                        Log.d("ExamDebug", "SINGLE/JUDGMENT: llOptions.getChildAt(0) is NOT a RadioGroup for q_idx $currentQuestionIndex. It is: ${llOptions.getChildAt(0)?.javaClass?.name}")
                    }
                } else {
                    Log.d("ExamDebug", "SINGLE/JUDGMENT: llOptions is empty for q_idx $currentQuestionIndex.")
                }
            }
            QuestionType.MULTI_CHOICE -> {
                Log.d("ExamDebug", "MULTI: Processing ${llOptions.childCount} children in llOptions for q_idx $currentQuestionIndex.")
                var anyCheckboxChecked = false
                for (i in 0 until llOptions.childCount) {
                    val checkBox = llOptions.getChildAt(i) as? CheckBox
                    if (checkBox != null) {
                        Log.d("ExamDebug", "MULTI: CheckBox $i, ID: ${checkBox.id}, Text: '${checkBox.text}', isChecked: ${checkBox.isChecked}")
                        if (checkBox.isChecked) {
                            userAnswers[currentQuestionIndex].add(checkBox.id)
                            Log.d("ExamDebug", ">>> SAVED MULTI for q_idx $currentQuestionIndex. User choice ID: ${checkBox.id}. Current answers for this q: ${userAnswers[currentQuestionIndex]}")
                            anyCheckboxChecked = true
                        }
                    } else {
                        Log.d("ExamDebug", "MULTI: Child $i of llOptions is NOT a CheckBox for q_idx $currentQuestionIndex. It is: ${llOptions.getChildAt(i)?.javaClass?.name}")
                    }
                }
                if (!anyCheckboxChecked) {
                    Log.d("ExamDebug", "MULTI: No CheckBoxes found checked for q_idx $currentQuestionIndex.")
                }
            }
        }
        Log.d("ExamDebug", "saveCurrentAnswer END for q_idx $currentQuestionIndex. userAnswers[$currentQuestionIndex]: ${userAnswers[currentQuestionIndex]}")
    }

    private fun calculateResults() {
        Log.d("ExamDebug", "calculateResults called. userAnswers before calculation: ${userAnswers.joinToString { "[${it.joinToString()}]" }}")
        Log.d("ExamDebug", "Total questions: ${questionList.size}, UserAnswers list size: ${userAnswers.size}")
        correctCount = 0
        answeredCount = 0
        for (i in questionList.indices) {
            val question = questionList[i]
            val userSelectedIndices = userAnswers[i].sorted()
            if (userSelectedIndices.isNotEmpty()) {
                answeredCount++
                val correctAnswerIndices = convertAnswerToIndices(question.correctAnswer, question.questionType)
                if (userSelectedIndices == correctAnswerIndices) {
                    correctCount++
                }
            }
        }
    }

    private fun convertAnswerToIndices(answerString: String, questionType: QuestionType): List<Int> {
        val indices = mutableListOf<Int>()
        when (questionType) {
            QuestionType.SINGLE_CHOICE -> {
                if (answerString.isNotBlank()) indices.add(answerString[0] - 'A')
            }
            QuestionType.MULTI_CHOICE -> {
                answerString.forEach { char -> indices.add(char - 'A') }
            }
            QuestionType.JUDGMENT -> {
                if (answerString == "正确") indices.add(0)
                else if (answerString == "错误") indices.add(1)
            }
        }
        return indices.sorted()
    }

    private fun startCountdown(durationMillis: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                examDurationMillis = millisUntilFinished
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                tvCountdown.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
            override fun onFinish() {
                tvCountdown.text = "时间到!"
                submitExam(false)
            }
        }.start()
    }

    private fun confirmSubmitExam() {
        AlertDialog.Builder(this)
            .setTitle("交卷确认")
            .setMessage("确定要交卷吗？")
            .setPositiveButton("确定") { _, _ -> submitExam(true) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun submitExam(isManualSubmit: Boolean) {
        Log.d("ExamDebug", "submitExam called. Manual submit: $isManualSubmit")
        countDownTimer?.cancel()
        saveCurrentAnswer(isNavigating = true)
        calculateResults()

        val totalQuestions = questionList.size
        val correctRateTotal = if (totalQuestions > 0) "%.2f%%".format((correctCount.toFloat() / totalQuestions) * 100) else "0.00%"
        val correctRateAnswered = if (answeredCount > 0) "%.2f%%".format((correctCount.toFloat() / answeredCount) * 100) else "0.00%"

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val submissionTime = sdf.format(Date())

        val examRecord = ExamRecord(
            name = studentName,
            major = studentMajor,
            specificMajor = studentSpecificMajor,
            correctRateBasedOnTotal = correctRateTotal,
            correctRateBasedOnAnswered = correctRateAnswered,
            submissionTimestamp = submissionTime
        )

        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra("examRecord", examRecord)
            putExtra("correctRateBasedOnTotal", correctRateTotal)
            putExtra("correctRateBasedOnAnswered", correctRateAnswered)
            putExtra("name", studentName)
            putExtra("major", studentMajor)
            putExtra("specificMajor", studentSpecificMajor)
            putExtra("totalQuestions", totalQuestions)
            putExtra("correctAnswers", correctCount)
            putExtra("answeredQuestions", answeredCount)
        }
        startActivity(intent)
        finish()
    }


    override fun onQuestionClick(index: Int) {
        if (index in questionList.indices) {
            saveCurrentAnswer(isNavigating = true)
            currentQuestionIndex = index
            showQuestion(currentQuestionIndex)
        }
        questionCardDialog?.dismiss()
    }

    private fun showQuestionCardDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_question_card, null)
        val rvQuestionCard = dialogView.findViewById<RecyclerView>(R.id.rv_question_card)
        val btnCloseCard = dialogView.findViewById<Button>(R.id.btn_close_card)

        val adapter = QuestionCardAdapter(this, questionList.size, userAnswers, currentQuestionIndex, this)
        rvQuestionCard.layoutManager = GridLayoutManager(this, 5)
        rvQuestionCard.adapter = adapter

        btnCloseCard.setOnClickListener {
            questionCardDialog?.dismiss()
        }

        questionCardDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        questionCardDialog?.show()
    }

    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val switchNavButtons = dialogView.findViewById<SwitchCompat>(R.id.switch_show_nav_buttons)
        val switchAutoNext = dialogView.findViewById<SwitchCompat>(R.id.switch_auto_next)
        val btnDecreaseText = dialogView.findViewById<Button>(R.id.btn_decrease_font)
        val btnIncreaseText = dialogView.findViewById<Button>(R.id.btn_increase_font)
        val tvCurrentTextSize = dialogView.findViewById<TextView>(R.id.tv_font_size_indicator)

        switchNavButtons.isChecked = showNavigationButtons
        switchAutoNext.isChecked = autoNextQuestion
        tvCurrentTextSize.text = "%.1fx".format(currentTextSizeMultiplier)

        switchNavButtons.setOnCheckedChangeListener { _, isChecked ->
            showNavigationButtons = isChecked
            applyNavigationButtonVisibility()
        }
        switchAutoNext.setOnCheckedChangeListener { _, isChecked -> autoNextQuestion = isChecked }

        btnDecreaseText.setOnClickListener {
            if (currentTextSizeMultiplier > minTextSizeMultiplier) {
                currentTextSizeMultiplier -= textSizeStep
                applyTextSize()
                tvCurrentTextSize.text = "%.1fx".format(currentTextSizeMultiplier)
            }
        }
        btnIncreaseText.setOnClickListener {
            if (currentTextSizeMultiplier < maxTextSizeMultiplier) {
                currentTextSizeMultiplier += textSizeStep
                applyTextSize()
                tvCurrentTextSize.text = "%.1fx".format(currentTextSizeMultiplier)
            }
        }

        settingsDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ -> saveSettings() }
            .setNegativeButton("取消", null)
            .create()
        settingsDialog?.show()
    }

    private fun applyTextSize() {
        val newSize = resources.getDimension(R.dimen.default_text_size) * currentTextSizeMultiplier
        tvQuestion.textSize = newSize / resources.displayMetrics.scaledDensity
        for (i in 0 until llOptions.childCount) {
            val view = llOptions.getChildAt(i)
            if (view is RadioGroup) {
                for (j in 0 until view.childCount) {
                    (view.getChildAt(j) as? TextView)?.let { it.textSize = newSize / resources.displayMetrics.scaledDensity }
                }
            } else if (view is TextView) {
                view.textSize = newSize / resources.displayMetrics.scaledDensity
            }
        }
    }

    private fun applyNavigationButtonVisibility() {
        navigationButtonsLayout.visibility = if (showNavigationButtons) View.VISIBLE else View.GONE
    }

    private fun saveSettings() {
        sharedPreferences.edit().apply {
            putFloat("textSizeMultiplier", currentTextSizeMultiplier)
            putBoolean("showNavigationButtons", showNavigationButtons)
            putBoolean("autoNextQuestion", autoNextQuestion)
            apply()
        }
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
    }

    private fun loadSettings() {
        currentTextSizeMultiplier = sharedPreferences.getFloat("textSizeMultiplier", 1.0f)
        showNavigationButtons = sharedPreferences.getBoolean("showNavigationButtons", true)
        autoNextQuestion = sharedPreferences.getBoolean("autoNextQuestion", false)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
