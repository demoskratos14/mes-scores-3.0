package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Saisie d'une donne de skat avec calcul automatique de la valeur du jeu et de la marque
 * (voir [computeSkatRound]) : déclarant, jeu, matadors, Hand / Ouvert / annonces et résultat.
 */
@Composable
fun NewSkatRoundScreen(
    viewModel: ScoreViewModel,
    onDone: () -> Unit
) {
    val players = viewModel.players
    val resources = LocalResources.current

    // Si on corrige une donne existante, les champs démarrent avec sa saisie d'origine.
    val editIndex = viewModel.editingTeamRoundIndex
    val editing: SkatInput? = editIndex
        ?.let { viewModel.teamRounds.getOrNull(it)?.contractInput }
        ?.let { SkatInput.decode(it) }

    // rememberSaveable : la saisie survit à une rotation de l'écran.
    var declarer by rememberSaveable { mutableStateOf<Int?>(editing?.declarer) }
    var game by rememberSaveable { mutableIntStateOf(editing?.game ?: SkatGame.CLUBS) }
    var matadors by rememberSaveable { mutableIntStateOf(editing?.matadors ?: 1) }
    var hand by rememberSaveable { mutableStateOf(editing?.hand ?: false) }
    var schneiderAnnounced by rememberSaveable { mutableStateOf(editing?.schneiderAnnounced ?: false) }
    var schwarzAnnounced by rememberSaveable { mutableStateOf(editing?.schwarzAnnounced ?: false) }
    var open by rememberSaveable { mutableStateOf(editing?.open ?: false) }
    var outcome by rememberSaveable { mutableIntStateOf(editing?.outcome ?: SkatOutcome.WON) }
    var bidText by rememberSaveable { mutableStateOf(editing?.bid?.takeIf { it > 0 }?.toString() ?: "") }

    val isNull = game == SkatGame.NULL
    val matadorsEffective = matadors.coerceIn(1, skatMaxMatadors(game))
    // Au null, on ne distingue que gagné et perdu.
    val outcomeOptions = if (isNull) listOf(SkatOutcome.WON, SkatOutcome.LOST) else listOf(
        SkatOutcome.WON, SkatOutcome.WON_SCHNEIDER, SkatOutcome.WON_SCHWARZ,
        SkatOutcome.LOST, SkatOutcome.LOST_SCHNEIDER, SkatOutcome.LOST_SCHWARZ
    )
    val outcomeEffective = if (isNull) {
        if (outcome <= SkatOutcome.WON_SCHWARZ) SkatOutcome.WON else SkatOutcome.LOST
    } else outcome
    val outcomeLabels = listOf(
        stringResource(R.string.skat_out_won), stringResource(R.string.skat_out_won_schneider),
        stringResource(R.string.skat_out_won_schwarz), stringResource(R.string.skat_out_lost),
        stringResource(R.string.skat_out_lost_schneider), stringResource(R.string.skat_out_lost_schwarz)
    )
    val gameNames = listOf("♣", "♠", "♥", "♦", stringResource(R.string.skat_grand), stringResource(R.string.skat_null))
    val bid = bidText.toIntOrNull() ?: 0

    // ----- Calcul (voir SkatScoring.kt) -----
    val declarerIndex = declarer
    val result = computeSkatRound(
        game = game,
        matadors = matadorsEffective,
        hand = hand,
        schneiderAnnounced = schneiderAnnounced && !isNull,
        schwarzAnnounced = schwarzAnnounced && !isNull,
        open = open,
        outcome = outcomeEffective,
        bid = bid
    )
    val deltas = declarerIndex?.let { skatDeltas(players.size, it, result) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 24.dp)
            .imePadding()
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardSurface()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    stringResource(if (editing != null) R.string.tarot_title_edit else R.string.new_round),
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(stringResource(R.string.skat_declarer), style = MaterialTheme.typography.titleSmall)
                ContractChoiceRow(players, declarer) { declarer = it }

                Text(stringResource(R.string.skat_game), style = MaterialTheme.typography.titleSmall)
                ContractChoiceRow(gameNames, game) { game = it }

                if (!isNull) {
                    Text(stringResource(R.string.skat_matadors), style = MaterialTheme.typography.titleSmall)
                    ContractChoiceRow(
                        List(skatMaxMatadors(game)) { (it + 1).toString() },
                        matadorsEffective - 1
                    ) { matadors = it + 1 }
                }

                // Au null, seuls Hand et Ouvert existent ; sinon on ajoute les annonces de Schneider et de Schwarz.
                if (isNull) {
                    ContractToggleRow(
                        listOf(stringResource(R.string.skat_hand) to hand, stringResource(R.string.skat_open) to open)
                    ) { if (it == 0) hand = !hand else open = !open }
                } else {
                    ContractToggleRow(
                        listOf(
                            stringResource(R.string.skat_hand) to hand,
                            stringResource(R.string.skat_open) to open,
                            stringResource(R.string.skat_schneider_announced) to schneiderAnnounced,
                            stringResource(R.string.skat_schwarz_announced) to schwarzAnnounced
                        )
                    ) {
                        when (it) {
                            0 -> hand = !hand
                            1 -> open = !open
                            2 -> schneiderAnnounced = !schneiderAnnounced
                            else -> schwarzAnnounced = !schwarzAnnounced
                        }
                    }
                    Text(
                        stringResource(R.string.skat_open_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(stringResource(R.string.skat_result), style = MaterialTheme.typography.titleSmall)
                ContractChoiceRow(
                    options = outcomeOptions.map { outcomeLabels[it] },
                    selected = outcomeOptions.indexOf(outcomeEffective).takeIf { it >= 0 }
                ) { outcome = outcomeOptions[it] }

                OutlinedTextField(
                    value = bidText,
                    onValueChange = { bidText = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.skat_bid_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // ----- Aperçu du résultat -----
                if (declarerIndex != null && deltas != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.won) successContainer() else failureContainer(),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isNull) {
                                    stringResource(R.string.skat_value_fixed, result.gameValue)
                                } else {
                                    stringResource(R.string.skat_value, result.gameValue, result.baseValue, result.multiplier)
                                },
                                fontWeight = FontWeight.Bold
                            )
                            if (result.overbid) {
                                Text(stringResource(R.string.skat_overbid), style = MaterialTheme.typography.bodySmall)
                            }
                            if (result.announceFailed) {
                                Text(stringResource(R.string.skat_announce_failed), style = MaterialTheme.typography.bodySmall)
                            }
                            if (!result.won) {
                                Text(
                                    stringResource(R.string.skat_others, signedPoints(result.defenderScore)),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            players.forEachIndexed { index, name ->
                                Text("$name : ${signedPoints(deltas[index])}")
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val roundDeltas = deltas ?: return@Button
                        val declarerId = declarerIndex ?: return@Button
                        val label = resources.getString(
                            R.string.skat_label,
                            players[declarerId],
                            gameNames[game],
                            resources.getString(if (result.won) R.string.skat_label_won else R.string.skat_label_lost)
                        )
                        val round = TeamRound(
                            teamALabel = label,
                            teamAPlayers = setOf(declarerId),
                            value = result.declarerScore,
                            deltas = roundDeltas,
                            contractInput = SkatInput(
                                declarer = declarerId,
                                game = game,
                                matadors = matadorsEffective,
                                hand = hand,
                                schneiderAnnounced = schneiderAnnounced && !isNull,
                                schwarzAnnounced = schwarzAnnounced && !isNull,
                                open = open,
                                outcome = outcomeEffective,
                                bid = bid
                            ).encode()
                        )
                        if (editIndex != null && editing != null) {
                            viewModel.replaceTeamRound(editIndex, round)
                        } else {
                            viewModel.appendTeamRound(round)
                        }
                        viewModel.editingTeamRoundIndex = null
                        onDone()
                    },
                    enabled = deltas != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(if (editing != null) R.string.tarot_submit_edit else R.string.validate_round))
                }
            }
        }
    }
}
