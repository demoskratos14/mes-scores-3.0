package com.aventure.messcores

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Choix de la langue : quelle langue est affichée, et garde-fous pour qu'une langue ajoutée à
 * [AppLanguage] ne soit pas oubliée dans les ressources ou dans la liste des langues d'Android.
 */
class AppLanguageTest {

    private fun resFile(path: String) = File("src/main/res/$path")

    private fun stringsFile(language: AppLanguage) =
        if (language == AppLanguage.DEFAULT) resFile("values/strings.xml") else resFile("values-${language.tag}/strings.xml")

    @Test
    fun laPremiereLangueTraduiteEstChoisie() {
        assertEquals(AppLanguage.ITALIAN, AppLanguage.resolve(listOf("de", "it", "fr")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(listOf("en", "es")))
    }

    @Test
    fun uneLangueNonTraduiteRetombeSurLeFrancais() {
        assertEquals(AppLanguage.FRENCH, AppLanguage.resolve(listOf("de", "ja")))
    }

    @Test
    fun uneListeVideRetombeSurLeFrancais() {
        assertEquals(AppLanguage.FRENCH, AppLanguage.resolve(emptyList()))
    }

    @Test
    fun codesEtDrapeauxSontUniques() {
        assertEquals(AppLanguage.entries.size, AppLanguage.entries.map { it.tag }.toSet().size)
        assertEquals(AppLanguage.entries.size, AppLanguage.entries.map { it.flag }.toSet().size)
    }

    @Test
    fun chaqueLangueADesRessourcesEtLeTexteDuBouton() {
        AppLanguage.entries.forEach { language ->
            val file = stringsFile(language)
            assertTrue("fichier manquant : ${file.path}", file.isFile)
            assertTrue(
                "language_change manquant dans ${file.path}",
                file.readText().contains("name=\"language_change\"")
            )
        }
    }

    @Test
    fun androidConnaitToutesLesLangues() {
        val config = resFile("xml/locales_config.xml")
        assertTrue("fichier manquant : ${config.path}", config.isFile)
        val declared = Regex("""<locale\s+android:name="([^"]+)"""").findAll(config.readText()).map { it.groupValues[1] }.toSet()
        assertEquals(AppLanguage.entries.map { it.tag }.toSet(), declared)
    }
}
