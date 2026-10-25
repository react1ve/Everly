package app.android.cloud

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import app.android.BuildConfig
import app.platform.AnalyticsSink
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import java.lang.ref.WeakReference

/** True when google-services.json was present at build time and Firebase initialised from it. */
fun firebaseReady(context: Context): Boolean =
    BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(context).isNotEmpty()

/** The resumed Activity, for flows that need one (Credential Manager, Apple web sign-in). */
object CurrentActivity : Application.ActivityLifecycleCallbacks {
    private var ref: WeakReference<Activity>? = null
    fun get(): Activity? = ref?.get()

    override fun onActivityResumed(activity: Activity) { ref = WeakReference(activity) }
    override fun onActivityPaused(activity: Activity) { if (ref?.get() === activity) ref = null }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { ref = WeakReference(activity) }
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) { if (ref?.get() === activity) ref = null }
}

/**
 * Firebase Analytics, collection only while the user has opted in.
 * Manifest defaults keep it off before the first launch decision and never collect the advertising ID.
 */
class FirebaseAnalyticsSink(context: Context) : AnalyticsSink {
    private val fa = FirebaseAnalytics.getInstance(context)
    override fun send(event: String, props: Map<String, String>) {
        fa.logEvent(event, Bundle().apply { props.forEach { (k, v) -> putString(k, v.take(100)) } })
    }
    override fun setEnabled(enabled: Boolean) = fa.setAnalyticsCollectionEnabled(enabled)
}
