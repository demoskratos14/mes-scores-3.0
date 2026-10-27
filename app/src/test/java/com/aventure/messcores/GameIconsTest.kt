package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class GameIconsTest {

    private val builtIns = GameRepository(RuntimeEnvironment.getApplication()).builtInGames()

    @Test
    fun chaqueJeuJouableSpecialAUneIconeDessinee() {
        // Un jeu jouable ajouté à « Spéciaux » sans icône apparaîtrait avec l'icône générique : on le détecte ici.
        val playableSpecial = builtIns
            .filter { it.category() == GameCategory.SPECIAL && it.isPlayableGame() }
            .map { it.id }
            .toSet()
        assertEquals(playableSpecial, GAME_ICON_IDS)
    }
}
