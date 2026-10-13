package com.aventure.messcores

import androidx.annotation.StringRes

/**
 * Langues proposées par l'appli : code de langue, drapeau affiché et nom écrit dans la langue elle-même
 * (jamais traduit : on doit pouvoir reconnaître sa langue même quand l'appli est dans une autre).
 *
 * Doit rester aligné avec les dossiers res/values-xx et res/xml/locales_config.xml (vérifié par AppLanguageTest).
 * Le français est la langue des ressources par défaut (res/values).
 */
enum class AppLanguage(val tag: String, val flag: String, @StringRes val nameRes: Int) {
    FRENCH("fr", "\uD83C\uDDEB\uD83C\uDDF7", R.string.language_name_fr),
    ENGLISH("en", "\uD83C\uDDEC\uD83C\uDDE7", R.string.language_name_en),
    SPANISH("es", "\uD83C\uDDEA\uD83C\uDDF8", R.string.language_name_es),
    PORTUGUESE("pt", "\uD83C\uDDF5\uD83C\uDDF9", R.string.language_name_pt),
    ITALIAN("it", "\uD83C\uDDEE\uD83C\uDDF9", R.string.language_name_it);

    companion object {
        /** Langue de repli : celle des ressources par défaut (res/values). */
        val DEFAULT = FRENCH

        /**
         * Langue réellement affichée pour une liste de langues préférées (la plus prioritaire d'abord) :
         * la première que l'appli sait traduire, comme le fait Android pour choisir les ressources.
         * Aucune ne correspond : le français.
         */
        fun resolve(languageCodes: List<String>): AppLanguage =
            languageCodes.firstNotNullOfOrNull { code -> entries.firstOrNull { it.tag == code } } ?: DEFAULT
    }
}
