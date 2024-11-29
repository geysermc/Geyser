plugins {
    // Allow blossom to mark sources root of templates
    idea
    id("geyser.publish-conventions")
    alias(libs.plugins.blossom)
}

dependencies {
    api(libs.base.api)
    api(libs.math)

    // Adventure text serialization
    api(libs.bundles.adventure)

    // TODO? can we exclude more
    api(libs.mcprotocollib) {
        exclude("io.netty", "netty-all")
        exclude("net.raphimc", "MinecraftAuth")
    }
}

version = property("version")!!
val apiVersion = (version as String).removeSuffix("-SNAPSHOT")

sourceSets {
    main {
        blossom {
            javaSources {
                property("version", apiVersion)
            }
        }
    }
}
