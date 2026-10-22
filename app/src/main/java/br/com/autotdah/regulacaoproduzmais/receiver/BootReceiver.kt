package br.com.autotdah.regulacaoproduzmais.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.autotdah.regulacaoproduzmais.service.SessionController

/**
 * Recebe o aviso de inicialização (e o de app atualizado). A sessão que estava em execução é
 * pausada preservando o tempo restante. O processamento é idempotente: o mesmo boot pode
 * chegar por mais de uma ação sem efeitos repetidos. Alguns fabricantes atrasam ou bloqueiam
 * este aviso; nesse caso a detecção ocorre quando o app ou a acessibilidade iniciarem.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            -> {
                SessionController.init(context)
                SessionController.onBootSignal()
            }
        }
    }
}
