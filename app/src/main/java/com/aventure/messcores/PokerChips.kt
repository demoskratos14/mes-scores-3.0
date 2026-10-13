package com.aventure.messcores

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val CHIP_BLUE = Color(0xFF1E5AA8)
private val CHIP_WHITE = Color(0xFFF7F7F2)
private val CHIP_GREY = Color(0xFFB4B4AC)
private val CHIP_TEXT_DARK = Color(0xFF212121)

/** Nombre de bandes alternées sur la tranche du jeton. */
private const val EDGE_STRIPES = 8

/**
 * Un jeton de poker vu de dessus : disque, bandes alternées sur la tranche, anneau intérieur et
 * [label] au centre. [blue] = jeton bleu à bandes blanches ; sinon jeton blanc à bandes grises.
 * Purement décoratif : l'information est déjà lue par TalkBack dans la description de la place.
 */
@Composable
fun PokerChip(label: String, blue: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val body = if (blue) CHIP_BLUE else CHIP_WHITE
    val stripe = if (blue) CHIP_WHITE else CHIP_GREY
    val textColor = if (blue) Color.White else CHIP_TEXT_DARK
    // Taille du texte liée à celle du jeton, indépendante de la taille de police du téléphone.
    val fontSize = with(LocalDensity.current) { (size.toPx() * 0.34f).toSp() }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            val middle = center
            // Légère ombre portée, puis le corps du jeton.
            drawCircle(Color.Black.copy(alpha = 0.25f), radius = radius, center = middle + Offset(0f, radius * 0.06f))
            drawCircle(body, radius = radius, center = middle)

            // Tranche : une bande sur deux est de l'autre couleur.
            val edgeWidth = radius * 0.22f
            val arcRadius = radius - edgeWidth / 2f
            val sweep = 360f / EDGE_STRIPES / 2f
            for (i in 0 until EDGE_STRIPES) {
                drawArc(
                    color = stripe,
                    startAngle = i * (360f / EDGE_STRIPES) - sweep / 2f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(middle.x - arcRadius, middle.y - arcRadius),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = edgeWidth)
                )
            }
            // Anneau intérieur et contour.
            drawCircle(stripe, radius = radius * 0.64f, center = middle, style = Stroke(width = radius * 0.07f))
            drawCircle(Color.Black.copy(alpha = 0.45f), radius = radius, center = middle, style = Stroke(width = radius * 0.05f))
        }
        Text(
            text = label,
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Les jetons posés devant un joueur : un jeton blanc « B » pour le bouton (dealer), un jeton bleu
 * « SB » pour la petite blind, deux jetons bleus « BB » pour la grosse blind. Rien si aucun
 * des trois ne concerne le joueur (la hauteur est quand même réservée, pour que les places gardent
 * toutes la même taille).
 */
@Composable
fun PokerSeatChips(isDealer: Boolean, isSmallBlind: Boolean, isBigBlind: Boolean, chipSize: Dp) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(chipSize + 2.dp)
    ) {
        if (isDealer) PokerChip(stringResource(R.string.poker_badge_dealer), blue = false, size = chipSize)
        if (isSmallBlind) PokerChip(stringResource(R.string.poker_badge_small), blue = true, size = chipSize)
        if (isBigBlind) {
            // Deux jetons identiques, le second décalé et posé par-dessus le premier.
            val step = chipSize / 2
            Box(modifier = Modifier.width(chipSize + step).height(chipSize)) {
                PokerChip(stringResource(R.string.poker_badge_big), blue = true, size = chipSize)
                PokerChip(
                    stringResource(R.string.poker_badge_big),
                    blue = true,
                    size = chipSize,
                    modifier = Modifier.offset(x = step)
                )
            }
        }
    }
}
