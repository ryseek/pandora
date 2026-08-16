package com.pandora.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVisibilityTrackerTest {
    @Test
    fun `foreground follows started activity count`() {
        val visibility = AppVisibilityTracker()

        assertFalse(visibility.isForeground)
        visibility.activityStarted()
        visibility.activityStarted()
        assertTrue(visibility.isForeground)
        visibility.activityStopped()
        assertTrue(visibility.isForeground)
        visibility.activityStopped()
        assertFalse(visibility.isForeground)
    }

    @Test
    fun `extra stop callback cannot make count negative`() {
        val visibility = AppVisibilityTracker()

        visibility.activityStopped()
        assertFalse(visibility.isForeground)
        visibility.activityStarted()
        assertTrue(visibility.isForeground)
    }
}
