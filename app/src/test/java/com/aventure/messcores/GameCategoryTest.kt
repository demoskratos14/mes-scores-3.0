package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class GameCategoryTest {

    private val builtIns = GameRepository(RuntimeEnvironment.getApplication()).builtInGames()

    @Test
    fun lesJeuxPredefinisSontRangesParRubrique() {
        val byId = builtIns.associate { it.id to it.category() }
        assertEquals(GameCategory.CLASSIC, byId.getValue("builtin_generic"))
        // Cartes classiques (nombres et figures, 32 / 52 / 78 cartes) seulement.
        for (id in listOf("builtin_tarot", "builtin_belote", "builtin_rami")) {
            assertEquals(id, GameCategory.CARDS, byId.getValue(id))
        }
        // Paquets spéciaux : Autres.
        assertEquals(GameCategory.OTHER, byId.getValue("builtin_skyjo"))
        assertEquals(GameCategory.OTHER, byId.getValue("builtin_uno"))
        for (id in listOf("builtin_yams", "builtin_421", "builtin_cdc", "builtin_qwixx", "builtin_kot", "builtin_mexicain")) {
            assertEquals(id, GameCategory.DICE, byId.getValue(id))
        }
    }

    @Test
    fun lesOnzeJeuxDeDesSontProposesEtDansLeurRubrique() {
        val dice = builtIns.filter { it.category() == GameCategory.DICE }.map { it.id }.toSet()
        assertEquals(
            setOf(
                "builtin_yams", "builtin_421", "builtin_cdc", "builtin_dixmille", "builtin_cochon", "builtin_zombie",
                "builtin_shutbox", "builtin_bunco", "builtin_kot", "builtin_mexicain", "builtin_qwixx"
            ),
            dice
        )
    }

    @Test
    fun seulsLesJeuxAPaquetSpecialSontDansAutres() {
        // Un nouveau jeu prédéfini oublié dans GameCategory.kt atterrirait dans « Autres » : on le détecte ici.
        val others = builtIns.filter { it.category() == GameCategory.OTHER }.map { it.id }.toSet()
        assertEquals(setOf("builtin_skyjo", "builtin_uno"), others)
    }

    @Test
    fun lesJeuxCreesParLUtilisateurVontDansAutres() {
        val custom = GameRules(id = "custom_123", name = "Mon jeu")
        assertEquals(GameCategory.OTHER, custom.category())
    }

    @Test
    fun lOrdreDAffichageEstClassiqueCartesDesAutres() {
        assertEquals(
            listOf(GameCategory.CLASSIC, GameCategory.CARDS, GameCategory.DICE, GameCategory.OTHER),
            GameCategory.entries.toList()
        )
    }
}
