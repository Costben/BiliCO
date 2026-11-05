package erl.bilico

import android.app.Application
import erl.bilico.di.AppContainer

class BiliCoApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
