# Mes Scores

Application Android (Kotlin + Jetpack Compose) pour noter les scores de vos jeux de société : tableaux par manches, compteurs, Tarot avec calcul automatique, championnats (élimination directe, tirage au sort ou ordre choisi, ou poules), jeux personnalisés, journal des parties (avec export/import JSON), statistiques (victoires, meilleur score, moyenne), thème clair / sombre, langue au choix depuis l'appli (drapeau sur l'écran d'accueil : français, anglais, espagnol, portugais, italien), partage du classement, annulation de la dernière saisie et minuteur.

- Identifiant : `com.aventure.messcores`
- minSdk 26, compileSdk/targetSdk 37
- Kotlin 2.3.10 (plugin Compose), Android Gradle Plugin 9.2.1, Gradle 9.8.1 (via le wrapper) ; Kotlin intégré à AGP et nouveau DSL (plus de plugin `org.jetbrains.kotlin.android`, ni de `applicationVariants`) : le nom des APK vient de `base.archivesName`

## Compiler

Le wrapper Gradle est inclus (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) : aucune installation de Gradle n'est nécessaire, seulement un JDK 17.

```
./gradlew assembleDebug      # APK de test  -> app/build/outputs/apk/debug/Mes scores-debug.apk
./gradlew assembleRelease    # APK minifié  -> app/build/outputs/apk/release/Mes scores-release.apk
```

## Tests

```
./gradlew testDebugUnitTest
```

Les tests unitaires (`app/src/test/`) couvrent notamment les noms en double (`PlayerNames`), le texte de partage (`ResultText`), la sauvegarde du journal (`JournalBackup`), l'annulation de saisie, ainsi que le calcul du Tarot (`TarotScoring.kt` : 3, 4 et 5 joueurs, garde sans, primes, chelems, somme des scores toujours nulle) et les règles de classement et de fin de partie (`ScoreViewModel` : seuil, manche à terminer, égalité, nombre de manches), les statistiques (`StatsCalculator`) et les formats de championnat (`TournamentFormatsTest` : calendrier des poules, classement et départage, tableau suivant l'ordre de la liste).

Sous Linux/macOS, si `gradlew` n'est pas exécutable après un clonage : `chmod +x gradlew`
(ou, une fois pour toutes : `git update-index --chmod=+x gradlew`).

Pour régénérer le wrapper : `gradle wrapper --gradle-version 9.8.1`.

## GitHub Actions

Le workflow `.github/workflows/build.yml` lance les tests unitaires et le lint (`./gradlew lintDebug`), puis construit l'APK debug à chaque push sur `main` et le publie comme artefact. Il vérifie aussi que la release (minifiée par R8) se construit, sans la publier. Le rapport de lint est publié en artefact à chaque exécution.

Le fichier `.github/dependabot.yml` demande à Dependabot de proposer chaque semaine les mises à jour de Kotlin, Compose, AndroidX, du plugin Android et de Gradle (et chaque mois celles des actions GitHub).

