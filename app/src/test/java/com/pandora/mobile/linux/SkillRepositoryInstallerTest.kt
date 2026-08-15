package com.pandora.mobile.linux

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class SkillRepositoryInstallerTest {
    @Test
    fun discoversRootAndNestedSkills() {
        val repository = Files.createTempDirectory("pandora-skills").toFile()
        try {
            repository.resolve("skills/review/SKILL.md").apply {
                checkNotNull(parentFile).mkdirs()
                writeText("---\nname: review\n---")
            }
            repository.resolve("skills/test/SKILL.md").apply {
                checkNotNull(parentFile).mkdirs()
                writeText("---\nname: test\n---")
            }

            assertEquals(
                listOf("review", "test"),
                discoverSkillDirectories(repository).map { it.name }.sorted(),
            )
        } finally {
            repository.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonHttpsRepositories() {
        validateRepositoryUrl("git@github.com:example/skills.git")
    }
}
