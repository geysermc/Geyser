import net.kyori.blossom.BlossomExtension

plugins {
    id("net.kyori.blossom")
    id("net.kyori.indra.git")
    id("geyser.publish-conventions")
}

dependencies {
    api(projects.common)
    api(projects.api)

    // Jackson JSON and YAML serialization
    api(libs.bundles.jackson)
    api(libs.guava)

    // Fastutil Maps
    implementation(libs.bundles.fastutil)

    // Network libraries
    implementation(libs.websocket)

    api(libs.bundles.protocol)

    api(libs.mcauthlib)
    api(libs.mcprotocollib) {
        exclude("io.netty", "netty-all")
        exclude("com.github.GeyserMC", "packetlib")
        exclude("com.github.GeyserMC", "mcauthlib")
    }

    implementation(libs.raknet) {
        exclude("io.netty", "*");
    }

    implementation(libs.netty.resolver.dns)
    implementation(libs.netty.resolver.dns.native.macos) { artifact { classifier = "osx-x86_64" } }
    implementation(libs.netty.codec.haproxy)

    // Network dependencies we are updating ourselves
    api(libs.netty.handler)

    implementation(libs.netty.transport.native.epoll) { artifact { classifier = "linux-x86_64" } }
    implementation(libs.netty.transport.native.epoll) { artifact { classifier = "linux-aarch_64" } }
    implementation(libs.netty.transport.native.kqueue) { artifact { classifier = "osx-x86_64" } }

    // Adventure text serialization
    api(libs.bundles.adventure)

    api(libs.erosion.common) {
        isTransitive = false
    }

    // Test
    testImplementation(libs.junit)

    // Annotation Processors
    compileOnly(projects.ap)

    annotationProcessor(projects.ap)

    api(libs.events)
}

configurations.api {
    // This is still experimental - additionally, it could only really benefit standalone
    exclude(group = "io.netty.incubator", module = "netty-incubator-transport-native-io_uring")
}

tasks.processResources {
    // This is solely for backwards compatibility for other programs that used this file before the switch to gradle.
    // It used to be generated by the maven Git-Commit-Id-Plugin
    filesMatching("git.properties") {
        val info = GitInfo()
        expand(
            "branch" to info.branch,
            "buildNumber" to info.buildNumber,
            "projectVersion" to project.version,
            "commit" to info.commit,
            "commitAbbrev" to info.commitAbbrev,
            "commitMessage" to info.commitMessage,
            "repository" to info.repository
        )
    }
}

configure<BlossomExtension> {
    val mainFile = "src/main/java/org/geysermc/geyser/GeyserImpl.java"
    val info = GitInfo()

    replaceToken("\${version}", "${project.version} (${info.gitVersion})", mainFile)
    replaceToken("\${gitVersion}", info.gitVersion, mainFile)
    replaceToken("\${buildNumber}", info.buildNumber, mainFile)
    replaceToken("\${branch}", info.branch, mainFile)
    replaceToken("\${commit}", info.commit, mainFile)
    replaceToken("\${repository}", info.repository, mainFile)
}

fun Project.buildNumber(): Int =
    System.getenv("BUILD_NUMBER")?.let { Integer.parseInt(it) } ?: -1

inner class GitInfo {
    val branch: String
    val commit: String
    val commitAbbrev: String

    val gitVersion: String
    val version: String
    val buildNumber: Int

    val commitMessage: String
    val repository: String

    init {
        // On Jenkins, a detached head is checked out, so indra cannot determine the branch.
        // Fortunately, this environment variable is available.
        branch = indraGit.branchName() ?: System.getenv("BRANCH_NAME") ?: "DEV"

        val commit = indraGit.commit()
        this.commit = commit?.name ?: "0".repeat(40)
        commitAbbrev = commit?.name?.substring(0, 7) ?: "0".repeat(7)

        gitVersion = "git-${branch}-${commitAbbrev}"
        version = "${project.version} ($gitVersion)"
        buildNumber = buildNumber()

        val git = indraGit.git()
        commitMessage = git?.commit()?.message ?: ""
        repository = git?.repository?.config?.getString("remote", "origin", "url") ?: ""
    }
}
