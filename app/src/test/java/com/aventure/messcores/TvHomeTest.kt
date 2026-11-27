package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class TvHomeTest {

    private val builtIns = GameRepository(RuntimeEnvironment.getApplication()).builtInGames()

    @Test
    fun lAccueilTvListeAuMoinsUnJeuSansDoublon() {
        assertTrue(TV_GAME_IDS.isNotEmpty())
        assertEquals(TV_GAME_IDS.size, TV_GAME_IDS.toSet().size)
    }

    @Test
    fun chaqueJeuTvExisteSeJoueSeulEtAUneRoute() {
        // Un jeu de la liste qui n'existe plus, qui demande d'autres joueurs ou qui n'a pas de route ne s'ouvrirait pas.
        for (id in TV_GAME_IDS) {
            val game = builtIns.firstOrNull { it.id == id }
            assertTrue("jeu inconnu : $id", game != null)
            assertTrue("$id n'est pas un jeu solo", game!!.isSoloOnlyGame())
            assertTrue("$id n'a pas de route dans OWN_SCREEN_ROUTES", id in OWN_SCREEN_ROUTES)
        }
    }

    @Test
    fun lInterNAQueLesHuitJeuxDeSupermarcheDeLaTele() {
        // L'Inter réutilise les écrans de la version télé : chaque jeu doit y être, sans doublon.
        assertEquals(8, INTER_GAME_IDS.size)
        assertEquals(INTER_GAME_IDS.size, INTER_GAME_IDS.toSet().size)
        for (id in INTER_GAME_IDS) {
            assertTrue("$id n'est pas dans TV_GAME_IDS", id in TV_GAME_IDS)
            assertTrue("$id n'a pas de route dans OWN_SCREEN_ROUTES", id in OWN_SCREEN_ROUTES)
        }
        // Les tests tournent dans la variante téléphone : l'accueil y reste celui de toute la liste télé.
        assertFalse(BuildConfig.IS_INTER)
        assertEquals(TV_GAME_IDS, homeGameIds())
    }

    @Test
    fun rythmeNEstPasSurLaTele() {
        // Rythme demande de choisir un fichier de musique avec le sélecteur de documents d'Android, absent des télés.
        assertFalse(RHYTHM_ID in TV_GAME_IDS)
    }

    @Test
    fun lAppliTelephoneNEstPasEnModeTvParDefaut() {
        // Les tests tournent dans la variante téléphone : le mode TV n'y est ni forcé ni allumé sur un téléphone.
        assertFalse(BuildConfig.IS_TV)
        assertFalse(TvMode.load(RuntimeEnvironment.getApplication(), SNAKE_ID))
    }
}
