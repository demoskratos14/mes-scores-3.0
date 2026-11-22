package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table du Loup-garou, à afficher aux joueurs. */
internal enum class WerewolfNotice { NONE, HOST_LEFT }

/**
 * La table du Loup-garou telle qu'un joueur la reçoit (voir [WerewolfRound.viewFor]) : sa carte, ce qu'il a le droit de savoir
 * des autres, et ce qu'on lui demande de faire. Les cartes des autres joueurs n'y sont que si elles sont retournées.
 */
internal data class WerewolfView(
    val names: List<String>,
    val seat: Int,
    val myRole: Int,
    val alive: List<Boolean>,
    /** Le rôle (ordinal de [WerewolfRole]) des cartes retournées, -1 pour une carte cachée. */
    val shown: List<Int>,
    val step: WerewolfStep,
    val night: Int,
    val prompt: WerewolfPrompt,
    val candidates: List<Int>,
    /** Les loups (pour un loup). */
    val mates: List<Int>,
    /** Mon amoureux (-1 : aucun). */
    val lover: Int,
    /** Pour la Voyante : des paires place, rôle. */
    val seerFound: List<Int>,
    /** Pour un loup : le choix de chaque loup (-1 : pas encore). */
    val picks: List<Int>,
    val wolfDisagree: Boolean,
    val witchVictim: Int,
    val healLeft: Boolean,
    val poisonLeft: Boolean,
    val myVote: Int,
    val voted: Int,
    val voters: Int,
    val canVote: Boolean,
    val marked: Int,
    val tally: List<Int>,
    val voteOf: List<Int>,
    val outcome: WerewolfVoteOutcome,
    val executed: Int,
    val lastDeaths: List<Int>,
    val winner: WerewolfWinner,
    /** Augmente quand mon téléphone doit vibrer. */
    val alert: Int,
    /** Voleur : les deux cartes de la table (ordinaux de [WerewolfRole]). */
    val thiefCards: List<Int>,
    val thiefForced: Boolean,
    /** Renard : la place flairée cette nuit (-1 : aucune) et s'il y a un loup parmi elle et ses voisins. */
    val foxSeat: Int,
    val foxFound: Boolean,
    /** Mes sœurs ou mes frères. */
    val siblings: List<Int>,
    /** Les joueurs charmés par le Joueur de flûte (que seuls les charmés et lui connaissent). */
    val charmedSeats: List<Int>,
    /** Vrai si je suis devenu loup-garou (infecté, Enfant sauvage ou Chien-loup). */
    val converted: Boolean,
    /** Comédien : les cartes de rôle qu'il peut encore emprunter. */
    val comedianLeft: List<Int>,
    /** Servante dévouée : le condamné dont elle peut prendre la carte. */
    val maidTarget: Int,
    val captain: Int,
    /** Abominable sectaire : le groupe (0 ou 1) de chaque joueur ; vide sans lui. */
    val groups: List<Int>,
    /** Pyromane : les joueurs dont le bâtiment a brûlé (ils sont devenus Vagabonds, sans pouvoir). */
    val vagabonds: List<Boolean>,
    /** Gitane : la carte Spiritisme qu'elle a sous les yeux cette nuit (-1 : aucune). */
    val gypsyCard: Int,
    /** La question Spiritisme posée ce matin (carte * [SPIRIT_QUESTIONS] + numéro, -1 : aucune), celui qui la pose et la réponse (-1 : pas encore). */
    val spiritQuestion: Int,
    val spirite: Int,
    val spiritAnswer: Int,
    val canJudge: Boolean,
    val secondVote: Boolean,
    /** Enfant sauvage : mon modèle. */
    val model: Int
) {
    val role: WerewolfRole get() = WerewolfRole.entries[myRole.coerceIn(0, WerewolfRole.entries.size - 1)]

    /** Le rôle de la carte retournée de la place [seat], ou null si elle est cachée. */
    fun shownRole(seat: Int): WerewolfRole? = shown.getOrNull(seat)?.takeIf { it >= 0 }?.let { WerewolfRole.entries.getOrNull(it) }
}

/**
 * Messages du Loup-garou chacun sur son téléphone (voir [WerewolfOnline]). Le narrateur (l'organisateur) tient la partie et
 * envoie à chaque joueur sa vue après chaque changement ; les joueurs ne lui envoient que leurs actions.
 */
internal sealed interface WerewolfMessage {
    /** Narrateur → joueur : les joueurs à la table, la place [seat] du destinataire, et sa vue une fois la partie lancée. */
    data class Table(
        val names: List<String>,
        val seat: Int,
        val notice: WerewolfNotice,
        val view: WerewolfView?
    ) : WerewolfMessage

    /** Joueur → narrateur : une action (voir [WerewolfRound.perform]). */
    data class Act(val action: WerewolfAction) : WerewolfMessage
}

private fun List<Int>.toJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

private fun JSONObject.intList(key: String): List<Int> = optJSONArray(key)?.ints() ?: emptyList()

