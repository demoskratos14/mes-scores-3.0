package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table, à afficher aux joueurs. */
internal enum class MastermindNotice { NONE, PLAYER_LEFT, HOST_LEFT }

/**
 * Messages du Mastermind chacun sur son téléphone (voir [MastermindOnline]). L'organisateur tient l'état public de
 * la partie et la liste des suites ; chaque joueur cherche seul, sur son téléphone, les suites des autres et ne
 * renvoie que ses essais une fois la suite terminée.
 */
internal sealed interface MmMessage {
    /** Organisateur → joueur : les joueurs à la table, ses réglages, et la place [seat] du destinataire. */
    data class Lobby(val names: List<String>, val settings: MastermindSettings, val seat: Int, val notice: MastermindNotice) : MmMessage

    /** Organisateur → tous : l'état public de la partie (les suites non révélées n'y sont pas). */
    data class State(val state: MastermindState) : MmMessage

    /**
     * Joueur → organisateur : ma suite secrète. Elle n'est affichée sur aucun téléphone ; l'organisateur la transmet,
     * cachée, aux autres joueurs, dont les téléphones calculent seuls les réponses à leurs essais.
     */
    data class Secret(val pegs: List<Int>) : MmMessage

    /** Organisateur → joueur : les suites des autres joueurs, à chercher (null à la place du destinataire). */
    data class Secrets(val secrets: List<List<Int>?>) : MmMessage

    /** Joueur → organisateur : tous mes essais pour la suite de [target], une fois trouvée ou les essais épuisés. */
    data class Run(val target: Int, val guesses: List<List<Int>>) : MmMessage
}

private fun List<Int>.toJsonArray(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

private fun JSONArray.toIntList(): List<Int> = (0 until length()).map { getInt(it) }

private fun MastermindSettings.toJson(): JSONObject =
    JSONObject().put("l", codeLength).put("c", colorCount).put("a", maxAttempts)

private fun settingsFromJson(json: JSONObject): MastermindSettings =
    MastermindSettings(json.getInt("l"), json.getInt("c"), json.getInt("a")).coerced()

internal fun MastermindState.toJson(): JSONObject {
    val json = JSONObject()
    json.put("players", JSONArray().also { array -> players.forEach { array.put(it) } })
    json.put("settings", settings.toJson())
    json.put("phase", phase.name)
    json.put("secrets", JSONArray().also { array -> secrets.forEach { array.put(it?.toJsonArray() ?: JSONObject.NULL) } })
    json.put(
        "guesses",
        JSONArray().also { all ->
            guesses.forEach { forTarget ->
                all.put(
                    JSONArray().also { list ->
                        forTarget.forEach { guess ->
                            list.put(
                                JSONObject().put("pegs", guess.pegs.toJsonArray())
                                    .put("w", guess.feedback.wellPlaced).put("m", guess.feedback.misplaced)
                            )
                        }
                    }
                )
            }
        }
    )
    json.put("secretTurn", secretTurn).put("target", target).put("awaiting", awaitingNext)
    json.put("ready", JSONArray().also { array -> ready.forEach { array.put(it) } })
    json.put(
        "results",
        JSONArray().also { all ->
            results.forEach { row ->
                all.put(JSONArray().also { list -> row.forEach { list.put(it ?: JSONObject.NULL) } })
            }
        }
    )
    return json
}

internal fun mastermindStateFromJson(json: JSONObject): MastermindState {
    val names = json.getJSONArray("players")
    val secrets = json.getJSONArray("secrets")
    val guesses = json.getJSONArray("guesses")
    val ready = json.getJSONArray("ready")
    val results = json.optJSONArray("results") ?: JSONArray()
    return MastermindState(
        players = (0 until names.length()).map { names.getString(it) },
        settings = settingsFromJson(json.getJSONObject("settings")),
        phase = MastermindPhase.valueOf(json.getString("phase")),
        secrets = (0 until secrets.length()).map { if (secrets.isNull(it)) null else secrets.getJSONArray(it).toIntList() },
        guesses = (0 until guesses.length()).map { t ->
            val list = guesses.getJSONArray(t)
            (0 until list.length()).map { g ->
                val guess = list.getJSONObject(g)
                MastermindGuess(
                    guess.getJSONArray("pegs").toIntList(),
                    MastermindFeedback(guess.getInt("w"), guess.getInt("m"))
                )
            }
        },
        secretTurn = json.getInt("secretTurn"),
        target = json.getInt("target"),
        awaitingNext = json.getBoolean("awaiting"),
        ready = (0 until ready.length()).map { ready.getBoolean(it) },
        results = (0 until results.length()).map { s ->
            val row = results.getJSONArray(s)
            (0 until row.length()).map { t -> if (row.isNull(t)) null else row.getInt(t) }
        }
    )
}

internal fun MmMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is MmMessage.Lobby -> json.put("k", "lobby")
            .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
            .put("settings", settings.toJson()).put("seat", seat).put("notice", notice.name)
        is MmMessage.State -> json.put("k", "state").put("state", state.toJson())
        is MmMessage.Secret -> json.put("k", "secret").put("pegs", pegs.toJsonArray())
        is MmMessage.Secrets -> json.put("k", "secrets")
            .put("secrets", JSONArray().also { array -> secrets.forEach { array.put(it?.toJsonArray() ?: JSONObject.NULL) } })
        is MmMessage.Run -> json.put("k", "run").put("target", target)
            .put("guesses", JSONArray().also { array -> guesses.forEach { array.put(it.toJsonArray()) } })
    }
    return json
}

internal fun mmMessageFromJson(json: JSONObject): MmMessage? = when (json.getString("k")) {
    "lobby" -> {
        val names = json.getJSONArray("names")
        MmMessage.Lobby(
            names = (0 until names.length()).map { names.getString(it) },
            settings = settingsFromJson(json.getJSONObject("settings")),
            seat = json.getInt("seat"),
            notice = MastermindNotice.valueOf(json.getString("notice"))
        )
    }
    "state" -> MmMessage.State(mastermindStateFromJson(json.getJSONObject("state")))
    "secret" -> MmMessage.Secret(json.getJSONArray("pegs").toIntList())
    "secrets" -> {
        val array = json.getJSONArray("secrets")
        MmMessage.Secrets((0 until array.length()).map { if (array.isNull(it)) null else array.getJSONArray(it).toIntList() })
    }
    "run" -> {
        val array = json.getJSONArray("guesses")
        MmMessage.Run(json.getInt("target"), (0 until array.length()).map { array.getJSONArray(it).toIntList() })
    }
    else -> null
}
