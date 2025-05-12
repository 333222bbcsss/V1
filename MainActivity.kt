package com.yourname.myapplication

import android.app.AlertDialog
import android.content.ContentValues // Added for database operations
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException // Added for database exceptions
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
// Apache POI imports
import org.apache.poi.ss.usermodel.Cell // For Excel cell handling
import org.apache.poi.ss.usermodel.CellType // For Excel cell type handling
import org.apache.poi.ss.usermodel.WorkbookFactory // For handling both .xls and .xlsx
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.Locale // Added import for Locale

class MainActivity : AppCompatActivity() {
    private var spinnerSpecificMajor: Spinner? = null
    private var spinnerMajor: Spinner? = null
    private var etName: EditText? = null
    private var btnLogin: Button? = null
    private var tvQuestion: TextView? = null // Used for minor status updates

    private lateinit var layoutExamRecords: View
    private lateinit var tvRecordName: TextView
    private lateinit var tvRecordMajor: TextView
    private lateinit var tvRecordSpecificMajor: TextView
    private lateinit var tvRecordRateTotal: TextView
    private lateinit var tvRecordRateAnswered: TextView
    private lateinit var tvRecordTimestamp: TextView
    private lateinit var btnPreviousRecord: Button
    private lateinit var btnNextRecord: Button
    private lateinit var tvRecordInfo: TextView

    private var examRecordsList = mutableListOf<ExamRecord>()
    private var currentRecordIndex = 0

    private val questionList = mutableListOf<Question>()
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val ADMIN_PASSWORD = "admin123"
    private val APP_DEBUG_TAG = "AppDebug"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(APP_DEBUG_TAG, "MainActivity onCreate")

        initViews()
        setupMajorSpinner()
        loadExamRecords()
        displayExamRecord(currentRecordIndex)

        if (intent.getBooleanExtra("reset_form", false)) {
            resetLoginForm()
        }

        val btnAdmin = findViewById<Button>(R.id.btn_admin)
        btnAdmin.setOnClickListener {
            showAdminPasswordDialog()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d(APP_DEBUG_TAG, "MainActivity onNewIntent")
        if (intent?.getBooleanExtra("reset_form", false) == true) {
            resetLoginForm()
        }
        loadExamRecords()
        displayExamRecord(currentRecordIndex)
    }

    private fun resetLoginForm() {
        Log.d(APP_DEBUG_TAG, "resetLoginForm called")
        etName?.setText("")
        spinnerMajor?.setSelection(0)

        val initialSpecificMajors = mutableListOf("请先选择专业大类")
        val specificMajorAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, initialSpecificMajors)
        specificMajorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSpecificMajor?.adapter = specificMajorAdapter
        spinnerSpecificMajor?.setSelection(0)

        Toast.makeText(this, "登录信息已清空", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(APP_DEBUG_TAG, "MainActivity onDestroy")
        mainScope.cancel() // Cancel all coroutines started by this scope
    }

