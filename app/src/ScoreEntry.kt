package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * Prochain joueur dont la case est encore vide, en partant de [from] et en avançant de [step]
 * (+1 : joueur suivant, −1 : joueur précédent). La recherche fait le tour de la table et saute
 * les joueurs dont le score est déjà inscrit ; [from] lui-même n'est jamais renvoyé.
 * Renvoie null s'il ne reste aucun autre joueur à saisir.
 */
internal fun nextPendingPlayer(values: List<Int?>, from: Int, step: Int): Int? {
    val n = values.size
    if (n < 2 || step == 0) return null
    for (k in 1 until n) {
        val candidate = ((from + step * k) % n + n) % n
        if (values[candidate] == null) return candidate
    }
    return null
}

/** Bandeau de la fenêtre de saisie : le nom du joueur au centre, une flèche de chaque côté (si disponible). */
@Composable
internal fun PlayerEntryTitle(
    nav: PlayerEntryNav,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { nav.onPrevious?.invoke() }, enabled = nav.onPrevious != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.score_previous_player)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = nav.playerName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = playerTextColor(nav.playerColor),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { nav.onNext?.invoke() }, enabled = nav.onNext != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.score_next_player)
            )
        }
    }
}

/**
 * Joueur affiché dans la fenêtre de saisie. [onPrevious] / [onNext] sont null quand il n'y a pas
 * d'autre joueur à aller saisir (les flèches sont alors grisées).
 */
internal class PlayerEntryNav(
    val playerName: String,
    val playerColor: Color,
    val onPrevious: (() -> Unit)?,
    val onNext: (() -> Unit)?
)

/**
 * Fenêtre de saisie unique du tableau : elle s'ouvre sur la case (manche [round], joueur [player]) et,
 * pour les manches « libres » (Skyjo…), passe d'un joueur à l'autre sans se fermer :
 * - après un score saisi sur une case vide, elle passe au joueur suivant dont la case est vide ;
 * - les flèches vont au joueur précédent / suivant en sautant ceux dont le score est déjà inscrit ;
 * - elle se ferme quand tous les joueurs de la manche ont leur score.
 * Les feuilles de catégories et le bowling gardent leur fenêtre habituelle, avec le nom du joueur.
 */
@Composable
internal fun ScoreEntryDialog(
    viewModel: ScoreViewModel,
    round: Int,
    player: Int,
    onSelect: (round: Int, player: Int) -> Unit,
    onClose: () -> Unit
) {
    val row = viewModel.scores.getOrNull(round)
    val cell = row?.getOrNull(player)
    val name = viewModel.players.getOrNull(player)
    if (row == null || cell == null || name == null) {
        // Case introuvable (ne devrait pas arriver) : on ferme la fenêtre en dehors de la composition.
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val color = viewModel.playerColors.getOrElse(player) { Color.Gray }
    val rules = viewModel.gameRules
    val sheetRow = viewModel.sheetRows?.getOrNull(round)
    val isBowling = rules.sheet == ScoreSheets.BOWLING
    // Seules les manches libres (ni feuille de catégories ni bowling) enchaînent les joueurs.
    val chains = viewModel.sheetRows == null && !isBowling

    val title = sheetRow?.title()
    val subtitle = title ?: stringResource(R.string.score_round_n, round + 1)

    fun pending(step: Int): Int? = nextPendingPlayer(row.map { it.baseValue }, player, step)

    val nav = PlayerEntryNav(
        playerName = name,
        playerColor = color,
        onPrevious = if (chains) pending(-1)?.let { target -> { onSelect(round, target) } } else null,
        onNext = if (chains) pending(+1)?.let { target -> { onSelect(round, target) } } else null
    )

    // Une case déjà remplie qu'on corrige : on ferme ensuite. Une case vide : on passe au joueur suivant.
    val wasEmpty = cell.baseValue == null
    val entered = { value: Int?, negative: Boolean ->
        viewModel.setCell(round, player, value, negative)
        val next = if (chains && wasEmpty && value != null) pending(+1) else null
        if (next != null) onSelect(round, next) else onClose()
    }

    val choices = sheetRow?.options
    val bowlingFrame = if (isBowling) round else null
    when {
        choices != null -> ScoreChoiceDialog(
            title = "$name · $subtitle",
            options = choices,
            hint = sheetRow?.hint?.let { stringResource(it) },
            zeroCrossesOut = sheetRow?.zeroCrossesOut ?: true,
            current = cell.baseValue,
            onPick = { value ->
                viewModel.setCell(round, player, value, false)
                onClose()
            },
            onDismiss = onClose
        )
        bowlingFrame != null -> BowlingFrameDialog(
            frame = bowlingFrame,
            title = "$name · $subtitle",
            onConfirm = { code ->
                viewModel.setCell(round, player, code, false)
                onClose()
            },
            onDismiss = onClose
        )
        rules.bidScoring != null -> BidScoreDialog(
            nav = nav,
            subtitle = subtitle,
            resetKey = round to player,
            kind = rules.bidScoring,
            onConfirm = { score -> entered(score, false) },
            onDismiss = onClose
        )
        else -> ScoreInputDialog(
            nav = nav,
            subtitle = subtitle,
            resetKey = round to player,
            initialValue = cell.baseValue,
            initialNegative = cell.isNegative,
            allowNegative = rules.allowNegativeScores,
            onConfirm = entered,
            onDismiss = onClose
        )
    }
}
