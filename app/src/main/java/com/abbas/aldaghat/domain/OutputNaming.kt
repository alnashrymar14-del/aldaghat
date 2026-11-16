package com.abbas.aldaghat.domain

/**
 * Naming rules for the produced file: never overwrite the source, always include the
 * `_compressed` marker and always end in `.mp4` because the output container is MP4.
 */
object OutputNaming {
    const val SUFFIX = "_compressed"
    const val EXTENSION = "mp4"
    private const val FALLBACK_BASE = "video"

    private val illegalCharacters = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

    fun compressedBaseName(sourceDisplayName: String): String {
        val withoutExtension = sourceDisplayName.substringBeforeLast('.', sourceDisplayName)
        val sanitized = withoutExtension
            .replace(illegalCharacters, "_")
            .trim()
            .trimEnd('.')
            .take(120)
        val base = sanitized.ifEmpty { FALLBACK_BASE }
        return if (base.endsWith(SUFFIX, ignoreCase = true)) base else base + SUFFIX
    }

    /** index 0 → `name_compressed.mp4`, index 1 → `name_compressed (1).mp4` … */
    fun fileName(base: String, index: Int = 0): String =
        if (index <= 0) "$base.$EXTENSION" else "$base ($index).$EXTENSION"
}
