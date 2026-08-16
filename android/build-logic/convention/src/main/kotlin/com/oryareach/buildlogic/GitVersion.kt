package com.oryareach.buildlogic

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.process.ExecOperations
import javax.inject.Inject
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.io.ByteArrayOutputStream

/**
 * The app version comes from the newest `v*` git tag, so a release is cut by tagging and
 * nothing has to be hand-edited in a build file.
 *
 * Read through a [ValueSource] rather than by shelling out at configuration time: Gradle
 * then tracks git output as a build input and the configuration cache stays valid.
 */
abstract class GitDescribeValueSource : ValueSource<String, GitDescribeValueSource.Params> {

    interface Params : ValueSourceParameters {
        val projectDir: org.gradle.api.file.DirectoryProperty
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        // `git describe --tags --abbrev=0` alone is ambiguous when more than one tag points at
        // the exact same commit (e.g. a release re-tagged with no new commits): it picks
        // *some* tag on that commit, not necessarily the highest version — verified the hard
        // way when v1.2.1, tagged on the same commit as v1.2.0, built and shipped as "1.2.0".
        // `git tag --points-at HEAD --sort=-v:refname` sorts by version and breaks that tie
        // correctly. It only finds a tag exactly on the current commit, though, so it's not a
        // full replacement for `describe` — a local/debug build off an untagged commit past
        // the last release still needs `describe`'s "nearest ancestor tag" behavior.
        val onHead = runGit("tag", "--points-at", "HEAD", "--list", "v[0-9]*", "--sort=-v:refname")
            .lineSequence().firstOrNull { it.isNotBlank() }
        if (onHead != null) return onHead

        // No tags yet (a fresh clone before the first release) is normal, not an error.
        return runGit("describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")
    }

    private fun runGit(vararg args: String): String {
        val stdout = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine(listOf("git") + args)
            workingDir = parameters.projectDir.get().asFile
            standardOutput = stdout
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        return if (result.exitValue == 0) stdout.toString(Charsets.UTF_8).trim() else ""
    }
}

/** Version before any release has been tagged. Sorts below every real release. */
const val FALLBACK_VERSION_NAME = "0.0.0-dev"

fun Project.gitVersionName(): Provider<String> =
    providers.of(GitDescribeValueSource::class.java) {
        parameters.projectDir.set(rootProject.layout.projectDirectory)
    }.map { tag ->
        tag.removePrefix("v").ifBlank { FALLBACK_VERSION_NAME }
    }

/**
 * Android requires a monotonically increasing integer. `major * 10000 + minor * 100 + patch`
 * keeps it readable (1.4.0 -> 10400) and ordered, as long as minor and patch stay under 100.
 */
fun versionNameToCode(versionName: String): Int {
    val core = versionName.substringBefore('-')
    val parts = core.split('.').mapNotNull(String::toIntOrNull)
    if (parts.size != 3) return 1
    val (major, minor, patch) = parts
    require(minor < 100 && patch < 100) {
        "version $versionName overflows the versionCode scheme: minor and patch must stay below 100"
    }
    return (major * 10_000 + minor * 100 + patch).coerceAtLeast(1)
}
