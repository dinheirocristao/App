package br.com.autotdah.regulacaoproduzmais.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.autotdah.regulacaoproduzmais.service.SessionController
import br.com.autotdah.regulacaoproduzmais.ui.screens.BlockScreen
import br.com.autotdah.regulacaoproduzmais.ui.theme.AutoTdahTheme

/**
 * TELA 8 — Tela de bloqueio. Não tem botão para ignorar o bloqueio: as saídas são a tela
 * inicial, o app liberado na etapa ou o próprio AutoTdah. “Voltar” leva à tela inicial.
 */
class BlockActivity : ComponentActivity() {
    private var blockedPkg by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        SessionController.init(applicationContext)
        blockedPkg = intent.getStringExtra(EXTRA_PKG)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            AutoTdahTheme {
                BlockScreen(
                    blockedPkg = blockedPkg,
                    onHome = ::goHome,
                    onOpenApp = ::openApp,
                    onOpenAutoTdah = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    },
                    onNoLongerBlocked = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        blockedPkg = intent.getStringExtra(EXTRA_PKG)
    }

    override fun onResume() {
        super.onResume()
        visible = true
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun openApp(pkg: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
    }

    companion object {
        const val EXTRA_PKG = "blocked_package"

        @Volatile var visible = false
            private set
    }
}
