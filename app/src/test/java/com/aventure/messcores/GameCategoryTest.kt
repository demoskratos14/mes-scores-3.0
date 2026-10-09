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
        for (id in listOf(
            "builtin_tarot", "builtin_belote", "builtin_rami", "builtin_coinche", "builtin_president",
            "builtin_coeurs", "builtin_pique", "builtin_gin", "builtin_huit", "builtin_cribbage"
        )) {
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
    fun seulsLesJeuxAPaquetSpecialSontDansAutres() {
        // Un nouveau jeu prédéfini oublié dans GameCategory.kt atterrirait dans « Autres » : on le détecte ici.
        val others = builtIns.filter { it.category() == GameCategory.OTHER }.map { it.id }.toSet()
        assertEquals(
            setOf("builtin_skyjo", "builtin_uno", "builtin_milleb", "builtin_sixqp", "builtin_papayoo", "builtin_cabo"),
            others
        )
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
