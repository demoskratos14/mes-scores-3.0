package com.aventure.messcores

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Délai de calme après une modification avant d'écrire dans le journal (une rafale de saisies = une écriture). */
internal const val AUTOSAVE_DELAY_MS = 2_000L

/** Intervalle de sauvegarde du chrono des blinds, qui avance même sans aucune action des joueurs. */
internal const val AUTOSAVE_CLOCK_INTERVAL_MS = 30_000L

/**
 * Auto-sauvegarde de la partie en cours dans le journal : [AUTOSAVE_DELAY_MS] après la dernière
 * modification (score, joueur, action de poker…), et toutes les [AUTOSAVE_CLOCK_INTERVAL_MS] tant que
 * le chrono des blinds tourne. Sans cela, la partie n'était écrite qu'au moment de quitter l'écran :
 * une batterie vide ou un plantage en pleine partie la faisait perdre.
 *
 * [snapshotFlow] relit l'état de la partie à chaque changement de n'importe lequel des états Compose
 * qu'elle contient ; [collectLatest] annule l'attente en cours à chaque nouvelle modification, ce
 * qui donne le même effet qu'un « debounce » sans API expérimentale.
 */
@Composable
fun AutoSaveEffect(viewModel: ScoreViewModel, repository: GameHistoryRepository) {
    LaunchedEffect(viewModel, repository) {
        snapshotFlow { viewModel.chartSnapshot() }.collectLatest {
            delay(AUTOSAVE_DELAY_MS)
            viewModel.autoSave(repository)
        }
    }
    LaunchedEffect(viewModel, repository) {
        while (true) {
            delay(AUTOSAVE_CLOCK_INTERVAL_MS)
            if (viewModel.pokerSession.clockRunning) viewModel.autoSave(repository, force = true)
        }
    }
}
