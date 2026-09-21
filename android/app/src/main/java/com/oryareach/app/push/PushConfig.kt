package com.oryareach.app.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.oryareach.app.BuildConfig

/**
 * Brings Firebase up from build configuration instead of `google-services.json`.
 *
 * The four values below are what that file would otherwise carry, and none of them is a
 * secret — they identify the Firebase project to this client. The credential that can actually
 * send a message is the service account, and it lives only in the `notify-workspace` edge
 * function's environment.
 *
 * Doing it this way keeps a build with no Firebase project working: the google-services Gradle
 * plugin fails the build outright when the file is missing, which would break every local build
 * and every fork. Here, unset values mean [isConfigured] is false, nothing initialises, and the
 * app behaves exactly as it did before push existed.
 */
object PushConfig {

    val isConfigured: Boolean
        get() = BuildConfig.FCM_PROJECT_ID.isNotBlank() &&
            BuildConfig.FCM_APPLICATION_ID.isNotBlank() &&
            BuildConfig.FCM_API_KEY.isNotBlank() &&
            BuildConfig.FCM_SENDER_ID.isNotBlank()

    /**
     * Idempotent: Firebase throws if the default app is initialised twice, and this is called
     * both from Application.onCreate and, after a process restart for a message, from the
     * messaging service's own startup path.
     */
    fun initialize(context: Context) {
        if (!isConfigured) return
        if (FirebaseApp.getApps(context).isNotEmpty()) return

        FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setProjectId(BuildConfig.FCM_PROJECT_ID)
                .setApplicationId(BuildConfig.FCM_APPLICATION_ID)
                .setApiKey(BuildConfig.FCM_API_KEY)
                .setGcmSenderId(BuildConfig.FCM_SENDER_ID)
                .build(),
        )
    }
}
