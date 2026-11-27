import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Nom des APK générés : "Mes scores-phone-release.apk", "Mes scores-tv-release.apk", etc.
// (au lieu de "app-phone-release.apk" basé sur le nom du module).
base {
    archivesName.set("Mes scores")
}

android {
    namespace = "com.aventure.messcores"
    // Compose 1.13 exige de compiler contre l'API 37.1 (version mineure) ; targetSdk reste 37.
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.aventure.messcores"
        minSdk = 26
        targetSdk = 37
        // versionCode doit augmenter à chaque publication (Play Store / mise à jour par-dessus l'existant) :
        // en CI on reprend le numéro d'exécution du workflow GitHub (toujours croissant), en local 1.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "3.0"
    }

    // Trois applis construites à partir du même code :
    //  - « phone » : l'appli habituelle, sur téléphone ;
    //  - « tv »    : la version pour Google TV / Android TV (accueil en grille des jeux solo, jouée à la manette) ;
    //  - « inter » : « L'Inter », accueil en grille comme la version télé mais avec seulement les jeux de supermarché
    //                (voir INTER_GAME_IDS) ; se joue au toucher sur téléphone comme à la manette sur télé ;
    //                construite uniquement à la main (workflow « Construire L'Inter »), jamais par le build habituel.
    // Elles ont des identifiants différents : on peut installer les trois sur un même appareil. Le code est commun ;
    // BuildConfig.IS_TV dit si l'appli est la version télé (mode TV toujours allumé), BuildConfig.IS_INTER si on est
    // dans « L'Inter » (qui garde le mode TV réglable, allumé de lui-même sur une télé). Les fichiers propres à la télé sont dans app/src/tv, ceux de L'Inter dans app/src/inter.
    flavorDimensions += "target"
    productFlavors {
        create("phone") {
            dimension = "target"
            buildConfigField("boolean", "IS_TV", "false")
            buildConfigField("boolean", "IS_INTER", "false")
        }
        create("tv") {
            dimension = "target"
            applicationIdSuffix = ".tv"
            versionNameSuffix = "-tv"
            buildConfigField("boolean", "IS_TV", "true")
            buildConfigField("boolean", "IS_INTER", "false")
        }
        create("inter") {
            dimension = "target"
            applicationIdSuffix = ".inter"
            versionNameSuffix = "-inter"
            // Pas « TV » : le mode TV reste un réglage de chaque jeu (tactile sur téléphone, allumé tout seul sur une télé).
            buildConfigField("boolean", "IS_TV", "false")
            buildConfigField("boolean", "IS_INTER", "true")
        }
    }

    // Signature release : les infos viennent de variables d'environnement (jamais du dépôt).
    // Sans elles, la release est construite non signée (le debug reste signé automatiquement).
    val keystorePath = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // BuildConfig.IS_TV et IS_INTER (voir les variantes ci-dessus).
        buildConfig = true
    }

    // Robolectric (tests des SharedPreferences) a besoin des ressources de l'appli.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // ./gradlew lintDebug (lancé par le workflow) : erreurs ET avertissements font échouer la
    // construction, pour garder le rapport vide. Un avertissement jugé acceptable se désactive
    // explicitement (disable += "NomDeLaRègle") ou se corrige ; ne pas repasser à false.
    lint {
        abortOnError = true
        warningsAsErrors = true
        htmlReport = true
    }
}

// Kotlin intégré à AGP 9 (plus de plugin org.jetbrains.kotlin.android) ; compilerOptions remplace kotlinOptions.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    // Choix de la langue depuis l'appli (AppCompatDelegate.setApplicationLocales) : fonctionne sur Android 13+
    // via le système, et est rétroporté jusqu'à minSdk 26 par AppCompat.
    implementation("androidx.appcompat:appcompat:1.8.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ViewModel + Navigation en Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // Partage d'une partie entre deux téléphones sans internet (Bluetooth et connexion directe).
    // Les services Google Play sont nécessaires sur les deux téléphones.
    implementation("com.google.android.gms:play-services-nearby:19.5.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Tests unitaires (./gradlew testDebugUnitTest)
    testImplementation("junit:junit:4.13.2")
    // org.json est fourni par Android au runtime, mais ses classes sont de simples coquilles vides
    // dans les tests unitaires JVM : cette version réelle permet de tester le format JSON du journal.
    testImplementation("org.json:json:20260814")
    // Robolectric simule Android sur la JVM : permet de tester ce qui passe par les SharedPreferences
    // (GameRepository, GameHistoryRepository, TournamentViewModel) sans émulateur.
    testImplementation("org.robolectric:robolectric:4.18.1")
    // Tests d'interface Compose, exécutés sur la JVM avec Robolectric (pas d'émulateur : ils tournent
    // dans le même ./gradlew testDebugUnitTest que les autres). La version vient du BOM Compose.
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // Déclare l'activité hôte utilisée par createComposeRule() dans le manifeste de la variante debug.
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
