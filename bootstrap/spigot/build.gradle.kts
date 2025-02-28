plugins {
    id("geyser.platform-conventions")
    id("geyser.modrinth-uploading-conventions")
    alias(libs.plugins.runpaper)
}

dependencies {
    api(projects.core)
    api(libs.erosion.bukkit.common) {
        isTransitive = false
    }

    implementation(libs.erosion.bukkit.nms) {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
        }
    }

    implementation(variantOf(libs.adapters.spigot) {
        classifier("all") // otherwise the unshaded jar is used without the shaded NMS implementations
    })
    implementation(variantOf(libs.adapters.paper) {
        classifier("all") // otherwise the unshaded jar is used without the shaded NMS implementations
    })

    implementation(libs.cloud.paper)
    implementation(libs.commodore)

    implementation(libs.adventure.text.serializer.bungeecord)

    compileOnly(libs.folia.api)

    compileOnlyApi(libs.viaversion)

    // For 1.16.5/1.17.1
    implementation(libs.gson.record.factory) {
        isTransitive = false
    }
}

platformRelocate("it.unimi.dsi.fastutil")
// Relocate net.kyori but exclude the component logger
platformRelocate("net.kyori", "net.kyori.adventure.text.logger.slf4j.ComponentLogger")
platformRelocate("org.objectweb.asm")
platformRelocate("me.lucko.commodore")
platformRelocate("org.incendo")
platformRelocate("io.leangen.geantyref") // provided by cloud and Configurate, should also be relocated
platformRelocate("org.yaml") // Broken as of 1.20
platformRelocate("org.spongepowered")
platformRelocate("marcono1234.gson")
platformRelocate("org.bstats")

// These dependencies are already present on the platform
provided(libs.viaversion)

tasks.withType<Jar> {
    manifest.attributes["Main-Class"] = "org.geysermc.geyser.platform.spigot.GeyserSpigotMain"
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {

    // Prevents Paper 1.20.5+ from remapping Geyser
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }

    archiveBaseName.set("Geyser-Spigot")

    dependencies {
        exclude(dependency("com.google.*:.*"))

        // We cannot shade Netty, or else native libraries will not load
        // Needed because older Spigot builds do not provide the haproxy module
        exclude(dependency("io.netty.incubator:.*"))
        exclude(dependency("io.netty:netty-transport-classes-epoll:.*"))
        exclude(dependency("io.netty:netty-transport-native-epoll:.*"))
        exclude(dependency("io.netty:netty-transport-native-unix-common:.*"))
        exclude(dependency("io.netty:netty-transport-classes-kqueue:.*"))
        exclude(dependency("io.netty:netty-transport-native-kqueue:.*"))
        exclude(dependency("io.netty:netty-handler:.*"))
        exclude(dependency("io.netty:netty-common:.*"))
        exclude(dependency("io.netty:netty-buffer:.*"))
        exclude(dependency("io.netty:netty-resolver:.*"))
        exclude(dependency("io.netty:netty-transport:.*"))
        exclude(dependency("io.netty:netty-codec:.*"))
        exclude(dependency("io.netty:netty-codec-dns:.*"))
        exclude(dependency("io.netty:netty-resolver-dns:.*"))
        exclude(dependency("io.netty:netty-resolver-dns-native-macos:.*"))

        // Commodore includes Brigadier
        exclude(dependency("com.mojang:.*"))
    }
}

modrinth {
    uploadFile.set(tasks.getByPath("shadowJar"))
    gameVersions.addAll("1.16.5", "1.17", "1.17.1", "1.18", "1.18.1", "1.18.2", "1.19",
        "1.19.1", "1.19.2", "1.19.3", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6")
    loaders.addAll("spigot", "paper")
}

tasks {
    runServer {
        minecraftVersion(libs.versions.runpaperversion.get())
    }
}
