package com.yourname.myapplication

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

// Interface for click events on question numbers in the card
interface OnQuestionClickListener {
    fun onQuestionClick(questionIndex: Int)
}

class QuestionCardAdapter(
    private val context: Context,
    private val totalQuestions: Int,
    private val userAnswers: List<List<Int>>, // List of lists of selected option indices for each question
    private val currentQuestionDisplayIndex: Int, // To highlight the current question
    private val listener: OnQuestionClickListener
) : RecyclerView.Adapter<QuestionCardAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val questionButton: Button = view.findViewById(R.id.btn_question_number) // Ensure R.id.btn_question_number exists in item layout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Ensure R.layout.dialog_question_card_item exists
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_question_card_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val questionNumber = position + 1 // Display number (1-based)
        holder.questionButton.text = questionNumber.toString()

        val isAnswered = userAnswers.getOrNull(position)?.isNotEmpty() == true
        val isCurrent = position == currentQuestionDisplayIndex

        // Set button background and text color based on answered status and if it's current
        // User needs to define these drawables: e.g., circle_background_current, circle_background_answered, circle_background_unanswered
        if (isCurrent) {
            holder.questionButton.background = ContextCompat.getDrawable(context, R.drawable.circle_background_current) // Example: a distinct drawable for current
            holder.questionButton.setTextColor(ContextCompat.getColor(context, android.R.color.white)) // Example: white text for current
        } else if (isAnswered) {
            holder.questionButton.background = ContextCompat.getDrawable(context, R.drawable.circle_background_answered)
            holder.questionButton.setTextColor(ContextCompat.getColor(context, android.R.color.black))
        } else {
            holder.questionButton.background = ContextCompat.getDrawable(context, R.drawable.circle_background_unanswered)
            holder.questionButton.setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }

        holder.questionButton.setOnClickListener {
            listener.onQuestionClick(position) // Pass the adapter position (0-based index)
        }
    }

    override fun getItemCount(): Int {
        return totalQuestions
    }
}
