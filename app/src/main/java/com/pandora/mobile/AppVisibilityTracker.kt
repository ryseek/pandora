package com.pandora.mobile

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

internal class AppVisibilityTracker : Application.ActivityLifecycleCallbacks {
    private val startedActivities = AtomicInteger()

    val isForeground: Boolean
        get() = startedActivities.get() > 0

    internal fun activityStarted() {
        startedActivities.incrementAndGet()
    }

    internal fun activityStopped() {
        startedActivities.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }

    override fun onActivityStarted(activity: Activity) = activityStarted()
    override fun onActivityStopped(activity: Activity) = activityStopped()
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
