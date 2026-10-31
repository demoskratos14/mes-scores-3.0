package com.aventure.messcores

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight

/**
 * L'écran de Cœurs jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici (voir [HeartsSection]).
 * « Retour » et la fin de partie quittent la partie ([onBack]).
 */
@Composable
internal fun HeartsScreen(viewModel: ScoreViewModel, onBack: () -> Unit) {
    if (viewModel.gameRules.id != HEARTS_ID) {
        // Aucune partie de Cœurs ouverte (partie fermée entre-temps) : retour.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.heartsRound() }
    // Un autre joueur, un pli complet, une nouvelle manche ou la main qu'on vient de dévoiler : on remonte en haut de la page.
    CardGameScreen(
        viewModel = viewModel,
        onBack = onBack,
        scrollKeys = listOf(round.current, round.revealed, round.roundOver, round.roundNumber, round.trickDone, round.phase)
    ) {
        HeartsSection(viewModel = viewModel, round = round, onLeave = onBack)
    }
}

/**
 * Cœurs tour par tour (voir [HeartsRound]) : l'appli distribue, fait passer 3 cartes, vérifie chaque carte posée, désigne qui
 * ramasse chaque pli, compte les points de la manche (grand chelem compris) et les inscrit au tableau des scores. Le
 * téléphone se passe de joueur en joueur : la main n'apparaît qu'après « Voir ma main ».
 */
@Composable
internal fun HeartsSection(viewModel: ScoreViewModel, round: HeartsRound, onLeave: () -> Unit) {
    val players = viewModel.players
    val labels = rememberFrenchLabels()
    val over = viewModel.isGameOver()

    // Une manche qui vient de finir est inscrite tout de suite : quitter ensuite ne la fait pas perdre.
    fun settleIfOver() {
        if (round.roundOver) round.settle(viewModel)
    }

    ScoresCard(viewModel = viewModel, current = if (over || round.roundOver) -1 else round.current)

    if (over) {
        // La partie est finie (ou reprise déjà finie) : le vainqueur, les points de la dernière manche s'il y en a une, puis la sortie.
        GameOverBanner(players = players, winners = viewModel.winners())
        if (round.roundOver) HeartsRoundResult(round = round, players = players, canContinue = false, onNext = {})
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dice_leave_game))
        }
        return
    }

    Text(
        text = stringResource(R.string.huit_round, round.roundNumber),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White
    )
    if (round.passOffset == 0 && !round.roundOver) {
        Text(
            text = stringResource(R.string.hearts_no_pass),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }

    if (round.roundOver) {
        HeartsRoundResult(
            round = round,
            players = players,
            canContinue = round.settled,
            onNext = { round.nextRound() }
        )
        return
    }

    val name = players.getOrElse(round.current) { "" }

    if (round.phase == HeartsPhase.PASSING) {
        if (!round.revealed) {
            PassPhoneCard(name = name, onReveal = { round.reveal() })
        } else {
            HeartsHandCard(
                round = round,
                labels = labels,
                name = name,
                targetName = players.getOrElse(round.passTarget(round.current)) { "" },
                onPlay = {},
                onPass = { cards -> round.confirmPass(cards) }
            )
        }
        return
    }

    HeartsTableCard(round = round, labels = labels, players = players)

    when {
        round.trickDone -> {
            // Tout le monde voit le pli : celui qui le ramasse, ce qu'il rapporte, puis le pli suivant.
            CardPanel {
                Text(
                    text = stringResource(
                        R.string.hearts_trick_won,
                        players.getOrElse(round.trickWinner) { "" },
                        round.trickPoints()
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                Button(
                    onClick = {
                        if (round.collectTrick()) settleIfOver()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.hearts_continue))
                }
            }
        }
        !round.revealed -> PassPhoneCard(name = name, onReveal = { round.reveal() })
        else -> HeartsHandCard(
            round = round,
            labels = labels,
            name = name,
            targetName = "",
            onPlay = { card -> round.play(card) },
            onPass = {}
        )
    }
}

/** La table : le pli en cours, les points ramassés par chacun et l'état des cœurs (rien de secret). */
@Composable
private fun HeartsTableCard(round: HeartsRound, labels: FrenchLabels, players: List<String>) {
    CardPanel {
        Text(
            text = stringResource(R.string.hearts_trick_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (round.trick.isEmpty()) {
            Text(stringResource(R.string.hearts_trick_empty), style = MaterialTheme.typography.bodyMedium)
        }
        round.trick.forEach { play ->
            Text(
                text = stringResource(R.string.hearts_trick_line, players.getOrElse(play.player) { "" }, labels.face(play.card)),
                style = MaterialTheme.typography.titleMedium,
                color = if (frenchSuitColor(FrenchCards.suit(play.card)) == CardRed) CardRed else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (round.trickDone && play.player == round.trickWinner) FontWeight.Bold else FontWeight.Normal
            )
        }
        Text(
            text = stringResource(if (round.heartsBroken) R.string.hearts_broken_yes else R.string.hearts_broken_no),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.hearts_taken_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val points = round.roundPoints()
        players.forEachIndexed { index, name ->
            Text(
                text = stringResource(R.string.dice_pig_score_line, name, points.getOrElse(index) { 0 }),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** Ajoute ou retire [card] de la sélection [picked] (un bit par carte) ; on ne choisit pas plus de 3 cartes. */
private fun togglePick(picked: Long, card: Int, count: Int): Long {
    val bit = 1L shl card
    return when {
        (picked and bit) != 0L -> picked and bit.inv()
        count < HeartsScoring.PASS_COUNT -> picked or bit
        else -> picked
    }
}

/**
 * La main du joueur qui agit : à la passe, il touche 3 cartes à passer à [targetName] puis confirme ; ensuite il touche une carte
 * jouable à son tour (les cartes qu'il vient de recevoir sont entourées).
 */
@Composable
private fun HeartsHandCard(
    round: HeartsRound,
    labels: FrenchLabels,
    name: String,
    targetName: String,
    onPlay: (card: Int) -> Unit,
    onPass: (cards: List<Int>) -> Unit
) {
    // Les cartes choisies pour la passe : un bit par carte.
    var picked by rememberSaveable(round.current, round.roundNumber, round.phase) { mutableLongStateOf(0L) }
    val passing = round.phase == HeartsPhase.PASSING
    val hand = round.currentHand.sortedWith(compareBy({ FrenchCards.suit(it) }, { FrenchCards.strength(it) }))
    val count = java.lang.Long.bitCount(picked)
    val receivedNow = round.received.getOrElse(round.current) { emptySet() }

    CardPanel(spacing = 10) {
        Text(
            text = stringResource(R.string.dice_bunco_turn, name),
            style = MaterialTheme.typography.titleMedium
        )
        if (passing) {
            Text(stringResource(R.string.hearts_pass_pick, targetName), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = stringResource(R.string.huit_hand_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardRows(hand) { card, cardModifier ->
            val playable = passing || round.isPlayable(card)
            val isPicked = (picked and (1L shl card)) != 0L
            val toggle: () -> Unit = { picked = togglePick(picked, card, count) }
            val play: () -> Unit = { onPlay(card) }
            FrenchCardFace(
                card = card,
                labels = labels,
                playable = playable,
                selected = if (passing) isPicked else card in receivedNow,
                modifier = cardModifier,
                onClick = if (!playable) null else if (passing) toggle else play
            )
        }

        if (passing) {
            Text(
                text = stringResource(R.string.hearts_pass_count, count, HeartsScoring.PASS_COUNT),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = {
                    onPass(hand.filter { (picked and (1L shl it)) != 0L })
                },
                enabled = count == HeartsScoring.PASS_COUNT,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.hearts_pass_confirm))
            }
        } else {
            val first = round.trick.isEmpty() && round.tricksPlayed == 0
            Text(
                text = stringResource(if (first) R.string.hearts_lead_first else R.string.hearts_hint),
                style = MaterialTheme.typography.bodyMedium
            )
            if (receivedNow.isNotEmpty()) {
                Text(stringResource(R.string.hearts_received_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** La fin de la manche : les points de chacun (déjà inscrits au tableau, grand chelem compris) et la manche suivante. */
@Composable
private fun HeartsRoundResult(round: HeartsRound, players: List<String>, canContinue: Boolean, onNext: () -> Unit) {
    val shooter = round.moonShooter
    RoundResultCard(
        title = if (shooter >= 0) {
            stringResource(R.string.hearts_moon, players.getOrElse(shooter) { "" })
        } else {
            stringResource(R.string.hearts_round_over)
        },
        players = players,
        points = round.roundPoints(),
        winner = shooter,
        canContinue = canContinue,
        onNext = onNext
    )
}
