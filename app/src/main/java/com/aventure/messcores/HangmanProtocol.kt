package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table, à afficher aux joueurs. */
internal enum class HangmanNotice { NONE, PLAYER_LEFT, HOST_LEFT }

/**
 * Messages du Pendu chacun sur son téléphone (voir [HangmanOnline]). Le mot ne circule jamais pendant la manche :
 * celui qui devine envoie une lettre, c'est le téléphone de celui qui a choisi le mot qui répond (les places de la
 * lettre). Le mot n'est dévoilé qu'à la fin ([Reveal]), pour que chacun vérifie que les réponses étaient honnêtes.
 */
internal sealed interface HgMessage {
    /** Organisateur → invité : les joueurs à la table et les réglages. */
    data class Lobby(val names: List<String>, val settings: HangmanSettings, val notice: HangmanNotice) : HgMessage

    /**
     * Organisateur → invité : une manche commence, le joueur [setter] choisit le mot. [wins] : manches gagnées par
     * chacun jusque-là, [round] : numéro de la manche.
     */
    data class Start(val setter: Int, val wins: List<Int>, val round: Int) : HgMessage

    /** Celui qui choisit → celui qui devine : le mot est choisi (il n'est pas envoyé), voici sa forme (blancs et séparateurs). */
    data class Ready(val shape: String) : HgMessage

    /** Celui qui devine → celui qui choisit : je propose la lettre [letter]. */
    data class Guess(val letter: Char) : HgMessage

    /** Celui qui choisit → celui qui devine : la réponse à la lettre proposée. */
    data class Report(val guess: HangmanGuess) : HgMessage

    /** Fin de manche : le mot, dévoilé à celui qui l'a deviné. */
    data class Reveal(val word: String) : HgMessage

    /** Invité → organisateur : manche suivante, une fois la manche finie. */
    data object Next : HgMessage
}

private fun intsToJson(values: List<Int>): JSONArray = JSONArray().also { array -> values.forEach { array.put(it) } }

private fun jsonToInts(array: JSONArray): List<Int> = (0 until array.length()).map { array.getInt(it) }

/** Une lettre tient dans une chaîne d'un caractère (le JSON n'a pas de type caractère). */
private fun String.toLetter(): Char = single()

internal fun HgMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is HgMessage.Lobby -> json.put("k", "lobby")
            .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
            .put("m", settings.maxMistakes).put("notice", notice.name)
        is HgMessage.Start -> json.put("k", "start").put("setter", setter).put("wins", intsToJson(wins)).put("round", round)
        is HgMessage.Ready -> json.put("k", "ready").put("shape", shape)
        is HgMessage.Guess -> json.put("k", "guess").put("l", letter.toString())
        is HgMessage.Report -> json.put("k", "report").put("l", guess.letter.toString()).put("p", intsToJson(guess.positions))
        is HgMessage.Reveal -> json.put("k", "reveal").put("word", word)
        HgMessage.Next -> json.put("k", "next")
    }
    return json
}

/** Le message codé dans [json], ou null s'il n'est pas du Pendu ; une valeur illisible lève une exception (rattrapée par [SyncCodec.decode]). */
internal fun hgMessageFromJson(json: JSONObject): HgMessage? = when (json.getString("k")) {
    "lobby" -> {
        val names = json.getJSONArray("names")
        HgMessage.Lobby(
            names = (0 until names.length()).map { names.getString(it) },
            settings = HangmanSettings(json.getInt("m")).coerced(),
            notice = HangmanNotice.valueOf(json.getString("notice"))
        )
    }
    "start" -> HgMessage.Start(
        setter = json.getInt("setter").coerceIn(0, 1),
        // Un score absurde est ramené à une valeur permise ; il y a toujours deux scores.
        wins = jsonToInts(json.getJSONArray("wins")).map { it.coerceAtLeast(0) }.let { listOf(it.getOrElse(0) { 0 }, it.getOrElse(1) { 0 }) },
        round = json.getInt("round").coerceAtLeast(1)
    )
    "ready" -> HgMessage.Ready(json.getString("shape"))
    "guess" -> HgMessage.Guess(json.getString("l").toLetter())
    "report" -> HgMessage.Report(HangmanGuess(json.getString("l").toLetter(), jsonToInts(json.getJSONArray("p"))))
    "reveal" -> HgMessage.Reveal(json.getString("word"))
    "next" -> HgMessage.Next
    else -> null
}
