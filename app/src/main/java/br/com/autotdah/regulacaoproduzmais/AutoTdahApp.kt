package br.com.autotdah.regulacaoproduzmais

import android.app.Application
import br.com.autotdah.regulacaoproduzmais.service.Notifications
import br.com.autotdah.regulacaoproduzmais.service.SessionController

class AutoTdahApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // Carrega o estado salvo; se o aparelho reiniciou durante uma sessão, ela é pausada aqui.
        SessionController.init(this)
    }
}
