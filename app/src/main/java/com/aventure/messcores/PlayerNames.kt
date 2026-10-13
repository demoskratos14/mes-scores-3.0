package com.aventure.messcores

/**
 * Noms des joueurs : nettoyage et garantie d'unicité. Fonctions pures (sans Android), testées
 * dans PlayerNamesTest.
 */
object PlayerNames {

    /**
     * Rend tous les noms distincts (sans tenir compte des majuscules ni des espaces autour) :
     * le premier « Marie » reste « Marie », le suivant devient « Marie 2 », puis « Marie 3 »…
     * Un suffixe déjà pris, y compris par un nom saisi plus loin dans la liste (« Marie 2 »
     * tapé à la main), est sauté pour ne jamais créer de nouveau doublon.
     */
    fun makeUnique(names: List<String>): List<String> {
        val cleaned = names.map { it.trim() }
        val typed = cleaned.map { it.lowercase() }.toSet()
        val used = mutableSetOf<String>()
        return cleaned.map { name ->
            var result = name
            if (result.lowercase() in used) {
                var n = 2
                while ("$name $n".lowercase().let { it in used || it in typed }) n++
                result = "$name $n"
            }
            used.add(result.lowercase())
            result
        }
    }

    /**
     * Noms prêts à l'emploi à partir des champs saisis : espaces retirés, champ vide remplacé
     * par « [fallbackPrefix] 1 », « [fallbackPrefix] 2 »… (selon sa position), puis doublons
     * départagés par [makeUnique].
     */
    fun resolve(raw: List<String>, fallbackPrefix: String): List<String> =
        makeUnique(raw.mapIndexed { i, n -> n.trim().ifBlank { "$fallbackPrefix ${i + 1}" } })

    /**
     * Noms modifiés pour devenir uniques, sous la forme (nom saisi, nom final), ex. (« Marie »,
     * « Marie 2 »). Liste vide si tout est déjà distinct. Le texte du message est composé côté
     * interface (voir duplicateNoticeText).
     */
    fun duplicateChanges(raw: List<String>, fallbackPrefix: String): List<Pair<String, String>> {
        val before = raw.mapIndexed { i, n -> n.trim().ifBlank { "$fallbackPrefix ${i + 1}" } }
        val after = makeUnique(before)
        return before.indices.filter { before[it] != after[it] }.map { before[it] to after[it] }
    }
}
