plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aventure.messcores"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aventure.messcores"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // Robolectric (tests des SharedPreferences) a besoin des ressources de l'appli.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // ./gradlew lintDebug (lancé par le workflow) : les erreurs font échouer la construction,
    // les avertissements apparaissent dans le rapport HTML sans la bloquer.
    lint {
        abortOnError = true
        warningsAsErrors = false
        htmlReport = true
        // Versions des bibliothèques et targetSdk : leur montée de version exige un compileSdk et un AGP plus récents,
        // à faire en une étape à part (avec test sur appareil). En attendant, on évite le bruit dans le rapport.
        disable += setOf("GradleDependency", "OldTargetApi")
    }

    // Renomme le fichier APK généré : "Mes scores-debug.apk", "Mes scores-release.apk", etc.
    // au lieu du nom par défaut "app-debug.apk" basé sur le nom du module.
    applicationVariants.all {
        outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            outputImpl.outputFileName = "Mes scores-${name}.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ViewModel + Navigation en Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Tests unitaires (./gradlew testDebugUnitTest)
    testImplementation("junit:junit:4.13.2")
    // org.json est fourni par Android au runtime, mais ses classes sont de simples coquilles vides
    // dans les tests unitaires JVM : cette version réelle permet de tester le format JSON du journal.
    testImplementation("org.json:json:20240303")
    // Robolectric simule Android sur la JVM : permet de tester ce qui passe par les SharedPreferences
    // (GameRepository, GameHistoryRepository, TournamentViewModel) sans émulateur.
    testImplementation("org.robolectric:robolectric:4.14.1")
}
