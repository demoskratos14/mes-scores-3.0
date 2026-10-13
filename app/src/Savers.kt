package com.aventure.messcores

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/**
 * Savers pour rememberSaveable : permettent de conserver une saisie quand l'activité est
 * recréée (rotation de l'écran, changement de thème ou de taille de police, etc.).
 */

/** Liste de textes (noms de joueurs…). */
val StringListSaver: Saver<List<String>, Any> =
    listSaver(save = { it.toList() }, restore = { it })

/** Ensemble d'index de joueurs. */
val IntSetSaver: Saver<Set<Int>, Any> =
    listSaver(save = { it.toList() }, restore = { it.toSet() })
