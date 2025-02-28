plugins {
    id("geyser.platform-conventions")
}

dependencies {
    api(projects.core)

    compileOnlyApi(libs.viaproxy)
}

platformRelocate("net.kyori")
platformRelocate("org.yaml")
platformRelocate("it.unimi.dsi.fastutil")
platformRelocate("org.cloudburstmc.netty")
platformRelocate("org.bstats")
platformRelocate("org.incendo")
platformRelocate("io.leangen.geantyref") // provided by cloud and Configurate, should also be relocated

// These dependencies are already present on the platform
provided(libs.viaproxy)

tasks.withType<Jar> {
    manifest.attributes["Main-Class"] = "org.geysermc.geyser.platform.viaproxy.GeyserViaProxyMain"
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveBaseName.set("Geyser-ViaProxy")

    dependencies {
        exclude(dependency("com.google.*:.*"))
        exclude(dependency("io.netty:.*"))
        exclude(dependency("io.netty.incubator:.*"))
        exclude(dependency("org.slf4j:.*"))
        exclude(dependency("org.ow2.asm:.*"))
    }
}
