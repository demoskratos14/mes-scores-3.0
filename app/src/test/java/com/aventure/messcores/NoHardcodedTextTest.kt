package com.aventure.messcores

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fou pour la traduction : tout texte visible doit venir de res/values/strings.xml.
 * Le test parcourt le code de l'appli et échoue si un texte français est écrit en dur dans un
 * composant (Text, titre, description pour TalkBack…).
 */
class NoHardcodedTextTest {

    // Valeurs interpolées ($nom ou ${...}) : retirées avant l'analyse, elles ne sont pas du texte.
    private val interpolation = Regex("""\$\{(?:[^{}]|\{[^{}]*\})*\}|\$\w+""")

    // Appels ou paramètres dont une valeur littérale serait vue par l'utilisateur.
    private val visibleText = Regex(
        """(?:\bText\(\s*|\b(?:text|title|subtitle|contentDescription|onClickLabel|stateDescription|placeholder|hint)\s*=\s*)\{?\s*"((?:[^"\\]|\\.)*)\x22"""
    )

    private val word = Regex("[A-Za-zÀ-ÿ]{3,}")

    @Test
    fun aucunTexteVisibleNEstEcritEnDur() {
        val root = File("src/main/java")
        assertTrue("dossier introuvable : ${root.absolutePath}", root.isDirectory)
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = interpolation.replace(line.substringBefore("//"), "_")
                    visibleText.findAll(code).firstOrNull { word.containsMatchIn(it.groupValues[1]) }?.let {
                        "${file.name}:${index + 1}: ${line.trim()}"
                    }
                }
            }
            .toList()
        assertTrue("Textes en dur à déplacer dans strings.xml :\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
