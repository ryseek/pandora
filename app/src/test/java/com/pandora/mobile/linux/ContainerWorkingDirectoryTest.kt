package com.pandora.mobile.linux

import org.junit.Assert.assertEquals
import org.junit.Test

class ContainerWorkingDirectoryTest {
    @Test
    fun acceptsProjectDirectories() {
        assertEquals("/root/projects/pandora", validatedContainerWorkingDirectory("/root/projects/pandora/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDirectoriesOutsideWorkspace() {
        validatedContainerWorkingDirectory("/tmp/project")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTraversal() {
        validatedContainerWorkingDirectory("/root/project/../other")
    }
}
