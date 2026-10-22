package br.com.autotdah.regulacaoproduzmais.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import br.com.autotdah.regulacaoproduzmais.accessibility.AppCatalog
import br.com.autotdah.regulacaoproduzmais.accessibility.BlockerAccessibilityService
import br.com.autotdah.regulacaoproduzmais.data.AppSettings
import br.com.autotdah.regulacaoproduzmais.domain.Limits
import br.com.autotdah.regulacaoproduzmais.domain.StepValidator
import br.com.autotdah.regulacaoproduzmais.domain.TimeFormat
import br.com.autotdah.regulacaoproduzmais.model.Template
import br.com.autotdah.regulacaoproduzmais.security.PinCheck
import br.com.autotdah.regulacaoproduzmais.security.PinHasher
import br.com.autotdah.regulacaoproduzmais.service.Notifications
import br.com.autotdah.regulacaoproduzmais.service.SessionController
import br.com.autotdah.regulacaoproduzmais.ui.BigButton
import br.com.autotdah.regulacaoproduzmais.ui.EditorDraft
import br.com.autotdah.regulacaoproduzmais.ui.InfoCard
import br.com.autotdah.regulacaoproduzmais.ui.Nav
import br.com.autotdah.regulacaoproduzmais.ui.NoticeCard
import br.com.autotdah.regulacaoproduzmais.ui.RuleIcon
import br.com.autotdah.regulacaoproduzmais.ui.Screen
import br.com.autotdah.regulacaoproduzmais.ui.ScreenScaffold
import br.com.autotdah.regulacaoproduzmais.ui.SecondaryButton
import br.com.autotdah.regulacaoproduzmais.ui.SectionTitle
import br.com.autotdah.regulacaoproduzmais.ui.StatusChip
import br.com.autotdah.regulacaoproduzmais.ui.VSpace
import br.com.autotdah.regulacaoproduzmais.ui.rememberSecondTick
import br.com.autotdah.regulacaoproduzmais.ui.theme.Brand
import br.com.autotdah.regulacaoproduzmais.model.RuleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun sessionActive() = SessionController.session.value?.status?.isActive == true

private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** TELA 12 — Configurações. */
@Composable
fun SettingsScreen(nav: Nav) {
    val context = LocalContext.current
    val tick = rememberSecondTick()
    val active = remember(tick) { sessionActive() }
    val a11y = remember(tick) { BlockerAccessibilityService.isEnabled(context) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    ScreenScaffold("Configurações", onBack = { nav.back() }) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingRow(Icons.Filled.Lock, "Senha de segurança (PIN)", "Defina ou altere seu PIN") { nav.go(Screen.PinSetup) }
            SettingRow(Icons.Filled.Star, "Aplicativos monitorados", "Escolha quais apps são controlados") { nav.go(Screen.Monitored) }
            SettingRow(
                Icons.Filled.Build, "Permissões e serviço",
                if (a11y) "Acessibilidade ativa" else "Acessibilidade DESATIVADA — toque para ativar",
            ) { nav.go(Screen.Permissions(false)) }
            SettingRow(Icons.AutoMirrored.Filled.List, "Modelos de programação", "Abrir, usar ou excluir modelos") { nav.go(Screen.Templates) }
            SettingRow(Icons.Filled.Info, "Sobre e limitações", "Como o AutoTdah funciona e o que o Android não permite") { nav.go(Screen.About) }
            SettingRow(
                Icons.Filled.Delete, "Limpar histórico",
                if (active) "Indisponível durante uma sessão ativa" else "Apaga todos os registros de sessões",
            ) { if (!active) confirmClear = true }
            SettingRow(
                Icons.Filled.Refresh, "Restaurar configurações",
                if (active) "Indisponível durante uma sessão ativa" else "Volta os apps monitorados ao padrão (o PIN é mantido)",
            ) { if (!active) confirmReset = true }
            message?.let { Text(it, color = Brand.Green) }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Limpar histórico?",
            text = "Todos os registros de sessões serão apagados. Esta ação não pode ser desfeita.",
            confirm = "Apagar",
            onConfirm = {
                confirmClear = false
                message = try {
                    SessionController.history.clear()
                    "Histórico apagado."
                } catch (e: Exception) {
                    "Falha ao apagar: ${e.message}"
                }
            },
            onDismiss = { confirmClear = false },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Restaurar configurações?",
            text = "Os aplicativos monitorados voltam ao padrão (todos). O PIN e o histórico são mantidos.",
            confirm = "Restaurar",
            onConfirm = {
                confirmReset = false
                SessionController.settings.update { AppSettings(onboardingDone = true, pinBlob = it.pinBlob, attempts = it.attempts) }
                message = "Configurações restauradas."
            },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = Brand.Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    InfoCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Brand.Navy, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = Brand.TextDark)
                Text(subtitle, fontSize = 13.sp, color = Brand.Muted)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Brand.Muted)
        }
    }
}

