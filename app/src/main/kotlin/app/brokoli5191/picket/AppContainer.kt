package app.brokoli5191.picket

import android.content.Context

/**
 * Manual dependency injection: one instance per process, created in [PicketApplication.onCreate].
 * Everything that needs the application context or a singleton lives here.
 */
class AppContainer(
    context: Context,
) {
    val appContext: Context = context.applicationContext
}
