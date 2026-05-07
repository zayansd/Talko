package com.talko.app.domain.usecase

import com.talko.app.domain.model.Message
import javax.inject.Inject

data class AiAssistResult(
    val summary: String,
    val quickReplies: List<String>,
)

class GenerateAiSuggestionsUseCase @Inject constructor() {
    operator fun invoke(messages: List<Message>): AiAssistResult {
        val last = messages.takeLast(4)
        val summary = if (last.isEmpty()) {
            "No conversation summary yet."
        } else {
            "Conversation Summary: " + last.joinToString(" ") { it.content }.take(140)
        }
        val context = last.lastOrNull()?.content?.lowercase().orEmpty()
        val replies = when {
            "pm" in context -> listOf("Sounds good!", "On it!", "Can we talk later?")
            "review" in context -> listOf("I checked it.", "Will send update.", "Let's finalize.")
            else -> listOf("Sounds good!", "On it!", "Thanks!")
        }
        return AiAssistResult(summary, replies)
    }
}