/** Permissões explicadas antes de levar o usuário à tela do Android. */
@Composable
fun PermissionsScreen(nav: Nav, onboarding: Boolean) {
    val context = LocalContext.current
    val tick = rememberSecondTick()
    val a11y = remember(tick) { BlockerAccessibilityService.isEnabled(context) }
    val notif = remember(tick) { Notifications.canPost(context) }
    val battery = remember(tick) {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    val hasPin = remember(tick) { SessionController.pins.hasPin() }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    ScreenScaffold(if (onboarding) "Configuração inicial" else "Permissões e serviço", onBack = { nav.back() }) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onboarding) {
                Text(
                    "Para o AutoTdah funcionar, o Android exige algumas autorizações. Cada uma é explicada antes de abrir a tela do sistema.",
                    color = Brand.Muted,
                )
            }
            PermissionCard(
                title = "1. Serviço de acessibilidade (essencial)",
                granted = a11y,
                explanation = "Permite saber qual aplicativo foi aberto, para mostrar a tela de bloqueio quando ele não estiver liberado na etapa atual. " +
                    "O AutoTdah não lê textos, mensagens ou senhas, não captura a tela e não envia nada para a internet.",
                action = "Abrir acessibilidade",
            ) { openSettings(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            if (!a11y) {
                NoticeCard(
                    "Instalou pelo APK e a opção aparece bloqueada (“configuração restrita”)? Abra as informações do app, toque no menu ⋮ e escolha “Permitir configurações restritas”. Depois volte e ative o AutoTdah em Acessibilidade.",
                    color = Brand.Orange,
                    action = "Abrir informações do app",
                ) {
                    openSettings(
                        context,
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                }
            }
            PermissionCard(
                title = "2. Notificações",
                granted = notif,
                explanation = "Mostram a etapa atual e o tempo restante, avisam a troca de etapa e a pausa após reiniciar o celular.",
                action = "Permitir notificações",
            ) {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openSettings(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
            PermissionCard(
                title = "3. Bateria sem restrição (recomendado)",
                granted = battery,
                explanation = "Alguns fabricantes encerram apps em segundo plano para economizar bateria. Deixar o AutoTdah “sem restrição” reduz interrupções do serviço.",
                action = "Abrir otimização de bateria",
            ) { openSettings(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            PermissionCard(
                title = "4. PIN de segurança",
                granted = hasPin,
                explanation = "Exigido para pausar ou encerrar uma sessão, evitando interrupções por impulso.",
                action = if (hasPin) "Alterar PIN" else "Criar PIN",
            ) { nav.go(Screen.PinSetup) }
            if (onboarding) {
                VSpace(4.dp)
                BigButton("Concluir", onClick = {
                    SessionController.settings.update { it.copy(onboardingDone = true) }
                    nav.reset(Screen.Home)
                })
            }
        }
    }
}

@Composable
private fun PermissionCard(title: String, granted: Boolean, explanation: String, action: String, onAction: () -> Unit) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Brand.TextDark, modifier = Modifier.weight(1f))
            StatusChip(if (granted) "Ativo" else "Desativado", if (granted) Brand.Green else Brand.Red)
        }
        VSpace(6.dp)
        Text(explanation, color = Brand.Muted, fontSize = 14.sp)
        VSpace(10.dp)
        SecondaryButton(action, onClick = onAction)
    }
}

/** Criar ou alterar o PIN. */
@Composable
fun PinSetupScreen(nav: Nav) {
    val pins = SessionController.pins
    val scope = rememberCoroutineScope()
    val hasPin = remember { pins.hasPin() }
    // Se o registro ficou ilegível e não há sessão ativa, permite recriar sem o PIN antigo.
    val requireCurrent = remember { hasPin && (pins.isReadable() || sessionActive()) }
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    ScreenScaffold(if (hasPin) "Alterar PIN" else "Criar PIN", onBack = { nav.back() }) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Use de 4 a 8 dígitos. O PIN será pedido para pausar ou encerrar uma sessão. " +
                    "Guarde-o bem: se esquecê-lo durante uma sessão, a saída é desativar a acessibilidade do AutoTdah nas configurações do Android.",
                color = Brand.Muted, fontSize = 14.sp,
            )
            if (requireCurrent) PinField("PIN atual", current) { current = it }
            PinField("Novo PIN", newPin) { newPin = it }
            PinField("Confirmar novo PIN", confirm) { confirm = it }
            message?.let { Text(it, color = Brand.Red) }
            BigButton("Salvar PIN", enabled = !busy, onClick = {
                when {
                    !PinHasher.isValidFormat(newPin) -> message = "O PIN deve ter de 4 a 8 dígitos."
                    newPin != confirm -> message = "A confirmação não corresponde ao novo PIN."
                    else -> {
                        busy = true
                        scope.launch {
                            val check = if (requireCurrent) withContext(Dispatchers.Default) { pins.check(current) } else null
                            if (requireCurrent && check !is PinCheck.Ok) {
                                message = when (check) {
                                    is PinCheck.Locked -> "Muitas tentativas. Aguarde ${TimeFormat.clock(check.remainingMs)}."
                                    is PinCheck.Wrong -> if (check.lockMs > 0) "PIN atual incorreto. Aguarde ${TimeFormat.clock(check.lockMs)}." else "PIN atual incorreto."
                                    else -> "Não foi possível verificar o PIN atual."
                                }
                                busy = false
                            } else {
                                val saved = withContext(Dispatchers.Default) {
                                    try { pins.setPin(newPin); true } catch (_: Exception) { false }
                                }
                                busy = false
                                if (!saved) message = "Falha ao salvar o PIN."
                                if (saved) nav.back()
                            }
                        }
                    }
                }
            })
        }
    }
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(8)) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}

