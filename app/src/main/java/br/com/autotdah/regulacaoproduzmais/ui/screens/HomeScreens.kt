package br.com.autotdah.regulacaoproduzmais.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.autotdah.regulacaoproduzmais.accessibility.BlockerAccessibilityService
import br.com.autotdah.regulacaoproduzmais.domain.AccessDecider
import br.com.autotdah.regulacaoproduzmais.domain.OpResult
import br.com.autotdah.regulacaoproduzmais.domain.TimeFormat
import br.com.autotdah.regulacaoproduzmais.model.SessionStatus
import br.com.autotdah.regulacaoproduzmais.service.SessionController
import br.com.autotdah.regulacaoproduzmais.ui.BigButton
import br.com.autotdah.regulacaoproduzmais.ui.BrandName
import br.com.autotdah.regulacaoproduzmais.ui.InfoCard
import br.com.autotdah.regulacaoproduzmais.ui.Logo
import br.com.autotdah.regulacaoproduzmais.ui.Nav
import br.com.autotdah.regulacaoproduzmais.ui.NoticeCard
import br.com.autotdah.regulacaoproduzmais.ui.PinAction
import br.com.autotdah.regulacaoproduzmais.ui.RuleIcon
import br.com.autotdah.regulacaoproduzmais.ui.Screen
import br.com.autotdah.regulacaoproduzmais.ui.SecondaryButton
import br.com.autotdah.regulacaoproduzmais.ui.Slogan
import br.com.autotdah.regulacaoproduzmais.ui.StatusChip
import br.com.autotdah.regulacaoproduzmais.ui.VSpace
import br.com.autotdah.regulacaoproduzmais.ui.rememberSecondTick
import br.com.autotdah.regulacaoproduzmais.ui.theme.Brand
import kotlinx.coroutines.delay

/** TELA 1 — Abertura. Na primeira utilização espera “Começar”; depois aparece só por um instante. */
@Composable
fun SplashScreen(nav: Nav) {
    val firstUse = !SessionController.settings.current.onboardingDone
    LaunchedEffect(firstUse) {
        if (!firstUse) {
            delay(450)
            nav.reset(Screen.Home)
        }
    }
    Column(
        Modifier.fillMaxSize().background(Brand.Navy).statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))
        Logo(132.dp)
        VSpace(20.dp)
        BrandName(46.sp)
        Slogan(size = 20.sp)
        VSpace(36.dp)
        if (firstUse) {
            Feature("Mais foco")
            Feature("Mais produtividade")
            Feature("Mais controle")
        }
        Spacer(Modifier.weight(1f))
        if (firstUse) {
            BigButton("Começar", onClick = { nav.reset(Screen.Home, Screen.Permissions(onboarding = true)) })
        }
    }
}

@Composable
private fun Feature(text: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("●", color = Brand.Orange, fontSize = 14.sp)
        Spacer(Modifier.width(12.dp))
        Text(text, color = Brand.White, fontSize = 17.sp)
    }
}

/** TELA 2 — Início (inclui a TELA 11: programação pausada após reinício). */
@Composable
fun HomeScreen(nav: Nav) {
    val context = LocalContext.current
    val session by SessionController.session.collectAsState()
    val revision by SessionController.revision.collectAsState()
    val tick = rememberSecondTick()
    val coordinator = SessionController.coordinator
    val a11yOn = remember(tick) { BlockerAccessibilityService.isEnabled(context) }
    val s = session

    Column(Modifier.fillMaxSize().background(Brand.Surface)) {
        Column(
            Modifier.fillMaxWidth().background(Brand.Navy).statusBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    BrandName(30.sp)
                    Slogan(size = 14.sp)
                }
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            coordinator.loadProblem?.let {
                NoticeCard(it, action = "Entendi", onAction = { coordinator.clearLoadProblem(); SessionController.tick() })
            }
            if (revision >= 0) coordinator.persistError?.let { NoticeCard(it) }
            if (!a11yOn) {
                NoticeCard(
                    "O bloqueio está desativado: o serviço de acessibilidade do AutoTdah não está ativo.",
                    color = Brand.Orange, action = "Ativar agora", onAction = { nav.go(Screen.Permissions(false)) },
                )
            }

            when {
                s != null && s.status == SessionStatus.RUNNING -> ActiveCard(nav, tick)
                s != null && s.status.isPaused -> PausedCard(nav, tick)
                else -> {
                    InfoCard {
                        Text(
                            "Nenhuma programação ativa",
                            fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Brand.TextDark,
                        )
                        VSpace(6.dp)
                        Text(
                            "Crie sua sequência de acesso para começar.",
                            color = Brand.Muted, fontSize = 15.sp,
                        )
                        if (s != null && s.status.isTerminal) {
                            VSpace(12.dp)
                            LastResult(s.status, coordinator.executedMs(), s.plannedMs)
                        }
                        VSpace(16.dp)
                        BigButton("Nova programação", icon = Icons.Filled.PlayArrow, onClick = { nav.go(Screen.Editor) })
                    }
                }
            }

            MenuRow(Icons.AutoMirrored.Filled.List, "Histórico de sessões") { nav.go(Screen.History) }
            MenuRow(Icons.Filled.Settings, "Configurações") { nav.go(Screen.Settings) }
        }
    }
}

