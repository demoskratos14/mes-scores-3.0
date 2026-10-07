package com.aventure.messcores

import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private fun signed(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> "−${-value}"
    else -> "0"
}

/** Nombre d'atouts à montrer pour une poignée simple / double / triple, selon le nombre de joueurs. */
private fun handfulThresholds(playerCount: Int): String? = when (playerCount) {
    3 -> "13 / 15 / 18"
    4 -> "10 / 13 / 15"
    5 -> "8 / 10 / 13"
    else -> null
}

/** Rangée de choix exclusifs, défilante horizontalement si elle ne tient pas à l'écran. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(options: List<String>, selected: Int?, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, label ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text(label) }
            )
        }
    }
}

/**
 * Saisie d'une manche de Tarot avec calcul automatique du score selon les règles officielles :
 * contrat, bouts, points réalisés, petit au bout, poignée et chelem. Le résultat est réparti
 * entre le preneur, son éventuel partenaire (à 5) et les défenseurs, de façon à ce que la somme
 * des scores d'une manche soit toujours nulle.
 */
@Composable
fun NewTarotRoundScreen(
    viewModel: ScoreViewModel,
    onDone: () -> Unit
) {
    val players = viewModel.players
    val multipliers = viewModel.gameRules.multipliers

    // Si on corrige une manche existante, les champs démarrent avec sa saisie d'origine.
    val editIndex = viewModel.editingTeamRoundIndex
    val editing: TarotRoundInput? = editIndex?.let { viewModel.teamRounds.getOrNull(it)?.tarotInput }

    // rememberSaveable : la saisie survit à une rotation de l'écran.
    var takerIndex by rememberSaveable { mutableStateOf<Int?>(editing?.taker) }
    // Index d'un joueur ; null = pas de partenaire (preneur seul).
    var partnerIndex by rememberSaveable { mutableStateOf<Int?>(editing?.partner) }
    var multiplierIndex by rememberSaveable { mutableIntStateOf(editing?.multiplierIndex ?: 0) }
    var bouts by rememberSaveable { mutableIntStateOf(editing?.bouts ?: 0) }
    var pointsText by rememberSaveable { mutableStateOf(editing?.points?.toString() ?: "") }
    var petitAuBout by rememberSaveable { mutableIntStateOf(editing?.petitAuBout ?: 0) } // 0 aucun, 1 preneur, 2 défense
    var handful by rememberSaveable { mutableIntStateOf(editing?.handful ?: 0) }         // 0 aucune, 1 simple, 2 double, 3 triple
    var slamAnnounced by rememberSaveable { mutableStateOf(editing?.slamAnnounced ?: false) } // chelem annoncé par le preneur
    var defenseSlam by rememberSaveable { mutableStateOf(editing?.defenseSlam ?: false) }     // la défense a fait tous les plis

    val taker = takerIndex
    val points = pointsText.toIntOrNull()?.takeIf { it in 0..TAROT_TOTAL_POINTS }
    val hasPartnerChoice = players.size >= 5
    val partner = if (hasPartnerChoice && partnerIndex != taker) partnerIndex else null
    val multiplier = multipliers.getOrElse(multiplierIndex) { multipliers.first() }

    val context = LocalContext.current

    val resources = LocalResources.current

    // ----- Calcul (voir TarotScoring.kt) -----
    val required = tarotRequiredPoints(bouts)
    val result = points?.let {
        computeTarotRound(
            playerCount = players.size,
            taker = taker,
            partner = partner,
            factor = multiplier.factor,
            bouts = bouts,
            points = it,
            petitAuBout = petitAuBout,
            handful = handful,
            slamAnnounced = slamAnnounced,
            defenseSlam = defenseSlam
        )
    }
    val success = result?.success ?: true
    val contractFailed = result?.success == false
    val slamSucceeded = result?.slamSucceeded ?: false
    val defenseSlamActive = result?.defenseSlamActive ?: false
    val deltas = result?.deltas
    val canValidate = taker != null && result != null && deltas != null

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

                Text(stringResource(R.string.tarot_taker), style = MaterialTheme.typography.titleSmall)
                ChoiceRow(players, takerIndex) { takerIndex = it }

                if (hasPartnerChoice) {
                    Text(stringResource(R.string.tarot_partner), style = MaterialTheme.typography.titleSmall)
                    val others = players.indices.filter { it != taker }
                    ChoiceRow(
                        options = listOf(stringResource(R.string.tarot_partner_none)) + others.map { players[it] },
                        selected = partner?.let { others.indexOf(it) + 1 } ?: 0
                    ) { choice -> partnerIndex = if (choice == 0) null else others[choice - 1] }
                }

                Text(stringResource(R.string.contract), style = MaterialTheme.typography.titleSmall)
                ChoiceRow(
                    options = multipliers.map { "${it.label} ×${it.factor}" },
                    selected = multiplierIndex
                ) { multiplierIndex = it }

                Text(stringResource(R.string.tarot_bouts_title), style = MaterialTheme.typography.titleSmall)
                ChoiceRow(List(4) { context.quantity(R.plurals.tarot_bouts, it) }, bouts) { bouts = it }
                Text(
                    stringResource(R.string.tarot_required, required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pointsText,
                    onValueChange = { pointsText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text(stringResource(R.string.tarot_points_label)) },
                    isError = pointsText.isNotEmpty() && points == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.tarot_petit), style = MaterialTheme.typography.titleSmall)
                ChoiceRow(
                    listOf(stringResource(R.string.tarot_none), stringResource(R.string.tarot_taker), stringResource(R.string.tarot_defense)),
                    petitAuBout
                ) { petitAuBout = it }

                Text(stringResource(R.string.tarot_handful), style = MaterialTheme.typography.titleSmall)
                ChoiceRow(
                    listOf(
                        stringResource(R.string.tarot_handful_none),
                        stringResource(R.string.tarot_handful_single),
                        stringResource(R.string.tarot_handful_double),
                        stringResource(R.string.tarot_handful_triple)
                    ),
                    handful
                ) { handful = it }
                handfulThresholds(players.size)?.let {
                    Text(
                        stringResource(R.string.tarot_handful_thresholds, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(stringResource(R.string.tarot_slam), style = MaterialTheme.typography.titleSmall)
                ChoiceRow(
                    options = listOf(stringResource(R.string.tarot_slam_not_announced), stringResource(R.string.tarot_slam_announced)),
                    selected = if (slamAnnounced) 1 else 0
                ) { slamAnnounced = it == 1 }
                if (contractFailed) {
                    ChoiceRow(
                        options = listOf(stringResource(R.string.tarot_defense_not_all), stringResource(R.string.tarot_defense_slam)),
                        selected = if (defenseSlam) 1 else 0
                    ) { defenseSlam = it == 1 }
                }
                val slamHint = when {
                    slamSucceeded && slamAnnounced -> stringResource(R.string.tarot_hint_announced_ok)
                    slamSucceeded -> stringResource(R.string.tarot_hint_unannounced_ok)
                    defenseSlamActive -> stringResource(R.string.tarot_hint_defense, DEFENSE_SLAM_PRIZE)
                    slamAnnounced && points != null -> stringResource(R.string.tarot_hint_announced_failed)
                    else -> stringResource(R.string.tarot_hint_default)
                }
                Text(
                    slamHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ----- Aperçu du résultat -----
                val preview = deltas
                if (taker != null && result != null && preview != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (success) successContainer() else failureContainer(),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (success) {
                                    resources.getQuantityString(R.plurals.tarot_contract_success, result.difference, result.difference)
                                } else {
                                    resources.getQuantityString(R.plurals.tarot_contract_failed, -result.difference, -result.difference)
                                },
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.tarot_per_defender, signed(-result.unit)),
                                style = MaterialTheme.typography.bodySmall
                            )
                            players.forEachIndexed { index, name ->
                                Text("$name : ${signed(preview[index])}")
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val roundDeltas = deltas ?: return@Button
                        val takerId = taker ?: return@Button
                        val pointsMade = points ?: return@Button
                        val unit = result?.unit ?: return@Button
                        val attackers = setOfNotNull(takerId, partner)
                        val team = attackers.joinToString(" + ") { players[it] }
                        val label = resources.getString(
                            R.string.tarot_label,
                            team,
                            multiplier.label,
                            context.quantity(R.plurals.tarot_bouts, bouts),
                            pointsMade,
                            resources.getString(if (success) R.string.tarot_label_success else R.string.tarot_label_failed),
                            when {
                                slamSucceeded -> resources.getString(R.string.tarot_label_slam)
                                defenseSlamActive -> resources.getString(R.string.tarot_label_defense_slam)
                                else -> ""
                            }
                        )
                        val round = TeamRound(
                            teamALabel = label,
                            teamAPlayers = attackers,
                            value = unit,
                            tarotInput = TarotRoundInput(
                                taker = takerId,
                                partner = partner,
                                multiplierIndex = multiplierIndex,
                                bouts = bouts,
                                points = pointsMade,
                                petitAuBout = petitAuBout,
                                handful = handful,
                                slamAnnounced = slamAnnounced,
                                defenseSlam = defenseSlam
                            ),
                            deltas = roundDeltas
                        )
                        if (editIndex != null && editing != null) {
                            viewModel.replaceTeamRound(editIndex, round)
                        } else {
                            viewModel.appendTeamRound(round)
                        }
                        viewModel.editingTeamRoundIndex = null
                        onDone()
                    },
                    enabled = canValidate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(if (editing != null) R.string.tarot_submit_edit else R.string.validate_round))
                }
            }
        }
    }
}
