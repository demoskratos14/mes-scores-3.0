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
 * Saisie d'une donne de bridge avec calcul automatique de la marque : camp déclarant, contrat (niveau,
 * couleur, contre), vulnérabilité et plis réalisés. Les deux « joueurs » sont les deux camps ; le camp
 * qui marque la donne reçoit les points, l'autre reste à 0 (voir [computeBridgeContract]).
 */
@Composable
fun NewBridgeRoundScreen(
    viewModel: ScoreViewModel,
    onDone: () -> Unit
) {
    val players = viewModel.players
    val resources = LocalResources.current

    // Si on corrige une donne existante, les champs démarrent avec sa saisie d'origine.
    val editIndex = viewModel.editingTeamRoundIndex
    val editing: BridgeInput? = editIndex
        ?.let { viewModel.teamRounds.getOrNull(it)?.contractInput }
        ?.let { BridgeInput.decode(it) }

    // rememberSaveable : la saisie survit à une rotation de l'écran.
    var declarer by rememberSaveable { mutableStateOf<Int?>(editing?.declarer) }
    var level by rememberSaveable { mutableIntStateOf(editing?.level ?: 1) }
    var suit by rememberSaveable { mutableIntStateOf(editing?.suit ?: BridgeSuit.CLUBS) }
    var doubling by rememberSaveable { mutableIntStateOf(editing?.doubling ?: 0) }
    var vulnerable by rememberSaveable { mutableStateOf(editing?.vulnerable ?: false) }
    var tricksText by rememberSaveable { mutableStateOf(editing?.tricks?.toString() ?: "") }

    val tricks = tricksText.toIntOrNull()?.takeIf { it in 0..13 }
    val suitNames = listOf("♣", "♦", "♥", "♠", stringResource(R.string.bridge_suit_nt))
    val doublingSuffixes = listOf("", " X", " XX")

    // ----- Calcul (voir BridgeScoring.kt) -----
    val declarerIndex = declarer
    val result = tricks?.let { computeBridgeContract(level, suit, doubling, vulnerable, it) }
    val deltas = if (declarerIndex != null && result != null) bridgeDeltas(players.size, declarerIndex, result) else null

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

                Text(stringResource(R.string.bridge_declarer), style = MaterialTheme.typography.titleSmall)
                ContractChoiceRow(players, declarer) { declarer = it }

                Text(stringResource(R.string.bridge_level), style = MaterialTheme.typography.titleSmall)
                ContractChoiceRow(List(7) { (it + 1).toString() }, level - 1) { level = it + 1 }

                Text(stringResource(R.string.bridge_suit), style = MaterialTheme.typography.titleSmall)
                ContractChoiceRow(suitNames, suit) { suit = it }

                Text(stringResource(R.string.bridge_doubling), style = MaterialTheme.typography.titleSmall)
                ContractChoiceRow(
                    listOf(
                        stringResource(R.string.bridge_doubling_none),
                        stringResource(R.string.bridge_doubling_x),
                        stringResource(R.string.bridge_doubling_xx)
                    ),
                    doubling
                ) { doubling = it }

                Text(stringResource(R.string.bridge_vulnerability), style = MaterialTheme.typography.titleSmall)
                ContractChoiceRow(
                    listOf(stringResource(R.string.bridge_vul_no), stringResource(R.string.bridge_vul_yes)),
                    if (vulnerable) 1 else 0
                ) { vulnerable = it == 1 }

                OutlinedTextField(
                    value = tricksText,
                    onValueChange = { tricksText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text(stringResource(R.string.bridge_tricks_label)) },
                    isError = tricksText.isNotEmpty() && tricks == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.bridge_needed, bridgeTricksNeeded(level)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ----- Aperçu du résultat -----
                if (declarerIndex != null && result != null && deltas != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.made) successContainer() else failureContainer(),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (result.made) {
                                    stringResource(R.string.bridge_result_made, result.difference)
                                } else {
                                    stringResource(R.string.bridge_result_down, -result.difference)
                                },
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (result.made) {
                                    stringResource(
                                        R.string.bridge_breakdown_made,
                                        result.contractPoints, result.overtrickPoints,
                                        result.gameBonus, result.slamBonus, result.doubledBonus
                                    )
                                } else {
                                    stringResource(R.string.bridge_breakdown_down, result.penalty)
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
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
                        val outcome = result ?: return@Button
                        val madeTricks = tricks ?: return@Button
                        val label = resources.getString(
                            R.string.bridge_label,
                            players[declarerId],
                            level,
                            suitNames[suit],
                            doublingSuffixes[doubling],
                            resources.getString(if (outcome.made) R.string.bridge_label_made else R.string.bridge_label_down)
                        )
                        val round = TeamRound(
                            teamALabel = label,
                            teamAPlayers = setOf(declarerId),
                            value = outcome.score,
                            deltas = roundDeltas,
                            contractInput = BridgeInput(declarerId, level, suit, doubling, vulnerable, madeTricks).encode()
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
