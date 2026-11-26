package app.lirnyk

import android.app.Application
import app.lirnyk.core.media.Lirnyk

class LirnykApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Lirnyk.init(this, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.GIT_COMMIT})")
    }
}
