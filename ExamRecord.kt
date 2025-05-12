package com.yourname.myapplication

import android.os.Parcel
import android.os.Parcelable

data class ExamRecord(
    val name: String?,
    val major: String?,
    val specificMajor: String?,
    val correctRateBasedOnTotal: String?,
    val correctRateBasedOnAnswered: String?,
    val submissionTimestamp: String // e.g., "2025-05-07 12:30:00"
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(major)
        parcel.writeString(specificMajor)
        parcel.writeString(correctRateBasedOnTotal)
        parcel.writeString(correctRateBasedOnAnswered)
        parcel.writeString(submissionTimestamp)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ExamRecord> {
        override fun createFromParcel(parcel: Parcel): ExamRecord {
            return ExamRecord(parcel)
        }

        override fun newArray(size: Int): Array<ExamRecord?> {
            return arrayOfNulls(size)
        }
    }
}
