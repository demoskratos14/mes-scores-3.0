import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aventure.messcores"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aventure.messcores"
        minSdk = 26
        targetSdk = 37
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

// Kotlin : le DSL kotlinOptions est supprimé depuis Kotlin 2.2, on utilise compilerOptions.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ViewModel + Navigation en Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Tests unitaires (./gradlew testDebugUnitTest)
    testImplementation("junit:junit:4.13.2")
    // org.json est fourni par Android au runtime, mais ses classes sont de simples coquilles vides
    // dans les tests unitaires JVM : cette version réelle permet de tester le format JSON du journal.
    testImplementation("org.json:json:20260814")
    // Robolectric simule Android sur la JVM : permet de tester ce qui passe par les SharedPreferences
    // (GameRepository, GameHistoryRepository, TournamentViewModel) sans émulateur.
    testImplementation("org.robolectric:robolectric:4.14.1")
}
