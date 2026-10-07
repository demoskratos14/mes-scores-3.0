package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNamesTest {

    @Test
    fun desNomsDejaDistinctsNeChangentPas() {
        assertEquals(listOf("Marie", "Paul"), PlayerNames.makeUnique(listOf("Marie", "Paul")))
    }

    @Test
    fun leDeuxiemeEtLeTroisiemeMarieSontNumerotes() {
        assertEquals(
            listOf("Marie", "Marie 2", "Marie 3"),
            PlayerNames.makeUnique(listOf("Marie", "Marie", "Marie"))
        )
    }

    @Test
    fun laComparaisonIgnoreLesMajusculesEtLesEspaces() {
        assertEquals(listOf("Marie", "marie 2"), PlayerNames.makeUnique(listOf("Marie", " marie ")))
    }

    @Test
    fun unSuffixeDejaSaisiPlusLoinEstRespecte() {
        // Le « Marie 2 » tapé à la main garde son nom ; le doublon saute au numéro suivant.
        assertEquals(
            listOf("Marie", "Marie 3", "Marie 2"),
            PlayerNames.makeUnique(listOf("Marie", "Marie", "Marie 2"))
        )
    }

    @Test
    fun lesChampsVidesRecoiventUnNomParDefautSelonLeurPosition() {
        assertEquals(
            listOf("Joueur 1", "Paul", "Joueur 3"),
            PlayerNames.resolve(listOf("", "Paul", "  "), "Joueur")
        )
    }

    @Test
    fun resolveDepartageAussiLesDoublons() {
        assertEquals(listOf("Léa", "Léa 2"), PlayerNames.resolve(listOf("Léa", "Léa"), "Joueur"))
    }

    @Test
    fun leMessageDecritLesNomsModifies() {
        assertTrue(PlayerNames.duplicateChanges(listOf("Marie", "Paul"), "Joueur").isEmpty())
        assertEquals(
            listOf("Marie" to "Marie 2"),
            PlayerNames.duplicateChanges(listOf("Marie", "Marie"), "Joueur")
        )
    }
}