/** Escolha dos aplicativos abrangidos pelo bloqueio. */
@Composable
fun MonitoredAppsScreen(nav: Nav) {
    val settings = SessionController.settings
    val locked = remember { sessionActive() }
    var monitorAll by remember { mutableStateOf(settings.current.monitorAll) }
    val selected = remember { mutableStateListOf<String>().apply { addAll(settings.current.monitoredPackages) } }
    var apps by remember { mutableStateOf<List<AppCatalog.AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { SessionController.catalog.launchableApps(force = true) } }

    fun save() {
        settings.update { it.copy(monitorAll = monitorAll, monitoredPackages = selected.toSet()) }
    }

    ScreenScaffold("Aplicativos monitorados", onBack = { nav.back() }) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Text(
                    "Configurações, Telefone, teclado, tela inicial e o próprio AutoTdah nunca são bloqueados.",
                    color = Brand.Muted, fontSize = 14.sp,
                )
                VSpace(8.dp)
                if (locked) {
                    NoticeCard("Para evitar desbloqueios por impulso, esta lista só pode ser alterada sem sessão ativa.", color = Brand.Orange)
                    VSpace(8.dp)
                }
                Row(Modifier.fillMaxWidth().clickable(enabled = !locked) { monitorAll = true; save() }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = monitorAll, onClick = { monitorAll = true; save() }, enabled = !locked)
                    Text("Todos os aplicativos da gaveta (recomendado)")
                }
                Row(Modifier.fillMaxWidth().clickable(enabled = !locked) { monitorAll = false; save() }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !monitorAll, onClick = { monitorAll = false; save() }, enabled = !locked)
                    Text("Somente os selecionados abaixo")
                }
                if (!monitorAll) {
                    OutlinedTextField(
                        query, { query = it }, placeholder = { Text("Buscar aplicativo…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
            if (!monitorAll) {
                val list = apps
                if (list == null) {
                    item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else {
                    val q = query.trim()
                    items(list.filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) }, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !locked) {
                                if (checked) selected.remove(app.packageName) else selected.add(app.packageName)
                                save()
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, enabled = !locked)
                            RuleIcon(RuleType.APP, app.packageName, 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(app.label)
                        }
                    }
                }
            }
        }
    }
}