private fun WerewolfView.toJson(): JSONObject = JSONObject()
    .put("nm", JSONArray().also { array -> names.forEach { array.put(it) } })
    .put("s", seat).put("ro", myRole)
    .put("al", JSONArray().also { array -> alive.forEach { array.put(it) } })
    .put("sh", shown.toJson())
    .put("st", step.name).put("n", night).put("pr", prompt.name)
    .put("ca", candidates.toJson()).put("ma", mates.toJson()).put("lo", lover)
    .put("sf", seerFound.toJson()).put("pk", picks.toJson()).put("wd", wolfDisagree)
    .put("wv", witchVictim).put("hl", healLeft).put("pl", poisonLeft)
    .put("mv", myVote).put("vd", voted).put("vs", voters).put("cv", canVote).put("mk", marked)
    .put("ta", tally.toJson()).put("vo", voteOf.toJson())
    .put("oc", outcome.name).put("ex", executed)
    .put("ld", lastDeaths.toJson()).put("w", winner.name).put("al2", alert)
    .put("tc", thiefCards.toJson()).put("tf", thiefForced).put("fs", foxSeat).put("ff", foxFound)
    .put("sb", siblings.toJson()).put("ch", charmedSeats.toJson()).put("cv2", converted).put("cl", comedianLeft.toJson())
    .put("mt", maidTarget).put("cp", captain).put("gr", groups.toJson())
    .put("vg", JSONArray().also { array -> vagabonds.forEach { array.put(it) } })
    .put("gc", gypsyCard).put("sq", spiritQuestion).put("sp", spirite).put("sa", spiritAnswer)
    .put("cj", canJudge).put("sv", secondVote).put("md", model)

private fun werewolfViewFromJson(json: JSONObject): WerewolfView {
    val namesArray = json.getJSONArray("nm")
    val count = namesArray.length().coerceAtMost(WEREWOLF_MAX_PLAYERS)
    val aliveArray = json.getJSONArray("al")
    return WerewolfView(
        names = (0 until count).map { namesArray.getString(it) },
        seat = json.getInt("s").coerceIn(0, (count - 1).coerceAtLeast(0)),
        myRole = json.getInt("ro"),
        alive = (0 until count).map { aliveArray.optBoolean(it, true) },
        shown = json.intList("sh"),
        step = WerewolfStep.valueOf(json.getString("st")),
        night = json.getInt("n"),
        prompt = WerewolfPrompt.valueOf(json.getString("pr")),
        candidates = json.intList("ca"),
        mates = json.intList("ma"),
        lover = json.optInt("lo", -1),
        seerFound = json.intList("sf"),
        picks = json.intList("pk"),
        wolfDisagree = json.optBoolean("wd", false),
        witchVictim = json.optInt("wv", -1),
        healLeft = json.optBoolean("hl", false),
        poisonLeft = json.optBoolean("pl", false),
        myVote = json.optInt("mv", -1),
        voted = json.optInt("vd", 0),
        voters = json.optInt("vs", 0),
        canVote = json.optBoolean("cv", true),
        marked = json.optInt("mk", -1),
        tally = json.intList("ta"),
        voteOf = json.intList("vo"),
        outcome = WerewolfVoteOutcome.valueOf(json.getString("oc")),
        executed = json.optInt("ex", -1),
        lastDeaths = json.intList("ld"),
        winner = WerewolfWinner.valueOf(json.getString("w")),
        alert = json.optInt("al2", 0),
        thiefCards = json.intList("tc"),
        thiefForced = json.optBoolean("tf", false),
        foxSeat = json.optInt("fs", -1),
        foxFound = json.optBoolean("ff", false),
        siblings = json.intList("sb"),
        charmedSeats = json.intList("ch"),
        converted = json.optBoolean("cv2", false),
        comedianLeft = json.intList("cl"),
        maidTarget = json.optInt("mt", -1),
        captain = json.optInt("cp", -1),
        groups = json.intList("gr"),
        vagabonds = json.optJSONArray("vg")?.let { array -> (0 until array.length()).map { array.optBoolean(it, false) } } ?: emptyList(),
        gypsyCard = json.optInt("gc", -1),
        spiritQuestion = json.optInt("sq", -1),
        spirite = json.optInt("sp", -1),
        spiritAnswer = json.optInt("sa", -1),
        canJudge = json.optBoolean("cj", false),
        secondVote = json.optBoolean("sv", false),
        model = json.optInt("md", -1)
    )
}

internal fun WerewolfMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is WerewolfMessage.Table -> {
            json.put("k", "table")
                .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
                .put("seat", seat).put("notice", notice.name)
            if (view != null) json.put("view", view.toJson())
        }
        is WerewolfMessage.Act -> json.put("k", "act").put("kind", action.kind.name).put("a", action.a).put("b", action.b)
    }
    return json
}

internal fun werewolfMessageFromJson(json: JSONObject): WerewolfMessage? = when (json.getString("k")) {
    "table" -> {
        val names = json.getJSONArray("names")
        val count = names.length().coerceAtMost(WEREWOLF_MAX_PLAYERS)
        WerewolfMessage.Table(
            names = (0 until count).map { names.getString(it) },
            seat = json.getInt("seat").coerceIn(0, (count - 1).coerceAtLeast(0)),
            notice = WerewolfNotice.valueOf(json.getString("notice")),
            view = json.optJSONObject("view")?.let { werewolfViewFromJson(it) }
        )
    }
    "act" -> WerewolfMessage.Act(
        WerewolfAction(WerewolfActionKind.valueOf(json.getString("kind")), json.getInt("a"), json.getInt("b"))
    )
    else -> null
}
