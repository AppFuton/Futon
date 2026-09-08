package io.github.landwarderer.futon.core.prefs

enum class ChapterCompletionMode {
    PERCENTAGE,
    PAGES_REMAINING;

    companion object {
        fun from(value: String?): ChapterCompletionMode = entries.find { it.name == value } ?: PERCENTAGE
    }
}
