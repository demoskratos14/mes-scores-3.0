# Mes Scores

Application Android (Kotlin + Jetpack Compose) pour noter les scores de vos jeux de société : tableaux par manches, compteurs, feuilles de catégories (Yams, Qwixx, Bowling, 7 Wonders, Wingspan, Hanabi), annonce et plis (Wizard, Oh Hell), Tarot, bridge et skat avec calcul automatique de la marque, tournoi de poker (tapis, blinds, enchères), tennis et padel (points, jeux, sets, tie-break, point d'or), championnats (élimination directe, tirage au sort ou ordre choisi, ou poules), jeux personnalisés (partageables par fichier), journal des parties (avec export/import JSON), statistiques (victoires, meilleur score, moyenne), thème clair / sombre, langue au choix depuis l'appli (drapeau sur l'écran d'accueil : français, anglais, espagnol, portugais, italien), couleur de chaque joueur (avec mode daltonien), partage du classement en texte ou en image, annulation et rétablissement des saisies (jusqu'à 100), courbe d'évolution des scores manche après manche (écrans de score et journal), reprise en un appui de la partie en cours depuis l'accueil, et minuteur / chronomètre.

- Identifiant : `com.aventure.messcores`
- minSdk 26, compileSdk/targetSdk 37
- Kotlin 2.4.21 (plugin Compose), Android Gradle Plugin 9.2.1, Gradle 9.8.1 (via le wrapper) ; Kotlin intégré à AGP et nouveau DSL (plus de plugin `org.jetbrains.kotlin.android`, ni de `applicationVariants`) : le nom des APK vient de `base.archivesName`

## Compiler

Le wrapper Gradle est inclus (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) : aucune installation de Gradle n'est nécessaire, seulement un JDK 17.

```
./gradlew assembleDebug      # APK de test  -> app/build/outputs/apk/debug/Mes scores-debug.apk
./gradlew assembleRelease    # APK minifié  -> app/build/outputs/apk/release/Mes scores-release.apk
./gradlew bundleRelease      # AAB pour le Play Store -> app/build/outputs/bundle/release/
```

Le `versionCode` vaut `1` en local ; dans GitHub Actions il reprend le numéro d'exécution du workflow (`GITHUB_RUN_NUMBER`), qui ne fait qu'augmenter : chaque build publié peut donc s'installer par-dessus le précédent et être envoyé au Play Store. `versionName` (« 3.0 ») se change à la main dans `app/build.gradle.kts`, à chaque nouvelle version du projet.

## Tests

```
./gradlew testDebugUnitTest
```

Les tests unitaires (`app/src/test/`) couvrent notamment les noms en double (`PlayerNames`), le texte de partage (`ResultText`) et l'image du classement (`ResultImageTest`), la sauvegarde du journal (`JournalBackup`), l'annulation de saisie, le score du tennis et du padel (`RacketScoringTest`, `RacketViewModelTest` : jeux, avantage, point d'or, tie-break, super tie-break, annulation, reprise depuis le journal), ainsi que le calcul du Tarot (`TarotScoring.kt` : 3, 4 et 5 joueurs, garde sans, primes, chelems, somme des scores toujours nulle), du bridge (`BridgeScoringTest`) et du skat (`SkatScoringTest`), les règles de classement et de fin de partie (`ScoreViewModel`, `ScoreStandingTest`, `ScoreRulesTest`, `MinLeadTest` : seuil, écart minimum, plafond, manche à terminer, égalité, nombre de manches), les feuilles de catégories (`ScoreSheetTest`, `CustomSheetTest`, `WingspanHanabiTest`), le poker (`PokerEngineTest`, `PokerTournamentTest`, `PokerReseatTest` : enchères, pots annexes, blinds, pauses, recaves, gains, réunion de tables), les couleurs des joueurs (`PlayerColorsTest`), la saisie de la durée du minuteur (`CountdownInputTest`), le partage de jeux personnalisés (`CustomGameShareTest`), les statistiques (`StatsCalculator`) et les formats de championnat (`TournamentFormatsTest` : calendrier des poules, classement et départage, tableau suivant l'ordre de la liste).

`NoHardcodedTextTest` vérifie qu'aucun texte visible n'est écrit en dur dans le code (tout passe par `strings.xml`) et `GameCategoryTest` que chaque jeu prédéfini a bien sa rubrique.

Annuler / rétablir (`RedoTest`) et les données de la courbe d'évolution (`ScoreProgressTest`) ont aussi leurs tests unitaires.

Les tests d'interface (`app/src/test/java/com/aventure/messcores/ui/`) utilisent Compose Test avec Robolectric : ils tournent sur la JVM, sans émulateur, dans la même commande. Ils couvrent l'écran d'accueil (`SetupScreenUiTest` : saisie des noms, noms par défaut, nombre de joueurs), l'écran de compteurs (`CounterScreenUiTest` : +1, annuler, rétablir, description TalkBack, enregistrer au journal), la proposition de reprise de la partie en cours (`SetupScreenUiTest`) et un parcours complet accueil → choix du jeu (`AppFlowUiTest`).

Sous Linux/macOS, si `gradlew` n'est pas exécutable après un clonage : `chmod +x gradlew`
(ou, une fois pour toutes : `git update-index --chmod=+x gradlew`).

Pour régénérer le wrapper : `gradle wrapper --gradle-version 9.8.1`.

## GitHub Actions

Le workflow `.github/workflows/build.yml` lance les tests unitaires et le lint (`./gradlew lintDebug`, où un avertissement fait échouer la construction comme une erreur), puis construit la release (minifiée par R8) à chaque push et à chaque pull request vers `main` (Dependabot compris) et la publie comme artefact ; l'APK debug n'est plus construit par le workflow. Le rapport de lint est publié en artefact à chaque exécution.

Le fichier `.github/dependabot.yml` demande à Dependabot de proposer chaque semaine les mises à jour de Kotlin, Compose, AndroidX, du plugin Android et de Gradle (et chaque mois celles des actions GitHub).

Pour obtenir un APK **release signé**, créez dans *Settings > Secrets and variables > Actions* :
`KEYSTORE_BASE64` (votre fichier `.jks` encodé en base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`,
puis lancez le workflow à la main (*Run workflow*) en cochant « release » : il publie l'APK signé et l'AAB signé (format demandé par le Play Store).
En local, la signature se configure avec les mêmes noms en variables d'environnement, plus `KEYSTORE_FILE` (chemin du `.jks`).
Sans ces variables, la release est construite non signée. Gardez votre clé de signature en lieu sûr : sans elle, impossible de publier des mises à jour.

## Avant de livrer une modification

La même liste sert de modèle de pull request (`.github/pull_request_template.md`) ; à parcourir aussi quand on pousse directement sur `main` :

- les tests passent (`./gradlew testDebugUnitTest`) et le lint ne signale rien (`./gradlew lintDebug`) ;
- tout nouveau texte visible est dans `strings.xml` **dans les 5 langues** (français, anglais, espagnol, portugais, italien) ; un nombre suivi d'un mot (« 2 sets ») est un `<plurals>` ;
- un nouveau jeu ou une nouvelle mécanique a ses tests, et sa fiche de règles (`GameRulesText.kt`) ;
- le **README** est à jour : fonctionnalités, liste des tests, structure du projet, versions (Kotlin, AGP, Gradle) ;
- `versionName` est changé si c'est une nouvelle version.

## Structure du projet

```
MesScores/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/
├── .github/dependabot.yml
├── .github/pull_request_template.md
├── .github/workflows/build.yml
└── app/
    ├── build.gradle.kts, proguard-rules.pro
    └── src/
        ├── test/java/com/aventure/messcores/   un fichier de test par module (TarotScoringTest.kt, BridgeScoringTest.kt,
        │                                       PokerEngineTest.kt, ScoreViewModelTest.kt, TournamentEngineTest.kt,
        │                                       CustomGameDraftTest.kt, ResultImageTest.kt…) ; ui/ : tests d'interface
        └── main/
            ├── AndroidManifest.xml
            ├── java/com/aventure/messcores/
            │   ├── MainActivity.kt            navigation, sauvegarde automatique
            │   ├── ScoreViewModel.kt          état de la partie en cours
            │   ├── ScoreModels.kt             CellState, TeamRound, annulation (UndoAction / RedoAction)
            │   ├── ScoreStanding.kt           classement et fin de partie (fonctions pures, testées)
            │   ├── PlayerColors.kt            palette et tirage des couleurs des joueurs
            │   ├── PlayerColorPicker.kt, ColorBlind.kt   choix de la couleur d'un joueur et mode daltonien (code A, B, C… sur chaque couleur)
            │   ├── GameRules.kt, GameRulesText.kt   règles des jeux et fiches de règles
            │   ├── GameCategory.kt            rubriques de la liste de jeux (Classiques, Cartes, Dés, Spéciaux, Autres)
            │   ├── ScoreRules.kt, ScoreSheet.kt   règles de total propres à certains jeux (Mölkky…) et feuilles de catégories
            │   ├── TarotScoring.kt            calcul d'une manche de Tarot (fonction pure, testée)
            │   ├── BridgeScoring.kt, NewBridgeRoundScreen.kt   marque d'une donne de bridge (fonction pure, testée) et sa saisie
            │   ├── SkatScoring.kt, NewSkatRoundScreen.kt       valeur et marque d'une donne de skat (fonction pure, testée) et sa saisie
            │   ├── BidScoring.kt              annonce et plis réalisés (Wizard, Oh Hell)
            │   ├── ContractWidgets.kt         éléments communs aux saisies de Tarot, bridge et skat (points signés, choix exclusifs)
            │   ├── PokerEngine.kt, PokerModels.kt, PokerBlinds.kt, PokerPrizes.kt   tournoi de poker : règles (enchères, pots annexes), types de la partie, blinds, gains — purs, testés
            │   ├── PokerSession.kt            tournoi de poker en cours : partie, annulation, minuteur de blinds (horloge injectable, testé)
            │   ├── PokerScreen.kt, PokerSetupScreen.kt, PokerChips.kt   table de poker, création d'une partie, jetons
            │   ├── PokerTablesDialog.kt, PokerTransfer.kt   réunion de tables par fichier d'échange (export/import des joueurs)
            │   ├── RacketScoring.kt, RacketScreen.kt   score du tennis et du padel (moteur pur, testé) et son écran
            │   ├── GameRepository.kt          jeux personnalisés (SharedPreferences) + accès aux jeux prédéfinis
            │   ├── BuiltInGames.kt            assemble les jeux prédéfinis ; un fichier par rubrique :
            │   │   ClassicGames.kt, CardGames.kt, DiceGames.kt, SpecialGames.kt, OtherGames.kt
            │   ├── GameHistory.kt, GameHistoryScreen.kt   journal des parties
            │   ├── AutoSave.kt                sauvegarde automatique de la partie dans le journal, 2 s après chaque modification (+ toutes les 30 s pour le chrono du poker)
            │   ├── JournalBackup.kt           export/import JSON du journal (fonctions pures, testées)
            │   ├── CustomGameShare.kt         export/import JSON de jeux personnalisés (fonctions pures, testées)
            │   ├── Stats.kt, StatsScreen.kt   statistiques tirées du journal (calcul pur, testé) et écran
            │   ├── ScoreProgress.kt, ScoreChart.kt   courbe d'évolution des scores (données pures, testées) et son dessin
            │   ├── ResultImage.kt, ShareFiles.kt   classement en image PNG et partage de fichiers (FileProvider)
            │   ├── Theme.kt                   thème clair / sombre (Auto, Clair, Sombre) et couleurs adaptées
            │   ├── AppLanguage.kt, LanguageSelector.kt   langues proposées et bouton drapeau (AppCompatDelegate.setApplicationLocales)
            │   ├── PlayerNames.kt, ResultText.kt, Strings.kt   noms uniques, texte de partage (fonctions pures, testées), textes composés hors @Composable
            │   ├── ScoreActions.kt            barre Annuler / Partager / Enregistrer
            │   ├── SetupScreen.kt, ChooseGameScreen.kt, CreateGameScreen.kt
            │   │   CreateGameForm.kt (état du formulaire + conversion en CustomGameDraft), CreateGameSections.kt,
            │   │   CreateGameRulesSections.kt (règles de multiplication, fin de partie), CreateGameWidgets.kt
            │   ├── CustomGameDraft.kt         formulaire de création -> GameRules (fonction pure, testée)
            │   ├── DiceScreen.kt, DiceRules.kt            écran des dés d'un jeu, dés et lancers (`DiceTurn`), Yams, valeur et force des jeux du 421 (`Dice421`)
│   ├── DiceRollAnimation.kt                   animation d'un lancer : les dés tournent puis s'arrêtent un à un (`DiceRollTimingTest`) ; le score, la combinaison ou le total du Cochon attendent la fin
            │   ├── Round421.kt, Dice421Panel.kt           421 tour par tour : moteur de la manche (pur, testé) et son interface
            │   ├── TicTacToe.kt, TicTacToeScreen.kt, TicTacToeOnline.kt   Morpion : règles, écran (un ou deux téléphones) et table en ligne
│   ├── Connect4.kt, Connect4Screen.kt, Connect4Online.kt      Puissance 4 : règles, écran (un ou deux téléphones) et table en ligne
│   ├── Nim.kt, NimScreen.kt, NimOnline.kt, NimProtocol.kt   Jeu des allumettes (Nim) : règles, écran (un ou deux téléphones), table en ligne et messages
│   ├── RockPaperScissors.kt, RockPaperScissorsScreen.kt, RockPaperScissorsOnline.kt, RockPaperScissorsProtocol.kt   Pierre-feuille-ciseaux : règles et séance, écran (un ou deux téléphones), table en ligne et messages
            │   ├── DuoMatch.kt, DuoGameScreen.kt, DuoOnline.kt, DuoProtocol.kt   Jeux à deux joueurs au tour par tour : séance de jeu, écran commun (un ou deux téléphones), table en ligne et messages
            │   ├── Gomoku.kt, GomokuScreen.kt   Gomoku : règles (15 × 15, cinq pierres) et plateau
            │   ├── Dots.kt, DotsScreen.kt   Pipopipette : règles (5 × 5 carrés, un carré fermé fait rejouer) et plateau
            │   ├── Draughts.kt, DraughtsProtocol.kt, DraughtsOnline.kt, DraughtsScreen.kt   Dames (10 × 10) : règles et séance (pures, testées), messages, table en ligne et écran (un ou deux téléphones)
            │   ├── Othello.kt, OthelloScreen.kt, OthelloOnline.kt, OthelloProtocol.kt   Othello (Reversi) : règles pures et séance, écran (un ou deux téléphones), table en ligne et messages
│   ├── HangmanEngine.kt, HangmanViewModel.kt, HangmanOnline.kt, HangmanProtocol.kt, HangmanScreen.kt   Pendu : règles, partie sur un téléphone, table en ligne, messages et écran
            │   ├── ChessEngine.kt, ChessMatch.kt, ChessProtocol.kt, ChessOnline.kt, ChessScreen.kt   Échecs : règles (pures, testées), séance de deux joueurs, messages, table en ligne et écran
            │   ├── WizardChess.kt   Échecs, style « sorcier » : statues dessinées en vectoriel, dalles de pierre, animation de chaque coup (éclats quand une pièce est prise)
            │   ├── BattleshipEngine.kt, BattleshipViewModel.kt, BattleshipOnline.kt, BattleshipProtocol.kt, BattleshipScreen.kt   Touché coulé : règles, partie sur un téléphone, table en ligne, messages et écran
            │   ├── ScoreScreen.kt, CounterScreen.kt       tableau par manches / compteurs
            │   │   ScoreTable.kt (en-tête + corps), ScoreTableFooter.kt (phase, bonus, total),
            │   │   ScoreCell.kt (une case), ScoreEntry.kt (fenêtre de saisie partagée : passage au joueur suivant, flèches), ScoreDialogs.kt (fenêtres de saisie : nombre, choix, annonce, bowling)
            │   ├── TeamRoundsScreen.kt, NewTeamRoundScreen.kt, NewTarotRoundScreen.kt
            │   ├── TournamentEngine.kt        règles des championnats (tableau, poules) — pures, testées
            │   ├── TournamentSetupScreen.kt, TournamentBracketScreen.kt, TournamentViewModel.kt
            │   ├── TimerViewModel.kt, TimerOverlay.kt, TimerAlert.kt, TimerReceiver.kt   minuteur / chronomètre
            │   ├── CountdownInput.kt          saisie de la durée du minuteur (5, 4:30, 2:05:30 ; testée)
            │   ├── AppBackground.kt, KeepScreenOn.kt, Savers.kt
            └── res/
                ├── values/strings.xml, values-en|es|pt|it/strings.xml   textes (français par défaut + 4 traductions)
                ├── xml/locales_config.xml    langues de l'appli pour Réglages Android (13+) — à garder alignée sur AppLanguage
                ├── xml/data_extraction_rules.xml, backup_rules.xml   sauvegarde Android (SharedPreferences)
                ├── xml/file_paths.xml        dossier du cache partagé par FileProvider (images, JSON)
                ├── drawable-nodpi/bg_board_games.webp
                ├── mipmap-anydpi/ic_launcher.xml, ic_launcher_round.xml   icône adaptative (+ monochrome)
                └── mipmap-*/ic_launcher_foreground.png
```

## Jeux personnalisés

Le formulaire de création (`CreateGameScreen`) donne accès à toutes les mécaniques des jeux prédéfinis ; `CustomGameDraft.toRules` en tire des `GameRules` cohérents (une mécanique impose ses propres réglages, comme pour les jeux prédéfinis) :

- **Nombre de joueurs** minimum et maximum (vide = 1 à 12 ; le Tarot est borné à 3–5).
- **Mode de score** : tableau de manches, compteur (+1/−1, « le plus bas gagne » possible) ou équipes variables.
- **Mécanique** (mode tableau) : *feuille de catégories* (Yams, Qwixx, Bowling, ou lignes écrites par l'utilisateur, comme pour 7 Wonders, Wingspan et Hanabi ; le 0 est toujours proposé pour barrer une case), *annonce et plis* (Wizard, Oh Hell), *score exact* avec repli (Mölkky), *décompte* jusqu'à 0 (fléchettes, avec coefficients Double/Triple possibles), ou *suivi des phases* (Phase 10). En mode équipes variables : *calcul du Tarot* (contrats, bouts, primes, chelem).
- **Règles de score** (coefficient + bonus), **fin de partie** (nombre de manches, score seuil dans un sens ou l'autre, écart minimum sur les adversaires comme au ping-pong, plafond de score comme au badminton, arrêt immédiat ou fin de manche, manche décisive) : proposées là où elles ont un sens ; pour une feuille, un décompte, un score exact ou des phases, c'est la mécanique qui décide de la fin.
- **Partage** : un ou plusieurs jeux personnalisés s'exportent dans un fichier JSON (`CustomGameShare`) qu'un autre téléphone peut importer ; un fichier d'une version plus récente de l'appli est refusé.
- Pour ajouter une mécanique : un cas dans `GameVariant`, son réglage dans `CustomGameDraft.toRules`, ses champs dans `CreateGameScreen` et ses textes `create_variant_*` dans les 5 langues.

## Tennis et padel

Deux jeux prédéfinis (`builtin_tennis`, `builtin_padel`, rubrique « Autres ») qui utilisent le mode `ScoreMode.RACKET` et l'écran `RacketScreen`.

- **Camps** : la première moitié de la liste de joueurs forme le camp 1, le reste le camp 2 (1 contre 1, 2 contre 2, ou 2 contre 1). Tennis : 2 à 4 joueurs ; padel : 2 à 4 joueurs également (le padel se joue d'ordinaire à 4). Les joueurs d'un même camp partagent le même score.
- **Saisie** : un gros bouton par camp (« Point pour … »). L'appli compte 15 / 30 / 40, l'avantage, les jeux, les sets, passe au tie-break et désigne le vainqueur. « Annuler » et « Rétablir » retirent / remettent le dernier point, sans limite.
- **Réglages** (modifiables jusqu'au premier point) : 1 set, meilleur des 3 ou meilleur des 5 ; point d'or à 40-40 (sans avantage) ; façon de jouer le set décisif (tie-break à 6-6, super tie-break à 10 points, ou sans tie-break avec 2 jeux d'écart). Le tennis démarre en meilleur des 3, avec avantage et tie-break ; le padel avec le point d'or.
- **Fonctionnement** : toute la partie se déduit de la liste des points gagnés (`RacketMatch.points`, 0 = camp 1, 1 = camp 2) rejouée par `RacketScoring.replay`. Le journal enregistre cette liste et les réglages (`SavedGame.racket`) ; le total de chaque joueur est son nombre de sets gagnés, ce qui sert au classement, aux statistiques et au partage (le texte partagé ajoute le score en chiffres, ex. « Tennis 6-4 3-6 2-1 »).
- Pas encore géré : le suivi du serveur et la courbe d'évolution (il n'y a pas de manches à tracer).

## Poker

Jeu prédéfini `builtin_poker` (`ScoreMode.POKER`, écran `PokerScreen`). Tournoi sans relance de tapis : l'appli ne distribue pas de cartes, elle suit les jetons et l'ordre de parole (`PokerEngine`, moteur pur et testé).

- **Table** : joueurs assis autour d'une table ronde, bouton et blinds désignés ; suivre, passer, relancer (avec relance minimum) ou tapis, pots annexes, classement.
- **Réglages** (`PokerSetupScreen`) : tapis de départ, blinds, durée d'un niveau (0 = blinds fixes), antes, vitesse de montée des blinds (régulière, rapide ou turbo), pause programmée tous les N niveaux, alerte sonore et vibration à chaque changement de niveau, buy-in et répartition des gains par place, recaves jusqu'à un niveau donné.
- **Réunion de tables** (`PokerTablesDialog`, `PokerTransfer`) : entre deux mains, une table exporte ses joueurs (nom, tapis, couleur) dans un fichier, l'autre l'importe ; les arrivants s'asseyent au hasard sans changer l'ordre ni le bouton.
- L'annulation du poker remonte jusqu'à 300 actions (100 ailleurs).

## Tarot, bridge et skat

Trois jeux de cartes avec calcul automatique de la marque, chacun dans une fonction pure testée : `TarotScoring` (3 à 5 joueurs), `BridgeScoring` (deux camps : contrat, contre, vulnérabilité, plis) et `SkatScoring` (jeu, matadors, Hand / Ouvert, annonces). Les écrans de saisie (`NewTarotRoundScreen`, `NewBridgeRoundScreen`, `NewSkatRoundScreen`) partagent les éléments de `ContractWidgets.kt`.

## 421 jouable tour par tour

Le bouton « Dés » du 421 fait jouer la partie dans l'ordre (`Round421`, moteur pur testé par `Round421Test`) :

- **Tirage du premier joueur** : chacun lance un dé à son tour, le plus fort commence, les ex æquo relancent entre eux. On peut aussi désigner le premier joueur directement. Ensuite, c'est le perdant de la manche précédente qui commence.
- **Limite de lancers** : le premier joueur de la manche lance jusqu'à 3 fois et garde son jeu quand il veut ; son nombre de lancers devient la limite de tous les autres (`DiceTurn.rollLimit`). Un joueur ne peut pas « garder son jeu » avant d'avoir lancé.
- **Perdant et jetons automatiques** : à la fin de la manche, le moins bon jeu (`Dice421.strength` : 421, 111, as doubles, brelans, suites, autres) perd. À jeu égal, celui qui a fait le moins de lancers est meilleur, puis celui qui a joué en premier. Pendant la charge (21 jetons au milieu, `Dice421.pot`), le perdant prend au milieu la valeur du meilleur jeu, sans dépasser ce qui reste ; ensuite, pendant la décharge, il la prend au gagnant. Seuls les joueurs qui ont encore des jetons jouent, et la partie s'arrête quand un joueur a tous les jetons (`Dice421.gameLoser`). « Rejouer la manche » rend les jetons et repart du même premier joueur.
- **Plusieurs téléphones** : toute la manche (`Round421.snapshot`) passe dans `DiceTable.turn`, comme le tour de dés des autres jeux ; seul le joueur dont c'est le tour peut agir.
- Le bouton « Règles » est sur la feuille de scores (compteurs), sur l'écran des dés et sur l'écran du Morpion, du jeu des allumettes, des dames, de l'Othello, du Pendu et des échecs (`RulesButton`).

## Couleurs des joueurs et mode daltonien

Chaque joueur a une couleur, tirée au sort ou choisie en touchant son nom (écran de départ, tableaux, Tarot, compteurs). Une couleur déjà prise ne peut pas être reprise : la palette (`PLAYER_COLOR_PALETTE`) compte toujours plus de couleurs que le nombre maximal de joueurs (12). Le mode daltonien (réglage de l'écran d'accueil, mémorisé) inscrit un petit code (A, B, C…) sur chaque couleur.

## Minuteur et chronomètre

Le minuteur (`TimerViewModel`, `TimerOverlay`) accepte `5` (minutes), `4:30` ou `2:05:30`, de 1 seconde à 99:59:59 (`CountdownInput`) et s'affiche au format h:mm:ss. Une alerte sonne à la fin, même si l'appli est en arrière-plan (`TimerAlert`, `TimerReceiver`).

## Partage

- **Classement** : le bouton « Partager » propose le texte (`ResultText`) ou une image PNG (`ResultImage`) ; les fichiers passent par un `FileProvider` (`ShareFiles`, `res/xml/file_paths.xml`), sans permission.
- **Jeux personnalisés** et **joueurs d'une table de poker** : fichiers JSON (voir plus haut).
- **Journal** : export/import JSON (`JournalBackup`).
- **Morpion à deux téléphones** : depuis l'écran du Morpion, « Jouer chacun sur son téléphone » (proposé tant que rien n'a été joué) ouvre une table sur la connexion Nearby de `SyncSession`, comme le Mastermind. L'organisateur joue les croix et tient la partie (`TicTacToeOnline`, `TicTacToeMatch`) ; l'invité, les ronds, envoie ses coups (`TttMessage`) et reçoit l'état complet après chaque changement. L'organisateur vérifie le tour et la case ; chacun peut lancer la manche suivante. Testé sans téléphones avec `FakeTransport` (`TicTacToeOnlineTest`).
- **Puissance 4 à deux téléphones** : même principe que le Morpion (`Connect4Online`, `Connect4Match`, `C4Message`), sur une grille de 7 × 6. L'organisateur joue les rouges et tient la partie ; l'invité, les jaunes, envoie sa colonne et reçoit l'état complet. Testé avec `FakeTransport` (`Connect4OnlineTest`).
- **Jeu des allumettes (Nim)** : 21 allumettes, on en retire 1, 2 ou 3 à tour de rôle et celui qui prend la dernière perd la manche (`Nim`, `NimMatch`). Même principe que le Morpion : un seul téléphone qu'on se passe (le premier joueur change à chaque manche, le score de la séance reste affiché) ou « Jouer chacun sur son téléphone » (proposé tant que rien n'a été joué). L'organisateur est le premier joueur et tient la partie (`NimOnline`) ; l'invité envoie le nombre d'allumettes retirées (`NimMessage.Take`) et reçoit l'état complet après chaque changement ; l'organisateur vérifie le tour et la quantité. Le tas se dessine sur un `Canvas` (les allumettes prises restent en pâle) et se lit comme « N allumettes restantes » avec TalkBack. Testé sans téléphones (`NimTest`, `NimOnlineTest` avec `FakeTransport`).
- **Pierre-feuille-ciseaux** : jeu à deux, en `RpsMatch` (premier à 1, 2, 3 ou 5 manches gagnées, égalités rejouées). Sur un seul téléphone, on se le passe : le choix du premier reste caché derrière un écran « Passe le téléphone à… » jusqu'à ce que le second ait choisi. Chacun sur son téléphone (« Jouer chacun sur son téléphone », proposé tant que rien n'a été joué) : même principe que les autres jeux à deux (`RockPaperScissorsOnline`, `RpsMessage`), mais les choix sont secrets. L'organisateur tient la partie ; la copie envoyée à l'invité remplace le choix de l'organisateur par `HIDDEN` tant que l'invité n'a pas choisi (`RpsMatch.maskedFor`), et l'invité n'envoie son choix qu'à l'organisateur, qui ne le montre qu'à la révélation. Une version « lézard Spock » (cinq choix, dix règles : `RpsMatch.extended`, `RockPaperScissors.EXTENDED_CHOICES`) se choisit avant la première manche, sur un téléphone comme à deux ; la version voyage avec l'état de la partie (`RpsMatch.encode`). Testé sans téléphones (`RockPaperScissorsTest`, `RockPaperScissorsOnlineTest`).
- **Gomoku et Pipopipette** : deux jeux à deux joueurs qui partagent le même écran (`DuoGameScreen`, décrit par un `DuoGameSpec` : nom, règles, couleurs, plateau) et la même table en ligne (`DuoOnline`, une instance par jeu, messages `DuoMessage` dans `SyncMessage.Duo`). Chaque jeu n'écrit que ses règles : une séance (`GomokuMatch`, `DotsMatch`, interface `DuoMatch`) qui sait jouer un coup, lancer la manche suivante et se coder en texte. Le coup est un simple nombre (case ou trait), si bien que l'organisateur vérifie tout (tour, case ou trait libre) sans rien savoir du jeu. Au Gomoku, toucher une intersection la choisit et « Valider le coup » la joue (les cases sont petites) ; à la Pipopipette, un carré fermé donne la main une deuxième fois. Testés sans téléphones (`GomokuTest`, `DotsTest`, `DuoOnlineTest`).
- **Dames** : écran propre (`DraughtsScreen`), à deux joueurs, sur un téléphone (on se le passe) ou chacun sur le sien, comme le Morpion. Règles françaises/internationales sur un damier de 10 × 10 (`Draughts`) : pions qui prennent en avant et en arrière, dames volantes, prise obligatoire et obligation de prendre le plus de pièces ; partie nulle après 25 coups de dames par camp sans prise ni mouvement de pion ; abandon possible. Un coup est un chemin de cases (`DraughtsMove`) : pour une suite de prises on touche chaque case d'arrivée, et le coup n'est joué qu'une fois complet. `DraughtsMatch` tient la partie et le score ; les couleurs s'échangent à chaque partie. À deux téléphones, l'organisateur (`DraughtsOnline`) vérifie chaque coup reçu de l'invité (`DrMessage.Move`) puis renvoie l'état complet ; chacun voit ses pièces en bas du damier. Testé sans téléphones (`DraughtsTest`, dont les valeurs de référence du nombre de coups possibles, et `DraughtsOnlineTest` avec `FakeTransport`).
- **Othello (Reversi)** : même principe que le Morpion (écran propre `OthelloScreen`, `builtin_othello`, deux joueurs). Règles pures et testées dans `Othello` (captures par encadrement dans les 8 directions, coups possibles, comptage) ; `OthelloMatch` tient la séance : grille de 64 cases, couleur à jouer, dernier coup, passe de tour (un joueur sans coup est sauté, l'appli l'annonce), score. La manche s'arrête quand plus personne ne peut jouer ; le plus de pions gagne. Les couleurs s'échangent à chaque manche (les noirs commencent toujours). Sur un téléphone on se le passe, les cases jouables sont marquées d'un point ; chacun sur le sien, l'organisateur (place 0) tient la partie (`OthelloOnline`) et vérifie chaque coup reçu de l'invité (`OthMessage.Move`), puis renvoie l'état complet. Testé sans téléphones (`OthelloTest`, `OthelloOnlineTest` avec `FakeTransport`).
- **Pendu** : écran propre (`HangmanScreen`), à deux joueurs, sur un téléphone (il passe de main en main, le mot est masqué pendant qu'on le saisit : `HangmanViewModel`) ou chacun sur le sien (`HangmanOnline`, sur `SyncSession`). Comme au Mastermind, le code secret ne quitte pas le téléphone de celui qui l'a choisi : il n'envoie que la forme du mot (`HgMessage.Ready`), reçoit les lettres proposées (`Guess`) et répond par leurs places (`Report`) ; à la fin de la manche il dévoile le mot (`Reveal`) et l'autre téléphone vérifie que toutes les réponses étaient honnêtes (`Hangman.isConsistent`). Les rôles s'échangent à chaque manche et le score de la séance reste affiché. Les accents ne comptent pas (« é » se devine avec le E). Testé sans téléphones (`HangmanTest`, `HangmanOnlineTest` avec `FakeTransport`).
- **Échecs** : écran propre (`ChessScreen`), à deux joueurs, sur un téléphone (on se le passe ; « Retourner l'échiquier » aide à jouer face à face) ou chacun sur le sien (`ChessOnline`, sur `SyncSession`, comme le Morpion). Les règles sont dans `Chess` / `ChessGame` (fonctions pures, vérifiées par des positions de référence « perft » dans `ChessTest`) : coups permis, roque, prise en passant, promotion (choix de la pièce), échec et mat, pat, matériel insuffisant, triple répétition et règle des 50 coups (ces nulles sont automatiques). La séance (`ChessMatch`) garde les coups de la manche en notation de coordonnées (`e2e4`, `e7e8q`), qui sont rejoués pour retrouver la position ; les couleurs s'échangent à chaque manche et le score (victoires de chacun, nulles) est gardé. À deux téléphones, l'organisateur (place 0) tient la partie et vérifie chaque coup reçu (`ChessMessage.Move`) ; l'invité peut aussi abandonner ou proposer / accepter une nulle (une proposition est refusée si l'autre joue sans répondre). Sur un seul téléphone, la nulle d'un commun accord se confirme d'un bouton. Le bouton « Passer au style sorcier » (réglage retenu d'un lancement à l'autre, propre à chaque téléphone : il ne change rien à la partie) remplace les figurines par des statues de marbre et d'obsidienne dessinées dans `WizardChess.kt` (aucune image) sur un damier de dalles ; chaque coup glisse d'une case à l'autre, et une pièce prise se brise en éclats (`WizardFx.detect` repère la prise, y compris en passant). Testé sans écran (`WizardChessTest`).
- **Touché coulé** : écran propre (`BattleshipScreen`), à deux joueurs, sur un téléphone (il passe de main en main, l'écran se cache entre les joueurs : `BattleshipViewModel`) ou chacun sur le sien (`BattleshipOnline`, sur `SyncSession`). Aucune flotte ne circule pendant la partie : le tireur envoie la case (`BsMessage.Fire`) et le téléphone du défenseur répond (`Report`) ; à la fin chacun dévoile sa flotte (`Reveal`) et vérifie les réponses reçues (`Battleship.isConsistent`). Les cases se choisissent sur la grille ou par colonne et ligne (boutons), et se lisent par leur nom avec TalkBack. Testé sans téléphones (`BattleshipTest`, `BattleshipOnlineTest` avec `FakeTransport`).

## Statistiques, apparence, formats de championnat

- **Statistiques** (écran d'accueil → *Statistiques*) : calculées à partir des parties **terminées** du journal (donc des 100 dernières au plus). Victoires et parties par joueur, tous jeux confondus ou jeu par jeu ; pour un jeu précis, meilleur score (le plus bas si « le plus bas gagne ») et moyenne. Un joueur est reconnu par son nom, sans tenir compte des majuscules ni des espaces. En cas d'égalité en tête, chaque joueur à égalité compte une victoire ; si tout le monde est à égalité, personne ne gagne.
- **Apparence** (écran d'accueil) : *Auto* suit le réglage du téléphone, ou on force *Clair* / *Sombre*. Le choix est mémorisé. Les couleurs des cartes, bandeaux et scores viennent de `Theme.kt` : n'écrivez plus de `Color.White` ou de couleur en dur pour un fond de carte, utilisez `cardSurface()`, `highlightContainer()`, etc.
- **Langue** (écran d'accueil) : par défaut l'appli suit la langue du téléphone ; le drapeau en haut de la carte affiche la langue en cours et permet d'en choisir une autre (français, anglais, espagnol, portugais, italien). Le choix passe par `AppCompatDelegate.setApplicationLocales` (d'où `AppCompatActivity` et le thème `Theme.AppCompat`) : Android 13+ le retient lui-même et l'affiche dans Réglages > Applications > Langue, les versions antérieures s'appuient sur le service `AppLocalesMetadataHolderService` du manifeste. Pour ajouter une langue : un dossier `res/values-xx`, une entrée dans `AppLanguage`, un `<locale>` dans `res/xml/locales_config.xml` et la chaîne `language_name_xx` (`AppLanguageTest` vérifie l'alignement).
- **Accessibilité** : le badge du minuteur est annoncé comme un bouton avec le temps « à voix haute » et son état ; « Temps écoulé ! » est annoncé dès qu'il apparaît ; les cases de score, de nom et les choix de vainqueur ont un rôle et une action décrits ; les interrupteurs et cases à cocher forment une seule cible avec leur texte (ligne `toggleable`, case sans `onCheckedChange`), comme les choix de `TournamentSetupScreen` ; le signe +/− et le coefficient d'une case ont un rôle de bouton et une action décrite. Les icônes à `contentDescription = null` sont décoratives (un texte les accompagne) : à garder ainsi.
- **Sauvegarde Android** : les données de l'appli (jeux personnalisés, journal, championnat, noms, thème) sont dans des SharedPreferences, incluses dans la sauvegarde cloud et le transfert vers un nouvel appareil (`res/xml/`). Si l'appli écrit un jour des fichiers ou une base de données, il faudra ajouter leur domaine dans ces deux fichiers.
- **Poules** : chacun rencontre tous les autres (12 participants au maximum), matchs répartis en journées, avec un repos par journée si le nombre est impair. Classement aux victoires ; à égalité, victoires dans les matchs entre les joueurs à égalité ; sinon ex æquo. Pas de match nul pour l'instant.
- **Élimination directe, ordre de la liste** : le 1er affronte le 2e, le 3e le 4e… ; s'il manque des joueurs pour remplir le tableau, les derniers de la liste sont qualifiés d'office.
- Un championnat sauvegardé avant cette version reste lisible (il est considéré comme de l'élimination directe).