    private fun initViews() {
        Log.d(APP_DEBUG_TAG, "initViews called")
        spinnerSpecificMajor = findViewById(R.id.spinner_specific_major)
        spinnerMajor = findViewById(R.id.spinner_major)
        etName = findViewById(R.id.et_name)
        btnLogin = findViewById(R.id.btn_login)
        tvQuestion = findViewById(R.id.tv_question)

        layoutExamRecords = findViewById(R.id.layout_exam_records)
        tvRecordName = findViewById(R.id.tv_record_name)
        tvRecordMajor = findViewById(R.id.tv_record_major)
        tvRecordSpecificMajor = findViewById(R.id.tv_record_specific_major)
        tvRecordRateTotal = findViewById(R.id.tv_record_rate_total)
        tvRecordRateAnswered = findViewById(R.id.tv_record_rate_answered)
        tvRecordTimestamp = findViewById(R.id.tv_record_timestamp)
        btnPreviousRecord = findViewById(R.id.btn_previous_record)
        btnNextRecord = findViewById(R.id.btn_next_record)
        tvRecordInfo = findViewById(R.id.tv_record_info)

        btnLogin?.setOnClickListener {
            Log.d(APP_DEBUG_TAG, "Login button clicked")
            val name = etName?.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "请输入姓名", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedMajorFolder = spinnerMajor?.selectedItem?.toString()
            if (selectedMajorFolder == "请选择专业大类" || selectedMajorFolder == "无可用专业大类" || selectedMajorFolder == null) {
                Toast.makeText(this, "请选择专业大类", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedSpecificMajorFile = spinnerSpecificMajor?.selectedItem?.toString()
            if (selectedSpecificMajorFile == "请选择具体专业" || selectedSpecificMajorFile == "该大类下无具体专业" || selectedSpecificMajorFile == "请先选择专业大类" || selectedSpecificMajorFile == null) {
                Toast.makeText(this, "请选择具体专业", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dialogMessage = "请确认您的信息：\n姓名: $name\n专业大类: $selectedMajorFolder\n具体专业: $selectedSpecificMajorFile"
            Log.d(APP_DEBUG_TAG, "Showing confirmation dialog with message: $dialogMessage")

            AlertDialog.Builder(this)
                .setTitle("信息确认")
                .setMessage(dialogMessage)
                .setPositiveButton("确定") { dialog, _ ->
                    Log.d(APP_DEBUG_TAG, "Confirmation dialog: OK clicked")
                    mainScope.launch {
                        try {
                            Log.d(APP_DEBUG_TAG, "Starting import process...")
                            Toast.makeText(this@MainActivity, "正在导入题库...", Toast.LENGTH_SHORT).show()
                            btnLogin?.isEnabled = false
                            val importResult = withContext(Dispatchers.IO) {
                                Log.d(APP_DEBUG_TAG, "Inside Dispatchers.IO for importSpecificMajor")
                                importSpecificMajor(this@MainActivity, selectedMajorFolder, selectedSpecificMajorFile)?.let { importedCount ->
                                    Log.d(APP_DEBUG_TAG, "importSpecificMajor returned: $importedCount. Now loading questions from DB.")
                                    loadQuestionsFromDatabase(selectedSpecificMajorFile)
                                    Log.d(APP_DEBUG_TAG, "loadQuestionsFromDatabase finished. Question list size: ${questionList.size}")
                                    importedCount
                                }
                            }
                            Log.d(APP_DEBUG_TAG, "Import process finished. Result: $importResult")
                            btnLogin?.isEnabled = true
                            when (importResult) {
                                null -> {
                                    Log.w(APP_DEBUG_TAG, "Import result is null. Load failed or file not found.")
                                    Toast.makeText(this@MainActivity, "题库加载失败或文件不存在", Toast.LENGTH_LONG).show()
                                    tvQuestion?.text = "加载失败"
                                }
                                0 -> {
                                    Log.i(APP_DEBUG_TAG, "Import result is 0.")
                                    if (questionList.isNotEmpty()) {
                                        Log.i(APP_DEBUG_TAG, "Question list not empty, starting ExamActivity.")
                                        Toast.makeText(this@MainActivity, "题库已是最新或导入0条新题目，题目已加载", Toast.LENGTH_SHORT).show()
                                        val intent = Intent(this@MainActivity, ExamActivity::class.java)
                                        intent.putExtra("name", name)
                                        intent.putExtra("major", selectedMajorFolder)
                                        intent.putExtra("specificMajor", selectedSpecificMajorFile)
                                        startActivity(intent)
                                    } else {
                                        Log.w(APP_DEBUG_TAG, "Question list is empty after importResult 0.")
                                        Toast.makeText(this@MainActivity, "未找到与 $selectedSpecificMajorFile 匹配的题目，或题库文件为空/格式错误", Toast.LENGTH_LONG).show()
                                        tvQuestion?.text = "无题目"
                                    }
                                }
                                else -> {
                                    Log.i(APP_DEBUG_TAG, "Import result: $importResult.")
                                    if (questionList.isNotEmpty()) {
                                        Log.i(APP_DEBUG_TAG, "Question list not empty, starting ExamActivity.")
                                        Toast.makeText(this@MainActivity, "成功导入 $importResult 条题目", Toast.LENGTH_LONG).show()
                                        val intent = Intent(this@MainActivity, ExamActivity::class.java)
                                        intent.putExtra("name", name)
                                        intent.putExtra("major", selectedMajorFolder)
                                        intent.putExtra("specificMajor", selectedSpecificMajorFile)
                                        startActivity(intent)
                                    } else {
                                        Log.w(APP_DEBUG_TAG, "Question list is empty after importResult $importResult.")
                                        Toast.makeText(this@MainActivity, "导入成功但加载题目列表失败 for $selectedSpecificMajorFile", Toast.LENGTH_LONG).show()
                                        tvQuestion?.text = "无题目"
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(APP_DEBUG_TAG, "Exception in PositiveButton onClick coroutine: ${e.message}", e)
                            Toast.makeText(this@MainActivity, "发生未知错误，请检查日志: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            btnLogin?.isEnabled = true // Re-enable button on error
                        }
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("取消") { dialog, _ ->
                    Log.d(APP_DEBUG_TAG, "Confirmation dialog: Cancel clicked")
                    dialog.dismiss()
                }
                .setCancelable(false)
                .create()
                .show()
        }

        btnPreviousRecord.setOnClickListener {
            if (currentRecordIndex > 0) {
                currentRecordIndex--
                displayExamRecord(currentRecordIndex)
            }
        }

        btnNextRecord.setOnClickListener {
            if (currentRecordIndex < examRecordsList.size - 1) {
                currentRecordIndex++
                displayExamRecord(currentRecordIndex)
            }
        }
    }

    private fun getAssetFolderNames(): List<String> {
        val folderList = mutableListOf<String>()
        try {
            assets.list("")?.forEach { entry ->
                if (assets.list(entry) != null) {
                    if (entry != "images" && entry != "sounds" && entry != "webkit" && !entry.startsWith("android_wear_micro_apk") && entry != "META-INF") {
                        folderList.add(entry)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("MainActivity", "Error listing asset folders: ${e.message}", e)
            // Toast.makeText(this, "获取专业大类列表出错", Toast.LENGTH_SHORT).show() // This Toast is on UI thread, so it's fine
        }
        if (folderList.isEmpty()) {
            Log.w("MainActivity", "No major folders (directories) found in assets root.")
        }
        return folderList.distinct()
    }

    private fun setupMajorSpinner() {
        val majors = getAssetFolderNames().toMutableList()
        val initialSpecificMajors = mutableListOf<String>()

        if (majors.isEmpty()) {
            majors.add(0, "无可用专业大类")
            initialSpecificMajors.add(0, "无可用具体专业")
        } else {
            majors.add(0, "请选择专业大类")
            initialSpecificMajors.add(0, "请先选择专业大类")
        }

        val majorAdapter = ArrayAdapter(this, R.layout.simple_spinner_item, majors)
        majorAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        spinnerMajor?.adapter = majorAdapter

        val specificMajorAdapter = ArrayAdapter(this, R.layout.simple_spinner_item, initialSpecificMajors)
        specificMajorAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        spinnerSpecificMajor?.adapter = specificMajorAdapter
        spinnerSpecificMajor?.visibility = View.VISIBLE

        spinnerMajor?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedMajorFolderName = parent?.getItemAtPosition(position).toString()
                val currentSpecificMajors = mutableListOf<String>()

                if (selectedMajorFolderName != "请选择专业大类" && selectedMajorFolderName != "无可用专业大类") {
                    val files = getExcelFilesFromAssets(selectedMajorFolderName)
                    if (files.isEmpty()) {
                        currentSpecificMajors.add("该大类下无具体专业")
                    } else {
                        currentSpecificMajors.add(0, "请选择具体专业")
                        currentSpecificMajors.addAll(files)
                    }
                } else {
                    currentSpecificMajors.add("请先选择专业大类")
                }
                val newSpecificMajorAdapter = ArrayAdapter(this@MainActivity, R.layout.simple_spinner_item, currentSpecificMajors)
                newSpecificMajorAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
                spinnerSpecificMajor?.adapter = newSpecificMajorAdapter
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                val emptySpecific = mutableListOf("请先选择专业大类")
                val newSpecificMajorAdapter = ArrayAdapter(this@MainActivity, R.layout.simple_spinner_item, emptySpecific)
                newSpecificMajorAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
                spinnerSpecificMajor?.adapter = newSpecificMajorAdapter
            }
        }
    }

    private fun loadExamRecords() {
        val sharedPreferences = getSharedPreferences("ExamRecordsPrefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val jsonRecords = sharedPreferences.getString("records", null)
        val type = object : TypeToken<MutableList<ExamRecord>>() {}.type
        examRecordsList = gson.fromJson(jsonRecords, type) ?: mutableListOf()
        if (currentRecordIndex >= examRecordsList.size) {
            currentRecordIndex = if (examRecordsList.isNotEmpty()) examRecordsList.size - 1 else 0
        }
        if (examRecordsList.isEmpty()) currentRecordIndex = 0
    }

    private fun displayExamRecord(index: Int) {
        if (examRecordsList.isNotEmpty() && index >= 0 && index < examRecordsList.size) {
            layoutExamRecords.visibility = View.VISIBLE
            val record = examRecordsList[index]
            tvRecordName.text = "姓名: ${record.name ?: "N/A"}"
            tvRecordMajor.text = "考试专业: ${record.major ?: "N/A"}"
            tvRecordSpecificMajor.text = "具体专业: ${record.specificMajor ?: "N/A"}"
            tvRecordRateTotal.text = record.correctRateBasedOnTotal ?: "N/A"
            tvRecordRateAnswered.text = record.correctRateBasedOnAnswered ?: "N/A"
            tvRecordTimestamp.text = "交卷时间: ${record.submissionTimestamp}"
            tvRecordInfo.text = "记录 ${index + 1} / ${examRecordsList.size}"
            btnPreviousRecord.isEnabled = index > 0
            btnNextRecord.isEnabled = index < examRecordsList.size - 1
        } else {
            layoutExamRecords.visibility = View.GONE
            tvRecordInfo.text = "无答题记录"
            btnPreviousRecord.isEnabled = false
            btnNextRecord.isEnabled = false
        }
    }

    private fun getExcelFilesFromAssets(folderName: String): MutableList<String> {
        val fileList = mutableListOf<String>()
        try {
            assets.list(folderName)?.forEach { file ->
                if (file.endsWith(".xls", ignoreCase = true) || file.endsWith(".xlsx", ignoreCase = true)) {
                    fileList.add(file.substringBeforeLast("."))
                }
            }
        } catch (e: IOException) {
            // Toast.makeText(this, "获取 $folderName 内文件列表出错: ${e.message}", Toast.LENGTH_SHORT).show() // This Toast is on UI thread, so it's fine
            Log.e("MainActivity", "Error getting file list from assets/$folderName: ${e.message}", e)
        }
        return fileList
    }

    private fun loadQuestionsFromDatabase(specificMajorName: String) {
        Log.d(APP_DEBUG_TAG, "loadQuestionsFromDatabase called for: $specificMajorName")
        val dbHelper = ExamDbHelper(this)
        val db: SQLiteDatabase = dbHelper.readableDatabase
        Log.d(APP_DEBUG_TAG, "Database opened for $specificMajorName")

        val projection = arrayOf(
            ExamDbHelper.COLUMN_ID, ExamDbHelper.COLUMN_QUESTION, ExamDbHelper.COLUMN_OPTION_A,
            ExamDbHelper.COLUMN_OPTION_B, ExamDbHelper.COLUMN_OPTION_C, ExamDbHelper.COLUMN_OPTION_D,
            ExamDbHelper.COLUMN_OPTION_E, ExamDbHelper.COLUMN_OPTION_F, ExamDbHelper.COLUMN_OPTION_G,
            ExamDbHelper.COLUMN_OPTION_H, ExamDbHelper.COLUMN_CORRECT_ANSWER,
            ExamDbHelper.COLUMN_ACTUAL_QUESTION_TYPE, ExamDbHelper.COLUMN_QUESTION_TYPE
        )
        val selection = "${ExamDbHelper.COLUMN_QUESTION_TYPE} = ?"
        val selectionArgs = arrayOf(specificMajorName)
        questionList.clear()
        Log.d(APP_DEBUG_TAG, "Question list cleared before querying DB.")
        try {
            db.query(ExamDbHelper.TABLE_QUESTIONS, projection, selection, selectionArgs, null, null, null)?.use { cursor ->
                Log.d(APP_DEBUG_TAG, "Query for $specificMajorName returned ${cursor.count} rows.")
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
                        else -> {
                            Log.w(APP_DEBUG_TAG, "Unknown question type string: '$actualQuestionTypeStr', defaulting to SINGLE_CHOICE for question ID $id")
                            QuestionType.SINGLE_CHOICE
                        }
                    }
                    questionList.add(Question(id, questionText, optionA, optionB, optionC, optionD, optionE, optionF, optionG, optionH, correctAnswer, questionTypeEnum, actualQuestionTypeStr))
                }
                Log.d(APP_DEBUG_TAG, "Finished populating questionList. Size: ${questionList.size}")
            }
        } catch (e: SQLiteException) {
            Log.e(APP_DEBUG_TAG, "Database query error for $specificMajorName: ${e.message}", e)
            // Toast.makeText(this, "查询题库出错: ${e.message}", Toast.LENGTH_LONG).show() // This Toast is on UI thread, so it's fine
        } finally {
            db.close()
            Log.d(APP_DEBUG_TAG, "Database closed for $specificMajorName")
        }
    }

    private fun showAdminPasswordDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("管理员操作")
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.hint = "请输入管理员密码"
        builder.setView(input)
        builder.setPositiveButton("确认") { dialog, _ ->
            val password = input.text.toString()
            if (password == ADMIN_PASSWORD) {
                showAdminOptionsDialog()
            } else {
                Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("取消") { dialog, _ -> dialog.cancel() }
        builder.create().show()
    }

    private fun showAdminOptionsDialog() {
        val options = arrayOf("进入管理员设置", "从Assets导入题库", "清空所有题目", "清空所有答题记录") // MODIFIED: Added new option at index 0
        AlertDialog.Builder(this)
            .setTitle("管理员选项")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> { // MODIFIED: Handle new option
                        // 跳转到 AdminActivity
                        val intent = Intent(this, AdminActivity::class.java)
                        startActivity(intent)
                    }
                    1 -> showMajorSelectionForImportDialog() // MODIFIED: Index shifted
                    2 -> confirmClearAllQuestions()       // MODIFIED: Index shifted
                    3 -> confirmClearAllExamRecords()    // MODIFIED: Index shifted
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .create().show()
    }

    private fun showMajorSelectionForImportDialog() {
        val majorFolders = getAssetFolderNames()
        if (majorFolders.isEmpty()) {
            Toast.makeText(this, "Assets中没有找到专业大类文件夹", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("选择要导入的专业大类")
            .setItems(majorFolders.toTypedArray()) { dialog, which ->
                val selectedMajorFolder = majorFolders[which]
                showSpecificMajorSelectionForImportDialog(selectedMajorFolder)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .create().show()
    }

    private fun showSpecificMajorSelectionForImportDialog(majorFolder: String) {
        val specificMajorFiles = getExcelFilesFromAssets(majorFolder)
        if (specificMajorFiles.isEmpty()) {
            Toast.makeText(this, "专业大类 '$majorFolder' 下没有找到具体的专业题库文件 (Excel)", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("选择要导入的具体专业题库 ($majorFolder)")
            .setItems(specificMajorFiles.toTypedArray()) { dialog, which ->
                val selectedSpecificMajorFile = specificMajorFiles[which]
                mainScope.launch {
                    try {
                        Log.d(APP_DEBUG_TAG, "Admin import: Starting import for $selectedSpecificMajorFile from $majorFolder")
                        // Toast on Main thread before switching to IO
                        Toast.makeText(this@MainActivity, "正在从Assets导入 $selectedSpecificMajorFile...", Toast.LENGTH_LONG).show()
                        val importedCount = withContext(Dispatchers.IO) {
                            importSpecificMajor(this@MainActivity, majorFolder, selectedSpecificMajorFile, true)
                        }
                        Log.d(APP_DEBUG_TAG, "Admin import: Import result for $selectedSpecificMajorFile: $importedCount")
                        // Toast on Main thread after returning from IO
                        if (importedCount != null && importedCount > 0) {
                            Toast.makeText(this@MainActivity, "成功从Assets导入 $importedCount 条题目到 $selectedSpecificMajorFile", Toast.LENGTH_LONG).show()
                        } else if (importedCount == 0) {
                            Toast.makeText(this@MainActivity, "题库 $selectedSpecificMajorFile 已是最新或Excel文件为空/格式错误", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@MainActivity, "从Assets导入 $selectedSpecificMajorFile 失败", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Log.e(APP_DEBUG_TAG, "Exception during admin import for $selectedSpecificMajorFile: ${e.message}", e)
                        Toast.makeText(this@MainActivity, "管理员导入 $selectedSpecificMajorFile 发生错误: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .create().show()
    }

    private fun confirmClearAllQuestions() {
        AlertDialog.Builder(this)
            .setTitle("确认操作")
            .setMessage("确定要清空数据库中所有题目吗？此操作不可恢复！")
            .setPositiveButton("清空所有题目") { dialog, _ ->
                clearAllQuestionsFromDb()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .create().show()
    }

    private fun clearAllQuestionsFromDb() {
        val dbHelper = ExamDbHelper(this)
        val db = dbHelper.writableDatabase
        try {
            val deletedRows = db.delete(ExamDbHelper.TABLE_QUESTIONS, null, null)
            Toast.makeText(this, "成功删除 $deletedRows 条题目", Toast.LENGTH_LONG).show()
            Log.d(APP_DEBUG_TAG, "Cleared all questions from DB, rows affected: $deletedRows")
            setupMajorSpinner() // Refresh spinners as available majors might change if DB is empty
        } catch (e: SQLiteException) {
            Toast.makeText(this, "清空题目失败: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(APP_DEBUG_TAG, "Error clearing questions: ${e.message}", e)
        } finally {
            db.close()
        }
    }

    private fun confirmClearAllExamRecords() {
        AlertDialog.Builder(this)
            .setTitle("确认操作")
            .setMessage("确定要清空所有本地保存的答题记录吗？此操作不可恢复！")
            .setPositiveButton("清空记录") { dialog, _ ->
                clearAllExamRecordsFromPrefs()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .create().show()
    }

    private fun clearAllExamRecordsFromPrefs() {
        val sharedPreferences = getSharedPreferences("ExamRecordsPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().remove("records").apply()
        examRecordsList.clear()
        currentRecordIndex = 0
        displayExamRecord(currentRecordIndex) // Update UI
        Toast.makeText(this, "所有答题记录已清空", Toast.LENGTH_LONG).show()
        Log.d(APP_DEBUG_TAG, "Cleared all exam records from SharedPreferences.")
    }

    companion object {
        private const val COMPANION_DEBUG_TAG = "MainActivityCompanion"
        // Helper function to show Toast on the main thread from a background thread
        private suspend fun showToastOnMainThread(context: Context, message: String, duration: Int) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, message, duration).show()
            }
        }

        fun importSpecificMajor(context: Context, majorFolder: String, specificMajorName: String, forceImportFromAssets: Boolean = false): Int? {
            Log.d(COMPANION_DEBUG_TAG, "importSpecificMajor called for $specificMajorName in $majorFolder. Force: $forceImportFromAssets")
            val dbHelper = ExamDbHelper(context)
            val db = dbHelper.writableDatabase
            var importedCount = 0
            var stream: InputStream? = null

            try {
                val assetPathXlsx = "$majorFolder/$specificMajorName.xlsx"
                val assetPathXls = "$majorFolder/$specificMajorName.xls"
                Log.d(COMPANION_DEBUG_TAG, "Attempting to import from asset: $assetPathXlsx or $assetPathXls")
                try {
                    stream = context.assets.open(assetPathXlsx)
                    Log.d(COMPANION_DEBUG_TAG, "Opened .xlsx asset: $assetPathXlsx")
                } catch (e: FileNotFoundException) {
                    Log.d(COMPANION_DEBUG_TAG, "Asset file not found: $assetPathXlsx. Trying .xls next.")
                    try {
                        stream = context.assets.open(assetPathXls)
                        Log.d(COMPANION_DEBUG_TAG, "Opened .xls asset: $assetPathXls")
                    } catch (e2: FileNotFoundException) {
                        Log.e(COMPANION_DEBUG_TAG, "Asset file not found (both .xlsx and .xls): $majorFolder/$specificMajorName")
                        return null // File not found
                    }
                }

                val workbook = WorkbookFactory.create(stream)
                val sheet = workbook.getSheetAt(0)
                if (sheet == null || sheet.lastRowNum < 0) {
                    Log.w(COMPANION_DEBUG_TAG, "Excel sheet is null or empty for $specificMajorName")
                    return 0 // Empty sheet
                }
                val headerRow = sheet.getRow(0)
                if (headerRow == null) {
                    Log.w(COMPANION_DEBUG_TAG, "Excel header row is null for $specificMajorName")
                    return 0 // No header row
                }
                val columnMap = mutableMapOf<String, Int>()
                headerRow.forEach { cell ->
                    cell.stringCellValue?.trim()?.let { headerName ->
                        columnMap[headerName] = cell.columnIndex
                    }
                }
                Log.d(COMPANION_DEBUG_TAG, "Column map for $specificMajorName: $columnMap")

                val requiredHeaders = listOf("题目", "选项A", "选项B", "正确答案", "题目类型")
                if (!columnMap.keys.containsAll(requiredHeaders)) {
                    Log.e(COMPANION_DEBUG_TAG, "Excel missing required headers. Found: ${columnMap.keys}. Required: $requiredHeaders")
                    // Use runBlocking for suspend function call from non-coroutine context if necessary, or make calling context suspend
                    // For simplicity here, assuming this function is called from a coroutine context that can call suspend functions.
                    // If not, this Toast needs to be handled differently (e.g., by returning a status to the caller).
                    // For now, we'll wrap it to ensure it's on the main thread if called from a coroutine.
                    runBlocking(Dispatchers.Main) { // This is a quick fix, ideally the caller handles UI.
                        Toast.makeText(context, "Excel文件 '$specificMajorName' 缺少必要的表头 (如 题目, 选项A, 选项B, 正确答案, 题目类型)", Toast.LENGTH_LONG).show()
                    }
                    return 0
                }

                db.beginTransaction()
                Log.d(COMPANION_DEBUG_TAG, "Begin transaction for DB import of $specificMajorName")
                try {
                    if (forceImportFromAssets) {
                        val deleteSelection = "${ExamDbHelper.COLUMN_QUESTION_TYPE} = ?"
                        val deleteSelectionArgs = arrayOf(specificMajorName)
                        val deletedRows = db.delete(ExamDbHelper.TABLE_QUESTIONS, deleteSelection, deleteSelectionArgs)
                        Log.d(COMPANION_DEBUG_TAG, "Force import: Deleted $deletedRows existing questions for '$specificMajorName' before import.")
                    }

                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val questionTextCell = row.getCell(columnMap["题目"]!!)
                        val questionText = questionTextCell?.stringCellValue?.trim() ?: ""
                        if (questionText.isEmpty()) {
                            Log.v(COMPANION_DEBUG_TAG, "Skipping row $i due to empty question text.")
                            continue
                        }

                        if (!forceImportFromAssets) {
                            val checkCursor = db.query(
                                ExamDbHelper.TABLE_QUESTIONS,
                                arrayOf(ExamDbHelper.COLUMN_ID),
                                "${ExamDbHelper.COLUMN_QUESTION} = ? AND ${ExamDbHelper.COLUMN_QUESTION_TYPE} = ?",
                                arrayOf(questionText, specificMajorName),
                                null, null, null, "1"
                            )
                            val exists = checkCursor.count > 0
                            checkCursor.close()
                            if (exists) {
                                Log.d(COMPANION_DEBUG_TAG, "Question already exists, skipping: ${questionText.take(30)} for $specificMajorName")
                                continue
                            }
                        }

                        val values = ContentValues().apply {
                            put(ExamDbHelper.COLUMN_QUESTION, questionText)
                            put(ExamDbHelper.COLUMN_OPTION_A, row.getCell(columnMap["选项A"]!!)?.stringCellValue?.trim() ?: "")
                            put(ExamDbHelper.COLUMN_OPTION_B, row.getCell(columnMap["选项B"]!!)?.stringCellValue?.trim() ?: "")
                            put(ExamDbHelper.COLUMN_OPTION_C, row.getCell(columnMap["选项C"]!!)?.stringCellValue?.trim() ?: "")
                            put(ExamDbHelper.COLUMN_OPTION_D, row.getCell(columnMap["选项D"]!!)?.stringCellValue?.trim() ?: "")
                            put(ExamDbHelper.COLUMN_OPTION_E, row.getCell(columnMap["选项E"]!!)?.stringCellValue?.trim() ?: "")
                            put(ExamDbHelper.COLUMN_OPTION_F, row.getCell(columnMap["选项F"]!!)?.stringCellValue?.trim() ?: "")
                            put(ExamDbHelper.COLUMN_OPTION_G, row.getCell(columnMap["选项G"]!!)?.stringCellValue?.trim() ?: "")
                            put(ExamDbHelper.COLUMN_OPTION_H, row.getCell(columnMap["选项H"]!!)?.stringCellValue?.trim() ?: "")
                            put(ExamDbHelper.COLUMN_CORRECT_ANSWER, row.getCell(columnMap["正确答案"]!!)?.stringCellValue?.trim()?.toUpperCase(Locale.ROOT) ?: "")
                            put(ExamDbHelper.COLUMN_ACTUAL_QUESTION_TYPE, row.getCell(columnMap["题目类型"]!!)?.stringCellValue?.trim() ?: "单选题")
                            put(ExamDbHelper.COLUMN_QUESTION_TYPE, specificMajorName) // This is the specific major name, used for filtering
                        }
                        db.insert(ExamDbHelper.TABLE_QUESTIONS, null, values)
                        importedCount++
                    }
                    db.setTransactionSuccessful()
                    Log.d(COMPANION_DEBUG_TAG, "DB transaction successful for $specificMajorName. Imported $importedCount new questions.")
                } finally {
                    db.endTransaction()
                    Log.d(COMPANION_DEBUG_TAG, "End transaction for DB import of $specificMajorName")
                }
            } catch (e: Exception) {
                Log.e(COMPANION_DEBUG_TAG, "Error importing questions for '$specificMajorName' from '$majorFolder': ${e.message}", e)
                // Ensure Toast is on main thread
                runBlocking(Dispatchers.Main) { // This is a quick fix, ideally the caller handles UI.
                    Toast.makeText(context, "导入题库 '$specificMajorName' 出错: ${e.message}", Toast.LENGTH_LONG).show()
                }
                return if (importedCount > 0) importedCount else 0 // Return count if some were imported before error, else 0
            } finally {
                try {
                    stream?.close()
                } catch (ioe: IOException) {
                    Log.w(COMPANION_DEBUG_TAG, "Error closing input stream: ${ioe.message}", ioe)
                }
                db.close()
                Log.d(COMPANION_DEBUG_TAG, "DB closed for $specificMajorName import process.")
            }
            Log.i(COMPANION_DEBUG_TAG, "Successfully imported $importedCount questions for '$specificMajorName' from '$majorFolder'")
            return importedCount
        }
    }
}

// Data classes (assuming they are defined elsewhere or here if not)
// data class Question(val id: Int, val question: String, ... val questionTypeEnum: QuestionType, val actualQuestionType: String)
// data class ExamRecord(val name: String?, ...)
// enum class QuestionType { SINGLE_CHOICE, MULTI_CHOICE, JUDGMENT }

