plugins {
    id("com.modrinth.minotaur")
}

// Ensure that the readme is synched
tasks.modrinth.get().dependsOn(tasks.modrinthSyncBody)

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN") ?: "") // Even though this is the default value, apparently this prevents GitHub Actions caching the token?
    projectId.set("geyser")
    versionName.set(versionName(project))
    versionNumber.set(projectVersion(project))
    versionType.set("beta")
    changelog.set(System.getenv("CHANGELOG") ?: "")
    gameVersions.addAll("1.21", libs.minecraft.get().version as String)
    failSilently.set(true)

    syncBodyFrom.set(rootProject.file("README.md").readText())
}
