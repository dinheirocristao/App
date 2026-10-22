package br.com.autotdah.regulacaoproduzmais.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.autotdah.regulacaoproduzmais.accessibility.BlockerAccessibilityService
import br.com.autotdah.regulacaoproduzmais.domain.AccessDecider
import br.com.autotdah.regulacaoproduzmais.domain.AccessDecision
import br.com.autotdah.regulacaoproduzmais.domain.OpResult
import br.com.autotdah.regulacaoproduzmais.domain.TimeFormat
import br.com.autotdah.regulacaoproduzmais.model.RuleType
import br.com.autotdah.regulacaoproduzmais.model.SessionStatus
import br.com.autotdah.regulacaoproduzmais.security.PinCheck
import br.com.autotdah.regulacaoproduzmais.service.SessionController
import br.com.autotdah.regulacaoproduzmais.ui.BigButton
import br.com.autotdah.regulacaoproduzmais.ui.BlockedSymbol
import br.com.autotdah.regulacaoproduzmais.ui.InfoCard
import br.com.autotdah.regulacaoproduzmais.ui.Nav
import br.com.autotdah.regulacaoproduzmais.ui.NoticeCard
import br.com.autotdah.regulacaoproduzmais.ui.PinAction
import br.com.autotdah.regulacaoproduzmais.ui.ProgressRing
import br.com.autotdah.regulacaoproduzmais.ui.RuleIcon
import br.com.autotdah.regulacaoproduzmais.ui.Screen
import br.com.autotdah.regulacaoproduzmais.ui.SecondaryButton
import br.com.autotdah.regulacaoproduzmais.ui.StatusChip
import br.com.autotdah.regulacaoproduzmais.ui.VSpace
import br.com.autotdah.regulacaoproduzmais.ui.rememberSecondTick
import br.com.autotdah.regulacaoproduzmais.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun ruleColor(type: RuleType) = when (type) {
    RuleType.APP -> Brand.Orange
    RuleType.ALL -> Brand.Green
    RuleType.NONE -> Brand.Red
}

@Composable
private fun DarkTopBar(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Brand.White) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Brand.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Brand.MutedOnDark, fontSize = 13.sp)
        }
    }
}

