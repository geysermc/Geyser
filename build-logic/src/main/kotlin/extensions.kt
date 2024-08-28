/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.named
import java.io.File
import java.net.URL

fun Project.relocate(pattern: String) {
    tasks.named<ShadowJar>("shadowJar") {
        relocate(pattern, "org.geysermc.geyser.shaded.$pattern")
    }
}

fun Project.exclude(group: String) {
    tasks.named<ShadowJar>("shadowJar") {
        exclude(group)
    }
}

fun Project.platformRelocate(pattern: String, exclusion: String = "") {
    tasks.named<ShadowJar>("shadowJar") {
        relocate(pattern, "org.geysermc.geyser.platform.${project.name}.shaded.$pattern") {
            exclude(exclusion)
        }
    }
}

val providedDependencies = mutableMapOf<String, MutableSet<String>>()

fun getProvidedDependenciesForProject(projectName: String): MutableSet<String> {
    return providedDependencies.getOrDefault(projectName, emptySet()).toMutableSet()
}

fun Project.provided(pattern: String, name: String, excludedOn: Int = 0b110) {
    providedDependencies.getOrPut(project.name) { mutableSetOf() }
        .add("${calcExclusion(pattern, 0b100, excludedOn)}:${calcExclusion(name, 0b10, excludedOn)}")
}

fun Project.provided(dependency: ProjectDependency) =
    provided(dependency.group!!, dependency.name)

fun Project.provided(dependency: MinimalExternalModuleDependency) =
    provided(dependency.module.group, dependency.module.name)

fun Project.provided(provider: Provider<MinimalExternalModuleDependency>) =
    provided(provider.get())

open class DownloadFilesTask : DefaultTask() {
    @Input
    var urls: List<String> = listOf()

    @Input
    var destinationDir: String = ""

    @Option(option="suffix", description="suffix")
    @Input
    var suffix: String = ""

    @Input
    var suffixedFiles: List<String> = listOf()

    @TaskAction
    fun downloadAndAddSuffix() {
        urls.forEach { fileUrl ->
            val fileName = fileUrl.substringAfterLast("/")
            val baseName = fileName.substringBeforeLast(".")
            val extension = fileName.substringAfterLast(".", "")
            val shouldSuffix = fileName in suffixedFiles
            val suffixedFileName = if (shouldSuffix && extension.isNotEmpty()) "$baseName.$suffix.$extension" else fileName
            val outputFile = File(destinationDir, suffixedFileName)

            if (!outputFile.parentFile.exists()) {
                outputFile.parentFile.mkdirs()
            }

            URL(fileUrl).openStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            println("Downloaded: $suffixedFileName")
        }
    }
}

private fun calcExclusion(section: String, bit: Int, excludedOn: Int): String =
    if (excludedOn and bit > 0) section else ""

fun projectVersion(project: Project): String =
    project.version.toString().replace("SNAPSHOT", "b" + buildNumber())

fun versionName(project: Project): String =
    "Geyser-" + project.name.replaceFirstChar { it.uppercase() } + "-" + projectVersion(project)

fun buildNumber(): Int =
    (System.getenv("BUILD_NUMBER"))?.let { Integer.parseInt(it) } ?: -1

