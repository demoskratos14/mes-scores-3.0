package com.aventure.messcores

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * Briques communes des jeux de cartes jouables dans l'appli (Huit américain, Uno…) : le cadre de l'écran, la carte de scores,
 * la carte « passe le téléphone », le résultat d'une manche, une carte à jouer et la grille de la main. Chaque jeu n'écrit
 * que ses règles (un moteur comme [HuitRound]) et la table qui lui est propre.
 */

/** Cartes par ligne dans la main affichée. */
internal const val CARDS_PER_ROW = 5

internal val CardWhite = Color(0xFFFAFAFA)
internal val CardBlocked = Color(0xFFBDBDBD)
internal val CardBlack = Color(0xFF212121)
internal val CardRed = Color(0xFFC62828)

/**
 * Inscrit [points] (un par joueur, dans l'ordre des joueurs) dans la première ligne vide du tableau des scores. Renvoie faux,
 * sans rien écrire, s'il n'y a plus de ligne libre.
 */
internal fun writeRoundPoints(viewModel: ScoreViewModel, points: List<Int>): Boolean {
    val row = viewModel.scores.indexOfFirst { line -> line.all { it.baseValue == null } }
    if (row < 0) return false
    points.forEachIndexed { player, value -> viewModel.setCell(row, player, value, false) }
    return true
}

/**
 * Le cadre de l'écran d'un jeu de cartes jouable : « Retour », la rubrique, le nom du jeu avec son bouton de règles, puis le
 * contenu du jeu dans une zone qui défile. [scrollKeys] : quand l'une de ces valeurs change (autre joueur, nouvelle manche,
 * main dévoilée), la page remonte en haut.
 */
@Composable
internal fun CardGameScreen(
    viewModel: ScoreViewModel,
    onBack: () -> Unit,
    scrollKeys: List<Any?>,
    content: @Composable ColumnScope.() -> Unit
) {
    val rules = viewModel.gameRules
    KeepScreenOn()
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollKeys) { scrollState.animateScrollTo(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.common_back), color = Color.White)
        }
        Text(
            text = stringResource(R.string.category_cards),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.semantics { heading() }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rules.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(10.dp))
            RulesButton(rules = rules)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

/** Une carte de la page : fond de l'appli, marge intérieure et espace entre les lignes. */
@Composable
internal fun CardPanel(spacing: Int = 8, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(spacing.dp), content = content)
    }
}

/** Les totaux de chacun (lus dans le tableau des scores) ; le joueur [current] est en gras, -1 : personne. */
@Composable
internal fun ScoresCard(viewModel: ScoreViewModel, current: Int) {
    val totals = viewModel.totals
    CardPanel(spacing = 2) {
        Text(
            text = stringResource(R.string.dice_sb_scores_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        viewModel.players.forEachIndexed { index, name ->
            val mine = index == current
            Text(
                text = stringResource(R.string.dice_pig_score_line, name, totals.getOrElse(index) { 0 }),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Le téléphone change de mains : la main de [name] reste cachée jusqu'à ce qu'il la demande ([onReveal]). */
@Composable
internal fun PassPhoneCard(name: String, onReveal: () -> Unit) {
    CardPanel(spacing = 10) {
        Text(stringResource(R.string.dice_bunco_turn, name), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.huit_cover_hint, name), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onReveal, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.huit_show_hand))
        }
    }
}

/** La fin de la manche : [title], les points de chacun (déjà inscrits au tableau) et, si [canContinue], la manche suivante. */
@Composable
internal fun RoundResultCard(
    title: String,
    players: List<String>,
    points: List<Int>,
    winner: Int,
    canContinue: Boolean,
    onNext: () -> Unit
) {
    CardPanel {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        players.forEachIndexed { index, name ->
            Text(
                text = stringResource(R.string.huit_points_line, name, points.getOrElse(index) { 0 }),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (index == winner) FontWeight.Bold else FontWeight.Normal
            )
        }
        if (canContinue) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.huit_next_round))
            }
        }
    }
}

/**
 * Une carte à jouer : [text] écrit au centre dans la couleur [textColor], sur le fond [background]. [description] est lue par
 * TalkBack ; [selected] entoure la carte ; [onClick] null : simple affichage.
 */
@Composable
internal fun PlayingCard(
    text: String,
    description: String,
    background: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(0.7f)
            .clip(shape)
            .background(background, shape)
            .border(
                width = if (selected) 4.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                shape = shape
            )
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = description }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

/**
 * Les cartes de la main, [perRow] par ligne : [item] dessine une carte avec le modifier qui lui donne sa largeur. La dernière
 * ligne incomplète garde la largeur des cartes.
 */
@Composable
internal fun <T> CardRows(cards: List<T>, perRow: Int = CARDS_PER_ROW, item: @Composable (T, Modifier) -> Unit) {
    cards.chunked(perRow).forEach { line ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            line.forEach { card -> item(card, Modifier.weight(1f)) }
            repeat(perRow - line.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}
