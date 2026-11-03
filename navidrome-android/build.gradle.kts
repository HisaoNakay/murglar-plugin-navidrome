plugins {
    alias(catalog.plugins.murglar.plugin.android)
}

murglarAndroidPlugin {
    id = "navidrome"
    name = "Navidrome"
    version = catalog.versions.navidrome.map(String::toInt)
    entryPointClass = "com.badmanners.murglar.lib.navidrome.NavidromeMurglar"
}

dependencies {
    implementation(project(":navidrome-core"))
}
