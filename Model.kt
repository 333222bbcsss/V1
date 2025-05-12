package com.yourname.myapplication

import android.os.Parcel
import android.os.Parcelable

// 题目类型枚举类
enum class QuestionType {
    SINGLE_CHOICE, MULTI_CHOICE, JUDGMENT
}

// Question 数据类
data class Question(
    val id: Int,
    val question: String,
    val optionA: String?,
    val optionB: String?,
    val optionC: String?,
    val optionD: String?,
    val optionE: String?,
    val optionF: String?,
    val optionG: String?,
    val optionH: String?,
    val correctAnswer: String,
    val questionType: QuestionType,
    val actualQuestionType: String // e.g., "单选题", "多选题", "判断题"
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString() ?: "",
        QuestionType.valueOf(parcel.readString() ?: QuestionType.SINGLE_CHOICE.name),
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(question)
        parcel.writeString(optionA)
        parcel.writeString(optionB)
        parcel.writeString(optionC)
        parcel.writeString(optionD)
        parcel.writeString(optionE)
        parcel.writeString(optionF)
        parcel.writeString(optionG)
        parcel.writeString(optionH)
        parcel.writeString(correctAnswer)
        parcel.writeString(questionType.name)
        parcel.writeString(actualQuestionType)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Question> {
        override fun createFromParcel(parcel: Parcel): Question {
            return Question(parcel)
        }

        override fun newArray(size: Int): Array<Question?> {
            return arrayOfNulls(size)
        }
    }
}
