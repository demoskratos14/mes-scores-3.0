package com.aventure.messcores

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Jeux personnalisés : sauvegarde, modification, suppression et reprise sur données abîmées. */
@RunWith(RobolectricTestRunner::class)
class GameRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: GameRepository

    private val prefs get() = context.getSharedPreferences("mes_scores_games", Context.MODE_PRIVATE)

    private fun custom(id: String, name: String = "Jeu $id") =
        GameRules(id = id, name = name, minPlayers = 2, maxPlayers = 6, lowestWins = true)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        repository = GameRepository(context)
    }

    @Test
    fun sansJeuPersonnaliseSeulsLesJeuxPredefinisSontProposes() {
        assertTrue(repository.customGames().isEmpty())
        assertEquals(repository.builtInGames(), repository.allGames())
    }

    @Test
    fun unJeuSauvegardeSeRetrouveAvecSesReglesApresRelecture() {
        repository.saveCustomGame(custom("a"))
        val relu = GameRepository(context).customGames().single()
        assertEquals(custom("a"), relu)
        assertEquals(repository.builtInGames().size + 1, repository.allGames().size)
    }

    @Test
    fun modifierUnJeuLeRemplaceSansLeDupliquer() {
        repository.saveCustomGame(custom("a"))
        repository.saveCustomGame(custom("b"))
        repository.updateCustomGame(custom("a", name = "Renommé").copy(maxPlayers = 8))
        val jeux = repository.customGames()
        assertEquals(listOf("a", "b"), jeux.map { it.id })
        assertEquals("Renommé", jeux[0].name)
        assertEquals(8, jeux[0].maxPlayers)
    }

    @Test
    fun modifierUnIdInconnuNeChangeRien() {
        repository.saveCustomGame(custom("a"))
        repository.updateCustomGame(custom("inconnu"))
        assertEquals(listOf("a"), repository.customGames().map { it.id })
    }

    @Test
    fun supprimerUnJeuLaisseLesAutres() {
        repository.saveCustomGame(custom("a"))
        repository.saveCustomGame(custom("b"))
        repository.deleteCustomGame("a")
        assertEquals(listOf("b"), repository.customGames().map { it.id })
    }

    @Test
    fun uneListeEntierementIllisibleDonneAucunJeuMaisEstMiseDeCote() {
        prefs.edit().putString("custom_games", "pas du json").commit()
        assertTrue(repository.customGames().isEmpty())
        assertEquals("pas du json", prefs.getString("custom_games_unreadable_backup", null))
    }

    @Test
    fun unJeuAbimeEstIgnoreSeulEtLeTexteBrutEstConserve() {
        val bon = custom("bon").toJson()
        val brut = """[$bon, {"id": "casse"}]"""
        prefs.edit().putString("custom_games", brut).commit()

        assertEquals(listOf("bon"), repository.customGames().map { it.id })
        assertEquals(brut, prefs.getString("custom_games_unreadable_backup", null))
    }

    @Test
    fun enregistrerUnNouveauJeuApresCorruptionNEcrasePasLaCopieDeSecurite() {
        prefs.edit().putString("custom_games", "pas du json").commit()
        repository.saveCustomGame(custom("neuf"))

        assertEquals(listOf("neuf"), repository.customGames().map { it.id })
        assertEquals("pas du json", prefs.getString("custom_games_unreadable_backup", null))
    }

    @Test
    fun laCopieDeSecuriteGardeLeTexteLePlusAncien() {
        prefs.edit().putString("custom_games", "premier").commit()
        repository.customGames()
        prefs.edit().putString("custom_games", "second").commit()
        repository.customGames()
        assertEquals("premier", prefs.getString("custom_games_unreadable_backup", null))
    }

    @Test
    fun desDonneesSainesNeCreentAucuneCopieDeSecurite() {
        repository.saveCustomGame(custom("a"))
        repository.customGames()
        assertFalse(prefs.contains("custom_games_unreadable_backup"))
        assertNull(prefs.getString("custom_games_unreadable_backup", null))
    }
}