@Composable
private fun LastResult(status: SessionStatus, executedMs: Long, plannedMs: Long) {
    val color = when (status) {
        SessionStatus.COMPLETED -> Brand.Green
        SessionStatus.ENDED_EARLY -> Brand.Orange
        else -> Brand.Red
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Última sessão: ", color = Brand.Muted, fontSize = 14.sp)
        StatusChip(status.label, color)
    }
    VSpace(4.dp)
    Text(
        "Executado ${TimeFormat.duration(executedMs)} de ${TimeFormat.duration(plannedMs)} planejados",
        color = Brand.Muted, fontSize = 14.sp,
    )
}

@Composable
private fun ActiveCard(nav: Nav, tick: Long) {
    val s = SessionController.session.collectAsState().value ?: return
    val step = s.currentStep ?: return
    val remaining = remember(tick, s) { SessionController.remainingMs() }
    InfoCard(dark = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip("Em execução", Brand.Green)
            Spacer(Modifier.width(8.dp))
            Text("Etapa ${s.currentIndex + 1} de ${s.steps.size}", color = Brand.MutedOnDark, fontSize = 14.sp)
        }
        VSpace(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RuleIcon(step.type, step.packageName, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(step.label, color = Brand.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(AccessDecider.ruleDescription(step), color = Brand.MutedOnDark, fontSize = 13.sp)
            }
            Text(TimeFormat.clock(remaining), color = Brand.Orange, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        s.nextStep?.let {
            VSpace(10.dp)
            Text("Próxima etapa: ${it.label} — ${it.minutes} min", color = Brand.MutedOnDark, fontSize = 14.sp)
        }
        VSpace(14.dp)
        BigButton("Abrir sessão", onClick = { nav.go(Screen.Running) })
    }
}

@Composable
private fun PausedCard(nav: Nav, tick: Long) {
    val s = SessionController.session.collectAsState().value ?: return
    val step = s.currentStep ?: return
    var message by remember { mutableStateOf<String?>(null) }
    val remaining = remember(tick, s) { SessionController.remainingMs() }
    InfoCard {
        Text(
            if (s.status == SessionStatus.PAUSED_REBOOT) "Programação pausada" else "Programação pausada por você",
            fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Brand.TextDark,
        )
        VSpace(6.dp)
        if (s.status == SessionStatus.PAUSED_REBOOT) {
            Text("O celular foi reiniciado.", fontWeight = FontWeight.SemiBold, color = Brand.TextDark)
            Text(
                "Sua programação está salva e foi pausada. Toque em continuar para retomar do ponto em que parou.",
                color = Brand.Muted, fontSize = 14.sp,
            )
        } else {
            Text("O tempo está congelado até você continuar.", color = Brand.Muted, fontSize = 14.sp)
        }
        VSpace(12.dp)
        InfoCard(dark = false) {
            Text("Etapa ${s.currentIndex + 1} de ${s.steps.size}", color = Brand.Muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RuleIcon(step.type, step.packageName, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(step.label, fontWeight = FontWeight.SemiBold, color = Brand.TextDark)
                    Text("${TimeFormat.clock(remaining)} restantes", color = Brand.Muted, fontSize = 13.sp)
                }
            }
        }
        message?.let {
            VSpace(8.dp)
            Text(it, color = Brand.Red, fontSize = 14.sp)
        }
        VSpace(12.dp)
        BigButton("Continuar programação", icon = Icons.Filled.PlayArrow, onClick = {
            when (val r = SessionController.resume()) {
                is OpResult.Ok -> nav.go(Screen.Running)
                is OpResult.Failure -> message = r.message
            }
        })
        VSpace(8.dp)
        SecondaryButton("Encerrar sessão (PIN)", onClick = { nav.go(Screen.Pin(PinAction.END)) })
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    InfoCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Brand.Navy, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Text(text, fontSize = 17.sp, color = Brand.TextDark, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Brand.Muted)
        }
    }
}