/** TELA 6 e TELA 7 — Sessão em andamento e mudança automática de etapa. */
@Composable
fun RunningScreen(nav: Nav) {
    val context = LocalContext.current
    val session by SessionController.session.collectAsState()
    val tick = rememberSecondTick()
    val a11y = remember(tick) { BlockerAccessibilityService.isEnabled(context) }
    val s = session
    var shownIndex by remember { mutableIntStateOf(s?.currentIndex ?: 0) }
    var banner by remember { mutableStateOf<String?>(null) }
    val goHome = { if (!nav.back()) nav.reset(Screen.Home) }

    LaunchedEffect(s?.currentIndex, s?.status) {
        val cur = s ?: return@LaunchedEffect
        if (cur.status == SessionStatus.RUNNING && cur.currentIndex != shownIndex) {
            shownIndex = cur.currentIndex
            banner = "Nova etapa iniciada automaticamente: ${cur.currentStep?.label}"
            delay(6_000)
            banner = null
        }
    }

    Column(Modifier.fillMaxSize().background(Brand.Navy)) {
        DarkTopBar(
            "Sessão em andamento",
            s?.takeIf { it.status.isActive }?.let { "Etapa ${it.currentIndex + 1} de ${it.steps.size}" },
            onBack = goHome,
        )
        val step = s?.currentStep
        if (s == null || s.status != SessionStatus.RUNNING || step == null) {
            Column(
                Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(s?.status?.label ?: "Nenhuma programação ativa", color = Brand.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (s != null && s.status.isTerminal) {
                    VSpace(8.dp)
                    Text(
                        "Executado ${TimeFormat.duration(SessionController.coordinator.executedMs())} de ${TimeFormat.duration(s.plannedMs)}",
                        color = Brand.MutedOnDark,
                    )
                }
                VSpace(24.dp)
                BigButton("Voltar ao início", onClick = { nav.reset(Screen.Home) })
            }
        } else {
            val remaining = remember(tick, s) { SessionController.remainingMs() }
            val fraction = if (step.durationMs > 0) remaining.toFloat() / step.durationMs else 0f
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                banner?.let {
                    Box(Modifier.fillMaxWidth().clip(CircleShape).background(Brand.Green.copy(alpha = 0.2f)).padding(10.dp)) {
                        Text(it, color = Brand.White, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    VSpace(10.dp)
                }
                RuleIcon(step.type, step.packageName, 72.dp)
                VSpace(10.dp)
                Text(step.label, color = Brand.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(AccessDecider.ruleDescription(step), color = Brand.MutedOnDark, fontSize = 15.sp, textAlign = TextAlign.Center)
                VSpace(18.dp)
                ProgressRing(fraction, ruleColor(step.type), 250.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(TimeFormat.clock(remaining), color = Brand.White, fontSize = 46.sp, fontWeight = FontWeight.Bold)
                        Text("restantes", color = Brand.MutedOnDark, fontSize = 15.sp)
                    }
                }
                VSpace(18.dp)
                InfoCard(dark = true) {
                    Text("Próxima etapa", color = Brand.MutedOnDark, fontSize = 13.sp)
                    VSpace(6.dp)
                    val next = s.nextStep
                    if (next == null) {
                        Text("Esta é a última etapa.", color = Brand.White)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RuleIcon(next.type, next.packageName, 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(next.label, color = Brand.White, modifier = Modifier.weight(1f), fontSize = 16.sp)
                            Text("${next.minutes} min", color = Brand.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (!a11y) {
                    VSpace(12.dp)
                    NoticeCard(
                        "O bloqueio não está ativo: ative o serviço de acessibilidade do AutoTdah.",
                        color = Brand.Orange, action = "Ativar", onAction = { nav.go(Screen.Permissions(false)) },
                    )
                }
                VSpace(18.dp)
                BigButton("Pausar sessão (PIN)", color = Brand.Red, icon = Icons.Filled.Lock, onClick = { nav.go(Screen.Pin(PinAction.PAUSE)) })
                VSpace(10.dp)
                SecondaryButton("Encerrar sessão (PIN)", onDark = true, onClick = { nav.go(Screen.Pin(PinAction.END)) })
                VSpace(16.dp)
            }
        }
    }
}

/** TELA 9 — Pausar ou encerrar com PIN. */
@Composable
fun PinPromptScreen(nav: Nav, action: PinAction) {
    val scope = rememberCoroutineScope()
    val tick = rememberSecondTick()
    var pin by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var needsPinSetup by remember { mutableStateOf(false) }
    val lockMs = remember(tick) { SessionController.pins.lockRemainingMs() }
    val isPause = action == PinAction.PAUSE

    Column(Modifier.fillMaxSize().background(Brand.Navy)) {
        DarkTopBar(if (isPause) "Pausar sessão" else "Encerrar sessão", null, onBack = { nav.back() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VSpace(16.dp)
            Box(Modifier.size(96.dp).clip(CircleShape).background(Brand.NavySoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Brand.White, modifier = Modifier.size(44.dp))
            }
            VSpace(20.dp)
            Text(
                if (isPause) "Digite o PIN para pausar a programação." else "Digite o PIN para encerrar a programação.",
                color = Brand.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
            if (!isPause) {
                VSpace(6.dp)
                Text(
                    "Encerrar finaliza a sessão definitivamente. A etapa atual fica registrada como interrompida.",
                    color = Brand.MutedOnDark, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
            }
            VSpace(20.dp)
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("PIN") },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                trailingIcon = {
                    TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ocultar" else "Ver", color = Brand.Orange) }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Brand.White, unfocusedTextColor = Brand.White,
                    focusedBorderColor = Brand.Orange, unfocusedBorderColor = Brand.MutedOnDark,
                    focusedLabelColor = Brand.Orange, unfocusedLabelColor = Brand.MutedOnDark, cursorColor = Brand.Orange,
                ),
            )
            if (lockMs > 0) {
                VSpace(10.dp)
                Text("Muitas tentativas incorretas. Aguarde ${TimeFormat.clock(lockMs)}.", color = Brand.Orange, textAlign = TextAlign.Center)
            }
            message?.let {
                VSpace(10.dp)
                Text(it, color = Brand.Orange, textAlign = TextAlign.Center)
            }
            VSpace(24.dp)
            BigButton(
                if (isPause) "Pausar sessão" else "Encerrar sessão",
                color = if (isPause) Brand.Orange else Brand.Red,
                enabled = !busy && lockMs == 0L && pin.length >= 4,
                onClick = {
                    busy = true
                    val typed = pin
                    scope.launch {
                        val res = withContext(Dispatchers.Default) { SessionController.pins.check(typed) }
                        pin = ""
                        busy = false
                        when (res) {
                            is PinCheck.Ok -> {
                                val r = if (isPause) SessionController.pause(res.proof) else SessionController.end(res.proof)
                                when (r) {
                                    is OpResult.Ok -> nav.reset(Screen.Home)
                                    is OpResult.Failure -> message = r.message
                                }
                            }
                            is PinCheck.Wrong -> message = if (res.lockMs > 0) {
                                "PIN incorreto. Aguarde ${TimeFormat.clock(res.lockMs)} para tentar de novo."
                            } else {
                                "PIN incorreto. ${res.attemptsLeft} tentativa(s) antes do bloqueio temporário."
                            }
                            is PinCheck.Locked -> message = "Muitas tentativas. Aguarde ${TimeFormat.clock(res.remainingMs)}."
                            PinCheck.NotConfigured -> {
                                message = "Nenhum PIN configurado."
                                needsPinSetup = true
                            }
                            PinCheck.Unavailable -> message =
                                "O PIN não pôde ser lido neste aparelho. Para recuperar o controle, desative o serviço de acessibilidade do AutoTdah nas Configurações do Android."
                        }
                    }
                },
            )
            if (needsPinSetup) {
                VSpace(10.dp)
                SecondaryButton("Criar PIN", onDark = true, onClick = { nav.go(Screen.PinSetup) })
            }
            VSpace(10.dp)
            SecondaryButton("Cancelar", onDark = true, onClick = { nav.back() })
        }
    }
}

/** TELA 8 — Bloqueio de aplicativo (exibida pela BlockActivity). */
@Composable
fun BlockScreen(
    blockedPkg: String?,
    onHome: () -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenAutoTdah: () -> Unit,
    onNoLongerBlocked: () -> Unit,
) {
    val session by SessionController.session.collectAsState()
    val tick = rememberSecondTick()
    val s = session
    val rule = AccessDecider.activeRule(s)
    val stillBlocked = remember(tick, s, blockedPkg) {
        rule != null && blockedPkg != null &&
            AccessDecider.decide(rule, blockedPkg, SessionController.catalog.accessContext(SessionController.settings.current)) == AccessDecision.BLOCK
    }
    LaunchedEffect(stillBlocked) { if (!stillBlocked) onNoLongerBlocked() }
    val blockedLabel = remember(blockedPkg) { blockedPkg?.let { SessionController.catalog.label(it) } }

    Column(
        Modifier.fillMaxSize().background(Brand.Navy).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VSpace(24.dp)
        BlockedSymbol(120.dp)
        VSpace(24.dp)
        Text("Acesso bloqueado nesta etapa.", color = Brand.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        VSpace(6.dp)
        Text(
            "${blockedLabel ?: "Este aplicativo"} não está permitido agora.",
            color = Brand.MutedOnDark, fontSize = 16.sp, textAlign = TextAlign.Center,
        )
        VSpace(24.dp)
        if (s != null && rule != null) {
            val remaining = remember(tick, s) { SessionController.remainingMs() }
            InfoCard(dark = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Etapa atual", color = Brand.MutedOnDark, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    StatusChip("${s.currentIndex + 1} de ${s.steps.size}", Brand.Orange)
                }
                VSpace(8.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RuleIcon(rule.type, rule.packageName, 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(rule.label, color = Brand.White, fontWeight = FontWeight.SemiBold)
                        Text(AccessDecider.ruleDescription(rule), color = Brand.MutedOnDark, fontSize = 13.sp)
                    }
                }
                VSpace(8.dp)
                Text("${TimeFormat.clock(remaining)} restantes", color = Brand.Orange, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                s.nextStep?.let {
                    VSpace(8.dp)
                    Text("Próxima etapa: ${it.label} — ${it.minutes} min", color = Brand.MutedOnDark, fontSize = 14.sp)
                }
            }
        }
        VSpace(24.dp)
        BigButton("Ir para a tela inicial", onClick = onHome)
        if (rule != null && rule.type == RuleType.APP && rule.packageName != null) {
            VSpace(10.dp)
            SecondaryButton("Abrir ${rule.label}", onDark = true, onClick = { onOpenApp(rule.packageName) })
        }
        VSpace(10.dp)
        SecondaryButton("Abrir AutoTdah", onDark = true, onClick = onOpenAutoTdah)
    }
}
