package com.aventure.messcores

/**
 * Saisie de la durée du minuteur : « 5 » = 5 minutes, « 4:30 » = 4 min 30 s, « 2:05:30 » = 2 h 05 min 30 s.
 * Les touches « . » et « , » (présentes sur le clavier numérique) valent « : ».
 * Maximum 99:59:59, minimum 1 seconde.
 */
object CountdownInput {
    const val MAX_SECONDS = 99 * 3600 + 59 * 60 + 59
    private const val MAX_LENGTH = 8
    private const val MAX_SEPARATORS = 2

    /** Ne garde que les chiffres et au plus deux séparateurs (toujours écrits « : »), sur 8 caractères au plus. */
    fun sanitize(input: String): String {
        val result = StringBuilder()
        var separators = 0
        for (c in input) {
            when {
                c in '0'..'9' -> result.append(c)
                (c == ':' || c == '.' || c == ',') && separators < MAX_SEPARATORS -> {
                    result.append(':')
                    separators++
                }
            }
        }
        return result.toString().take(MAX_LENGTH)
    }

    /** Durée en secondes (entre 1 et [MAX_SECONDS]), ou null tant que la saisie est vide. */
    fun parseSeconds(text: String): Int? {
        val clean = sanitize(text)
        if (clean.isEmpty() || clean.all { it == ':' }) return null
        // Chaque partie vide (« :30 », « 4: ») compte pour 0.
        val parts = clean.split(':').map { it.toLongOrNull() ?: 0L }
        val total = when (parts.size) {
            1 -> parts[0] * 60
            2 -> parts[0] * 60 + parts[1]
            else -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        }
        return total.coerceIn(1L, MAX_SECONDS.toLong()).toInt()
    }
}

/** « 05:30 » sous une heure, « 2:05:30 » à partir d'une heure. */
internal fun formatClock(totalSeconds: Long): String {
    val t = totalSeconds.coerceAtLeast(0)
    val hours = t / 3600
    val minutes = t % 3600 / 60
    val seconds = t % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}
