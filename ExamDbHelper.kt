package com.yourname.myapplication

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class ExamDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 2
        private const val DATABASE_NAME = "ExamDatabase.db"
        const val TABLE_QUESTIONS = "questions"
        const val COLUMN_ID = "_id"
        const val COLUMN_QUESTION = "question"
        const val COLUMN_OPTION_A = "option_a"
        const val COLUMN_OPTION_B = "option_b"
        const val COLUMN_OPTION_C = "option_c"
        const val COLUMN_OPTION_D = "option_d"
        const val COLUMN_OPTION_E = "option_e"
        const val COLUMN_OPTION_F = "option_f"
        const val COLUMN_OPTION_G = "option_g"
        const val COLUMN_OPTION_H = "option_h"
        const val COLUMN_CORRECT_ANSWER = "correct_answer"
        const val COLUMN_QUESTION_TYPE = "question_type" // This is specificMajor
        const val COLUMN_ACTUAL_QUESTION_TYPE = "actual_question_type" // e.g., "单选题", "多选题"
        const val COLUMN_USER_ANSWER = "user_answer"
        const val COLUMN_IS_CORRECT = "is_correct"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTableQuery = "CREATE TABLE $TABLE_QUESTIONS (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "$COLUMN_QUESTION TEXT," +
                "$COLUMN_OPTION_A TEXT," +
                "$COLUMN_OPTION_B TEXT," +
                "$COLUMN_OPTION_C TEXT," +
                "$COLUMN_OPTION_D TEXT," +
                "$COLUMN_OPTION_E TEXT," +
                "$COLUMN_OPTION_F TEXT," +
                "$COLUMN_OPTION_G TEXT," +
                "$COLUMN_OPTION_H TEXT," +
                "$COLUMN_CORRECT_ANSWER TEXT," +
                "$COLUMN_QUESTION_TYPE TEXT," +
                "$COLUMN_ACTUAL_QUESTION_TYPE TEXT," +
                "$COLUMN_USER_ANSWER TEXT DEFAULT ''," +
                "$COLUMN_IS_CORRECT INTEGER DEFAULT 0" +
                ")"
        db?.execSQL(createTableQuery)
        Log.d("ExamDbHelper", "Database table $TABLE_QUESTIONS created.")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db?.execSQL("ALTER TABLE $TABLE_QUESTIONS ADD COLUMN $COLUMN_USER_ANSWER TEXT DEFAULT ''")
            db?.execSQL("ALTER TABLE $TABLE_QUESTIONS ADD COLUMN $COLUMN_IS_CORRECT INTEGER DEFAULT 0")
            Log.d("ExamDbHelper", "Database upgraded to version 2. Added COLUMN_USER_ANSWER and COLUMN_IS_CORRECT.")
        }
    }

    fun clearQuestionsByType(questionType: String) {
        val db = this.writableDatabase
        try {
            val deletedRows = db.delete(TABLE_QUESTIONS, "$COLUMN_QUESTION_TYPE = ?", arrayOf(questionType))
            Log.d("ExamDbHelper", "Deleted $deletedRows rows for question type: $questionType")
        } catch (e: Exception) {
            Log.e("ExamDbHelper", "Error clearing questions for type $questionType: ${e.message}")
        } finally {
            db.close()
        }
    }
}
