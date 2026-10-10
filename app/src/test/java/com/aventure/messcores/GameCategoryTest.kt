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
            "builtin_coeurs", "builtin_pique", "builtin_gin", "builtin_huit", "builtin_cribbage",
            "builtin_manille", "builtin_scopa", "builtin_yaniv", "builtin_barbu", "builtin_ohhell", "builtin_sueca"
        )) {
            assertEquals(id, GameCategory.CARDS, byId.getValue(id))
        }
        // Paquets spéciaux : Spéciaux.
        assertEquals(GameCategory.SPECIAL, byId.getValue("builtin_skyjo"))
        assertEquals(GameCategory.SPECIAL, byId.getValue("builtin_uno"))
        for (id in listOf("builtin_yams", "builtin_421", "builtin_cdc", "builtin_qwixx", "builtin_kot", "builtin_mexicain")) {
            assertEquals(id, GameCategory.DICE, byId.getValue(id))
        }
    }

    @Test
    fun lesJeuxSpeciauxEtLesSportsSontRanges() {
        // Un nouveau jeu prédéfini oublié dans GameCategory.kt atterrirait dans « Autres » : on le détecte ici.
        fun idsIn(category: GameCategory) = builtIns.filter { it.category() == category }.map { it.id }.toSet()
        assertEquals(
            setOf(
                "builtin_skyjo", "builtin_uno", "builtin_milleb", "builtin_sixqp", "builtin_papayoo", "builtin_cabo",
                "builtin_rummikub", "builtin_dominos", "builtin_wizard", "builtin_phase10", "builtin_splendor",
                "builtin_dixit", "builtin_ttr", "builtin_azul", "builtin_carcassonne", "builtin_catane",
                "builtin_sevenwonders", "builtin_scrabble", "builtin_trivial"
            ),
            idsIn(GameCategory.SPECIAL)
        )
        assertEquals(
            setOf(
                "builtin_petanque", "builtin_molkky", "builtin_darts301", "builtin_darts501", "builtin_bowling",
                "builtin_golf", "builtin_pingpong", "builtin_badminton", "builtin_volley", "builtin_babyfoot"
            ),
            idsIn(GameCategory.OTHER)
        )
    }

    @Test
    fun lesJeuxCreesParLUtilisateurVontDansAutres() {
        val custom = GameRules(id = "custom_123", name = "Mon jeu")
        assertEquals(GameCategory.OTHER, custom.category())
    }

    @Test
    fun lOrdreDAffichageEstClassiqueCartesDesSpeciauxAutres() {
        assertEquals(
            listOf(GameCategory.CLASSIC, GameCategory.CARDS, GameCategory.DICE, GameCategory.SPECIAL, GameCategory.OTHER),
            GameCategory.entries.toList()
        )
    }
}
