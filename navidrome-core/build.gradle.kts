plugins {
    alias(catalog.plugins.murglar.plugin.core)
}

murglarPlugin {
    id = "navidrome"
    name = "Navidrome"
    version = catalog.versions.navidrome.map(String::toInt)
    entryPointClass = "com.badmanners.murglar.lib.navidrome.NavidromeMurglar"
}
