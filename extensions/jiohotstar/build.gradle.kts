extension {
    name = "extensions/jiohotstar.mpe"
}

android {
    namespace = "app.brokenn.extension.jiohotstar.settings"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "PATCHES_VERSION", "\"${project.version}\"")
    }
}
