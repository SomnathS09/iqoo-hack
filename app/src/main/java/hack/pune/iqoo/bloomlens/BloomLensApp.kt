package hack.pune.iqoo.bloomlens

import android.app.Application

class BloomLensApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}