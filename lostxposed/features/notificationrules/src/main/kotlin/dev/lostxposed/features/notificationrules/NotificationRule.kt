package dev.lostxposed.features.notificationrules

import android.app.Notification
import android.os.Bundle
import dev.lostxposed.core.api.ConfigSource

/**
 * What to do with one package's notifications.
 *
 * Keyword matching is the reason this feature exists at all. Android's own channels already
 * give coarse per-category control, and a NotificationListener can dismiss things after the
 * fact, but neither can suppress *before display*, and neither can match on content. Only a
 * hook inside NotificationManagerService can.
 */
data class NotificationRule(
    val blockAll: Boolean = false,
    val keywords: List<String> = emptyList(),
    val caseSensitive: Boolean = false,
) {
    val isActive: Boolean get() = blockAll || keywords.isNotEmpty()

    fun blocks(notification: Notification?): Boolean {
        if (blockAll) return true
        if (keywords.isEmpty() || notification == null) return false
        return matches(textOf(notification.extras))
    }

    /**
     * The decision itself, over already-extracted text. Separate from [blocks] so the matching
     * rules can be tested without an Android runtime. This runs inside
     * NotificationManagerService, where getting it wrong means a notification the user needed
     * is silently gone.
     */
    fun matches(fields: List<String>): Boolean = keywords.any { needle ->
        fields.any { field -> field.contains(needle, ignoreCase = !caseSensitive) }
    }

    private fun textOf(extras: Bundle?): List<String> {
        if (extras == null) return emptyList()
        return TEXT_KEYS.mapNotNull {
            runCatching { extras.getCharSequence(it)?.toString() }.getOrNull()
        }.filter { it.isNotBlank() }
    }

    fun describe(): String = buildList {
        if (blockAll) add("block all")
        if (keywords.isNotEmpty()) add("keywords=${keywords.joinToString("/")}")
    }.joinToString(", ").ifEmpty { "inactive" }

    companion object {
        const val KEY_BLOCK_ALL = "blockAll"
        const val KEY_KEYWORDS = "keywords"
        const val KEY_CASE_SENSITIVE = "caseSensitive"

        private val TEXT_KEYS = listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
        )

        fun from(config: ConfigSource) = NotificationRule(
            blockAll = config.boolean(KEY_BLOCK_ALL, false),
            keywords = config.string(KEY_KEYWORDS)
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty(),
            caseSensitive = config.boolean(KEY_CASE_SENSITIVE, false),
        )
    }
}
