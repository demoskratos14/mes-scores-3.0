package com.aventure.messcores

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat

/** Langue actuellement affichée par l'appli : choix fait dans l'appli, sinon réglage du téléphone. */
fun currentAppLanguage(configuration: Configuration): AppLanguage =
    AppLanguage.resolve((0 until configuration.locales.size()).map { configuration.locales[it].language })

/**
 * Change la langue de l'appli. Android recrée l'écran et recharge tous les textes ; le choix est retenu
 * d'un lancement à l'autre (par le système sur Android 13+, par AppCompat avant), et il apparaît aussi dans
 * Réglages > Applications > Mes Scores > Langue.
 */
fun setAppLanguage(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
}

/**
 * Bouton « drapeau » : affiche le drapeau de la langue en cours ; un appui ouvre la liste des langues
 * (drapeau + nom), et choisir l'une d'elles change aussitôt la langue de l'appli.
 */
@Composable
fun LanguageSelector(modifier: Modifier = Modifier) {
    val current = currentAppLanguage(LocalConfiguration.current)
    var expanded by remember { mutableStateOf(false) }
    val description = stringResource(R.string.language_change)

    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = description }
        ) {
            // Le drapeau est décoratif pour TalkBack : c'est la description du bouton qui est lue.
            Text(
                text = current.flag,
                fontSize = 28.sp,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.entries.forEach { language ->
                val isCurrent = language == current
                DropdownMenuItem(
                    text = { Text(stringResource(language.nameRes)) },
                    leadingIcon = {
                        Text(
                            text = language.flag,
                            fontSize = 22.sp,
                            modifier = Modifier.clearAndSetSemantics { }
                        )
                    },
                    trailingIcon = if (isCurrent) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    modifier = Modifier.semantics { selected = isCurrent },
                    onClick = {
                        expanded = false
                        if (!isCurrent) setAppLanguage(language)
                    }
                )
            }
        }
    }
}