Pour obtenir un APK **release signé**, créez dans *Settings > Secrets and variables > Actions* :
`KEYSTORE_BASE64` (votre fichier `.jks` encodé en base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`,
puis lancez le workflow à la main (*Run workflow*) en cochant « release ».
En local, la signature se configure avec les mêmes noms en variables d'environnement, plus `KEYSTORE_FILE` (chemin du `.jks`).
Sans ces variables, la release est construite non signée. Gardez votre clé de signature en lieu sûr : sans elle, impossible de publier des mises à jour.

## Structure du projet

```
MesScores/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/
├── .github/dependabot.yml
├── .github/workflows/build.yml
└── app/
    ├── build.gradle.kts, proguard-rules.pro
    └── src/
        ├── test/java/com/aventure/messcores/   TarotScoringTest.kt, ScoreViewModelTest.kt, TournamentEngineTest.kt,
        │                                       TournamentFormatsTest.kt, StatsCalculatorTest.kt,
        │                                       PlayerNamesTest.kt, ResultTextTest.kt, JournalBackupTest.kt,
        │                                       CustomGameDraftTest.kt
        └── main/
            ├── AndroidManifest.xml
            ├── java/com/aventure/messcores/
            │   ├── MainActivity.kt            navigation, sauvegarde automatique
            │   ├── ScoreViewModel.kt          état de la partie en cours
            │   ├── GameRules.kt, GameRulesText.kt   règles des jeux et fiches de règles
            │   ├── TarotScoring.kt            calcul d'une manche de Tarot (fonction pure, testée)
            │   ├── GameRepository.kt          jeux prédéfinis et personnalisés
            │   ├── GameHistory.kt, GameHistoryScreen.kt   journal des parties
            │   ├── JournalBackup.kt           export/import JSON du journal (fonctions pures, testées)
            │   ├── Stats.kt, StatsScreen.kt   statistiques tirées du journal (calcul pur, testé) et écran
            │   ├── Theme.kt                   thème clair / sombre (Auto, Clair, Sombre) et couleurs adaptées
            │   ├── AppLanguage.kt, LanguageSelector.kt   langues proposées et bouton drapeau (AppCompatDelegate.setApplicationLocales)
            │   ├── PlayerNames.kt, ResultText.kt   noms uniques, texte de partage (fonctions pures, testées)
            │   ├── ScoreActions.kt            barre Annuler / Partager / Enregistrer
            │   ├── SetupScreen.kt, ChooseGameScreen.kt, CreateGameScreen.kt
            │   ├── CustomGameDraft.kt         formulaire de création -> GameRules (fonction pure, testée)
            │   ├── ScoreScreen.kt, CounterScreen.kt       tableau par manches / compteurs
            │   ├── TeamRoundsScreen.kt, NewTeamRoundScreen.kt, NewTarotRoundScreen.kt
            │   ├── TournamentEngine.kt        règles des championnats (tableau, poules) — pures, testées
            │   ├── TournamentSetupScreen.kt, TournamentBracketScreen.kt, TournamentViewModel.kt
            │   ├── TimerViewModel.kt, TimerOverlay.kt, TimerAlert.kt, TimerReceiver.kt   minuteur
            │   ├── AppBackground.kt, KeepScreenOn.kt, Savers.kt
            └── res/
                ├── values/strings.xml, values-en|es|pt|it/strings.xml   textes (français par défaut + 4 traductions)
                ├── xml/locales_config.xml    langues de l'appli pour Réglages Android (13+) — à garder alignée sur AppLanguage
                ├── xml/data_extraction_rules.xml, backup_rules.xml   sauvegarde Android (SharedPreferences)
                ├── drawable-nodpi/bg_board_games.webp
                ├── mipmap-anydpi/ic_launcher.xml, ic_launcher_round.xml   icône adaptative (+ monochrome)
                └── mipmap-*/ic_launcher_foreground.png
```

## Jeux personnalisés

Le formulaire de création (`CreateGameScreen`) donne accès à toutes les mécaniques des jeux prédéfinis ; `CustomGameDraft.toRules` en tire des `GameRules` cohérents (une mécanique impose ses propres réglages, comme pour les jeux prédéfinis) :

- **Nombre de joueurs** minimum et maximum (vide = 1 à 12 ; le Tarot est borné à 3–5).
- **Mode de score** : tableau de manches, compteur (+1/−1, « le plus bas gagne » possible) ou équipes variables.
- **Mécanique** (mode tableau) : *feuille de catégories* (Yams, Qwixx, Bowling), *annonce et plis* (Wizard, Oh Hell), *score exact* avec repli (Mölkky), *décompte* jusqu'à 0 (fléchettes, avec coefficients Double/Triple possibles), ou *suivi des phases* (Phase 10). En mode équipes variables : *calcul du Tarot* (contrats, bouts, primes, chelem).
- **Règles de score** (coefficient + bonus), **fin de partie** (nombre de manches, score seuil dans un sens ou l'autre, arrêt immédiat ou fin de manche, manche décisive) : proposées là où elles ont un sens ; pour une feuille, un décompte, un score exact ou des phases, c'est la mécanique qui décide de la fin.
- Pour ajouter une mécanique : un cas dans `GameVariant`, son réglage dans `CustomGameDraft.toRules`, ses champs dans `CreateGameScreen` et ses textes `create_variant_*` dans les 5 langues.

## Statistiques, apparence, formats de championnat

- **Statistiques** (écran d'accueil → *Statistiques*) : calculées à partir des parties **terminées** du journal (donc des 50 dernières au plus). Victoires et parties par joueur, tous jeux confondus ou jeu par jeu ; pour un jeu précis, meilleur score (le plus bas si « le plus bas gagne ») et moyenne. Un joueur est reconnu par son nom, sans tenir compte des majuscules ni des espaces. En cas d'égalité en tête, chaque joueur à égalité compte une victoire ; si tout le monde est à égalité, personne ne gagne.
- **Apparence** (écran d'accueil) : *Auto* suit le réglage du téléphone, ou on force *Clair* / *Sombre*. Le choix est mémorisé. Les couleurs des cartes, bandeaux et scores viennent de `Theme.kt` : n'écrivez plus de `Color.White` ou de couleur en dur pour un fond de carte, utilisez `cardSurface()`, `highlightContainer()`, etc.
- **Langue** (écran d'accueil) : par défaut l'appli suit la langue du téléphone ; le drapeau en haut de la carte affiche la langue en cours et permet d'en choisir une autre (français, anglais, espagnol, portugais, italien). Le choix passe par `AppCompatDelegate.setApplicationLocales` (d'où `AppCompatActivity` et le thème `Theme.AppCompat`) : Android 13+ le retient lui-même et l'affiche dans Réglages > Applications > Langue, les versions antérieures s'appuient sur le service `AppLocalesMetadataHolderService` du manifeste. Pour ajouter une langue : un dossier `res/values-xx`, une entrée dans `AppLanguage`, un `<locale>` dans `res/xml/locales_config.xml` et la chaîne `language_name_xx` (`AppLanguageTest` vérifie l'alignement).
- **Accessibilité** : le badge du minuteur est annoncé comme un bouton avec le temps « à voix haute » et son état ; « Temps écoulé ! » est annoncé dès qu'il apparaît ; les cases de score, de nom et les choix de vainqueur ont un rôle et une action décrits ; les interrupteurs et cases à cocher forment une seule cible avec leur texte (ligne `toggleable`, case sans `onCheckedChange`), comme les choix de `TournamentSetupScreen` ; le signe +/− et le coefficient d'une case ont un rôle de bouton et une action décrite. Les icônes à `contentDescription = null` sont décoratives (un texte les accompagne) : à garder ainsi.
- **Sauvegarde Android** : les données de l'appli (jeux personnalisés, journal, championnat, noms, thème) sont dans des SharedPreferences, incluses dans la sauvegarde cloud et le transfert vers un nouvel appareil (`res/xml/`). Si l'appli écrit un jour des fichiers ou une base de données, il faudra ajouter leur domaine dans ces deux fichiers.
- **Poules** : chacun rencontre tous les autres (12 participants au maximum), matchs répartis en journées, avec un repos par journée si le nombre est impair. Classement aux victoires ; à égalité, victoires dans les matchs entre les joueurs à égalité ; sinon ex æquo. Pas de match nul pour l'instant.
- **Élimination directe, ordre de la liste** : le 1er affronte le 2e, le 3e le 4e… ; s'il manque des joueurs pour remplir le tableau, les derniers de la liste sont qualifiés d'office.
- Un championnat sauvegardé avant cette version reste lisible (il est considéré comme de l'élimination directe).
