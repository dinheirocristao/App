package br.com.autotdah.regulacaoproduzmais.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import br.com.autotdah.regulacaoproduzmais.domain.StepError
import br.com.autotdah.regulacaoproduzmais.model.DraftStep
import br.com.autotdah.regulacaoproduzmais.model.Step
import br.com.autotdah.regulacaoproduzmais.service.SessionController
import br.com.autotdah.regulacaoproduzmais.ui.screens.AboutScreen
import br.com.autotdah.regulacaoproduzmais.ui.screens.AppPickerScreen
import br.com.autotdah.regulacaoproduzmais.ui.screens.EditorScreen
import br.com.autotdah.regulacaoproduzmais.ui.screens.HistoryDetailScreen
import br.com.autotdah.regulacaoproduzmais.ui.screens.HistoryScreen
import br.com.autotdah.regulacaoproduzmais.ui.screens.HomeScreen
import br.com.autotdah.regulacaoproduzmais.ui.screens.MonitoredAppsScreen
import br.com.autotdah.regulacaoproduzmais.ui.screens.PermissionsScreen
import br.com.autotdah.regulacaoproduzmais.ui.screens.PinPromptScreen
import br.com.autotdah.regulacaoproduzmais.ui.screens.PinSetupScreen
import br.com.autotdah.regulacaoproduzmais.ui.screens.ReviewScreen
import br.com.autotdah.regulacaoproduzmais.ui.screens.RunningScreen
import br.com.autotdah.regulacaoproduzmais.ui.screens.SettingsScreen
import br.com.autotdah.regulacaoproduzmais.ui.screens.SplashScreen
import br.com.autotdah.regulacaoproduzmais.ui.screens.TemplatesScreen
import br.com.autotdah.regulacaoproduzmais.ui.theme.AutoTdahTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        SessionController.init(applicationContext)
        setContent { AutoTdahTheme { AppRoot() } }
    }
}

enum class PinAction { PAUSE, END }

sealed interface Screen {
    data object Splash : Screen
    data object Home : Screen
    data object Editor : Screen
    data class Picker(val index: Int) : Screen
    data object Review : Screen
    data object Running : Screen
    data class Pin(val action: PinAction) : Screen
    data object History : Screen
    data class HistoryDetail(val id: String) : Screen
    data object Settings : Screen
    data class Permissions(val onboarding: Boolean) : Screen
    data object PinSetup : Screen
    data object Monitored : Screen
    data object Templates : Screen
    data object About : Screen
}

/** Navegação simples por pilha (sem bibliotecas extras). */
class Nav(start: Screen) {
    val stack = mutableStateListOf(start)
    val current: Screen get() = stack.last()

    fun go(s: Screen) {
        stack.add(s)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun reset(vararg s: Screen) {
        stack.clear()
        stack.addAll(s)
    }
}

/** Rascunho da programação em edição; sobrevive à navegação entre telas. */
object EditorDraft {
    val steps = mutableStateListOf(DraftStep())
    var errors by mutableStateOf<List<StepError>>(emptyList())
    var validated: List<Step> = emptyList()

    fun load(newSteps: List<DraftStep>) {
        steps.clear()
        steps.addAll(newSteps.ifEmpty { listOf(DraftStep()) })
        errors = emptyList()
    }
}

@Composable
fun AppRoot() {
    val nav = remember { Nav(Screen.Splash) }
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }
    when (val s = nav.current) {
        Screen.Splash -> SplashScreen(nav)
        Screen.Home -> HomeScreen(nav)
        Screen.Editor -> EditorScreen(nav)
        is Screen.Picker -> AppPickerScreen(nav, s.index)
        Screen.Review -> ReviewScreen(nav)
        Screen.Running -> RunningScreen(nav)
        is Screen.Pin -> PinPromptScreen(nav, s.action)
        Screen.History -> HistoryScreen(nav)
        is Screen.HistoryDetail -> HistoryDetailScreen(nav, s.id)
        Screen.Settings -> SettingsScreen(nav)
        is Screen.Permissions -> PermissionsScreen(nav, s.onboarding)
        Screen.PinSetup -> PinSetupScreen(nav)
        Screen.Monitored -> MonitoredAppsScreen(nav)
        Screen.Templates -> TemplatesScreen(nav)
        Screen.About -> AboutScreen(nav)
    }
}