/** Modelos de programação salvos. */
@Composable
fun TemplatesScreen(nav: Nav) {
    var templates by remember { mutableStateOf(SessionController.templates.load()) }
    var toDelete by remember { mutableStateOf<Template?>(null) }

    ScreenScaffold("Modelos de programação", onBack = { nav.back() }) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (templates.isEmpty()) {
                item { Text("Nenhum modelo salvo. Na tela de programação, use “Salvar como modelo”.", color = Brand.Muted) }
            }
            items(templates, key = { it.id }) { t ->
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontWeight = FontWeight.SemiBold)
                            Text("${t.steps.size} etapa(s) · ${TimeFormat.duration(t.steps.sumOf { it.durationMs })}", color = Brand.Muted, fontSize = 13.sp)
                        }
                        IconButton(onClick = { toDelete = t }) { Icon(Icons.Filled.Delete, contentDescription = "Excluir modelo") }
                    }
                    t.steps.forEachIndexed { i, s ->
                        Text("${i + 1}. ${s.label} — ${s.minutes} min", fontSize = 14.sp, color = Brand.TextDark)
                    }
                    VSpace(8.dp)
                    SecondaryButton("Usar este modelo", onClick = {
                        EditorDraft.load(t.steps.take(Limits.MAX_STEPS).map { StepValidator.toDraft(it) })
                        nav.go(Screen.Editor)
                    })
                }
            }
        }
    }
    toDelete?.let { t ->
        ConfirmDialog(
            title = "Excluir modelo?",
            text = "O modelo “${t.name}” será excluído. Sessões já registradas não são afetadas.",
            confirm = "Excluir",
            onConfirm = {
                try { SessionController.templates.delete(t.id) } catch (_: Exception) { }
                templates = SessionController.templates.load()
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }
}

/** Informações e limitações reais do Android. */
@Composable
fun AboutScreen(nav: Nav) {
    ScreenScaffold("Sobre e limitações", onBack = { nav.back() }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoCard {
                Text("AutoTdah — Regulação produz +", fontWeight = FontWeight.Bold)
                Text("Versão 1.0.0 · funciona sem internet · nenhum dado sai do aparelho.", color = Brand.Muted, fontSize = 14.sp)
            }
            SectionTitle("Como funciona")
            AboutText(
                "Você define até ${Limits.MAX_STEPS} etapas (de 1 a ${Limits.MAX_MINUTES} minutos). Ao iniciar, as etapas avançam sozinhas. " +
                    "O tempo corre com a tela desligada e fica congelado durante pausas. Se o celular reiniciar, a sessão é pausada com o tempo restante preservado.",
            )
            AboutText(
                "Regras: “aplicativo específico” libera só ele; “Todos os aplicativos” é período livre; “Nenhum aplicativo” bloqueia todos os monitorados. " +
                    "Configurações, Telefone, teclado, tela inicial e o AutoTdah nunca são bloqueados, para você sempre recuperar o controle do aparelho.",
            )
            SectionTitle("Limitações do Android")
            AboutText("O AutoTdah não é inviolável. Um app comum não tem controle absoluto sobre os demais. É possível contornar o bloqueio:")
            AboutText(
                "• desativando o serviço de acessibilidade nas Configurações;\n" +
                    "• forçando a parada ou desinstalando o AutoTdah;\n" +
                    "• alterando permissões ou o modo de economia de bateria;\n" +
                    "• usando o modo de segurança do Android.",
            )
            AboutText(
                "Fabricantes como Xiaomi, Samsung, Motorola e outros podem encerrar serviços em segundo plano ou atrasar o aviso de inicialização. " +
                    "Nesses casos a contagem continua correta (é recalculada pelo relógio interno), mas a tela de bloqueio pode demorar a aparecer até o app ser reaberto.",
            )
            AboutText(
                "Se o aparelho for desligado de forma abrupta, até cerca de 15 segundos da etapa podem não ser contabilizados (o progresso é salvo a cada 15 s).",
            )
            AboutText("O AutoTdah é uma ferramenta de apoio à autorregulação e não substitui acompanhamento profissional.")
        }
    }
}

@Composable
private fun AboutText(text: String) {
    Text(text, color = Brand.TextDark, fontSize = 15.sp)
    HorizontalDivider(color = Brand.Field)
}
