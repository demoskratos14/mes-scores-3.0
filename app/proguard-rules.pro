# Règles R8/ProGuard pour la version release.
# L'appli n'utilise ni réflexion ni bibliothèque de sérialisation externe (le JSON passe par
# org.json, fourni par Android) : les règles par défaut d'Android et de Compose suffisent.
# Garde les numéros de ligne lisibles dans les rapports de plantage.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
