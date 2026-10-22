package br.com.autotdah.regulacaoproduzmais.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import br.com.autotdah.regulacaoproduzmais.accessibility.AppCatalog
import br.com.autotdah.regulacaoproduzmais.accessibility.BlockerAccessibilityService
import br.com.autotdah.regulacaoproduzmais.domain.AccessDecider
import br.com.autotdah.regulacaoproduzmais.domain.Limits
import br.com.autotdah.regulacaoproduzmais.domain.OpResult
import br.com.autotdah.regulacaoproduzmais.domain.StepValidator
import br.com.autotdah.regulacaoproduzmais.domain.TimeFormat
import br.com.autotdah.regulacaoproduzmais.domain.ValidationResult
import br.com.autotdah.regulacaoproduzmais.model.DraftStep
import br.com.autotdah.regulacaoproduzmais.model.RuleType
import br.com.autotdah.regulacaoproduzmais.model.Step
import br.com.autotdah.regulacaoproduzmais.model.Template
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
import br.com.autotdah.regulacaoproduzmais.ui.rememberSecondTick
import br.com.autotdah.regulacaoproduzmais.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Remove etapas totalmente vazias do fim/meio (as “não configuradas ainda”). */
private fun meaningfulDrafts(): List<DraftStep> =
    EditorDraft.steps.filterNot { it.type == null && it.minutesText.isBlank() }

/** TELA 3 — Configurar programação. */
@Composable
fun EditorScreen(nav: Nav) {
    val steps = EditorDraft.steps
    var showSave by remember { mutableStateOf(false) }
    var showLoad by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    val active = SessionController.session.value?.status?.isActive == true
    val total = StepValidator.totalMinutes(steps)
    val errors = EditorDraft.errors

    ScreenScaffold("Nova programação", onBack = { nav.back() }, subtitle = "Até ${Limits.MAX_STEPS} etapas · 1 a ${Limits.MAX_MINUTES} min cada") {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Escolha o aplicativo (ou regra) e os minutos de cada etapa. Não há horários fixos: a sequência começa quando você iniciar e avança sozinha.",
                    color = Brand.Muted, fontSize = 14.sp,
                )
            }
            if (active) {
                item { NoticeCard("Já existe uma programação ativa. Encerre-a (com PIN) antes de iniciar outra.", color = Brand.Orange) }
            }
            itemsIndexed(steps) { i, d ->
                StepRow(
                    index = i,
                    draft = d,
                    error = errors.filter { it.index == i }.joinToString("\n") { it.message }.ifBlank { null },
                    canUp = i > 0,
                    canDown = i < steps.lastIndex,
                    canDelete = steps.size > 1,
                    onPick = { nav.go(Screen.Picker(i)) },
                    onMinutes = { txt -> steps[i] = d.copy(minutesText = txt.filter { it.isDigit() }.take(3)) },
                    onUp = { val x = steps.removeAt(i); steps.add(i - 1, x); EditorDraft.errors = emptyList() },
                    onDown = { val x = steps.removeAt(i); steps.add(i + 1, x); EditorDraft.errors = emptyList() },
                    onDelete = { steps.removeAt(i); EditorDraft.errors = emptyList() },
                )
            }
            val general = errors.filter { it.index == null }
            if (general.isNotEmpty()) {
                item { NoticeCard(general.joinToString("\n") { it.message }) }
            }
            if (steps.size < Limits.MAX_STEPS) {
                item {
                    SecondaryButton("+ Adicionar etapa", onClick = { steps.add(DraftStep()) })
                }
            }
            item {
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Duração total", color = Brand.Muted, modifier = Modifier.weight(1f))
                        Text(TimeFormat.duration(total * 60_000L), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Brand.TextDark)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Abrir modelo", onClick = { showLoad = true }, modifier = Modifier.weight(1f))
                    SecondaryButton("Salvar como modelo", onClick = { showSave = true }, modifier = Modifier.weight(1f))
                }
            }
            info?.let { msg -> item { Text(msg, color = Brand.Green, fontSize = 14.sp) } }
        }
        Column(Modifier.padding(16.dp)) {
            BigButton("Iniciar programação", enabled = !active, icon = Icons.Filled.PlayArrow, onClick = {
                val drafts = meaningfulDrafts()
                when (val r = StepValidator.validate(drafts) { SessionController.catalog.isInstalled(it) }) {
                    is ValidationResult.Valid -> {
                        EditorDraft.load(drafts)
                        EditorDraft.validated = r.steps
                        nav.go(Screen.Review)
                    }
                    is ValidationResult.Invalid -> {
                        EditorDraft.load(drafts)
                        EditorDraft.errors = r.errors
                    }
                }
            })
        }
    }

    if (showSave) {
        SaveTemplateDialog(
            onDismiss = { showSave = false },
            onSaved = { name -> showSave = false; info = "Modelo “$name” salvo." },
        )
    }
    if (showLoad) {
        LoadTemplateDialog(onDismiss = { showLoad = false }, onPick = { t ->
            EditorDraft.load(t.steps.map { StepValidator.toDraft(it) })
            showLoad = false
            info = "Modelo “${t.name}” carregado."
        })
    }
}

@Composable
private fun StepRow(
    index: Int,
    draft: DraftStep,
    error: String?,
    canUp: Boolean,
    canDown: Boolean,
    canDelete: Boolean,
    onPick: () -> Unit,
    onMinutes: (String) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val pkg = draft.packageName
    val installed = remember(pkg) { pkg == null || SessionController.catalog.isInstalled(pkg) }
    val missingApp = draft.type == RuleType.APP && !installed
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Brand.Field),
                contentAlignment = Alignment.Center,
            ) { Text("${index + 1}", fontWeight = FontWeight.Bold, color = Brand.Navy) }
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Brand.Field).clickable(onClick = onPick)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RuleIcon(draft.type, draft.packageName, 24.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    draft.label ?: "Selecione um aplicativo",
                    color = if (draft.type == null) Brand.Muted else Brand.TextDark,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = draft.minutesText,
                onValueChange = onMinutes,
                modifier = Modifier.width(84.dp),
                singleLine = true,
                suffix = { Text("min", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = error != null && error.contains("duração"),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onUp, enabled = canUp) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Subir etapa") }
            IconButton(onClick = onDown, enabled = canDown) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Descer etapa") }
            IconButton(onClick = onDelete, enabled = canDelete) { Icon(Icons.Filled.Delete, contentDescription = "Excluir etapa") }
        }
        if (missingApp) Text("Este aplicativo foi desinstalado. Escolha outro.", color = Brand.Orange, fontSize = 13.sp)
        if (error != null) Text(error, color = Brand.Red, fontSize = 13.sp)
    }
}

@Composable
private fun SaveTemplateDialog(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Salvar como modelo") },
        text = {
            Column {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nome do modelo") }, singleLine = true)
                error?.let { Text(it, color = Brand.Red, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = name.trim()
                val r = StepValidator.validate(meaningfulDrafts()) { SessionController.catalog.isInstalled(it) }
                if (trimmed.isEmpty()) {
                    error = "Informe um nome."
                } else if (r is ValidationResult.Invalid) {
                    error = r.errors.first().message
                } else if (r is ValidationResult.Valid) {
                    try {
                        SessionController.templates.upsert(Template(UUID.randomUUID().toString(), trimmed, r.steps, System.currentTimeMillis()))
                        onSaved(trimmed)
                    } catch (e: Exception) {
                        error = "Não foi possível salvar: ${e.message}"
                    }
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun LoadTemplateDialog(onDismiss: () -> Unit, onPick: (Template) -> Unit) {
    val templates = remember { SessionController.templates.load() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abrir modelo") },
        text = {
            if (templates.isEmpty()) {
                Text("Nenhum modelo salvo ainda.")
            } else {
                LazyColumn(Modifier.height(320.dp)) {
                    items(templates, key = { it.id }) { t ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(t) }.padding(vertical = 10.dp)) {
                            Text(t.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${t.steps.size} etapa(s) · ${TimeFormat.duration(t.steps.sumOf { it.durationMs })}",
                                color = Brand.Muted, fontSize = 13.sp,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

/** TELA 4 — Selecionar aplicativo. */
@Composable
fun AppPickerScreen(nav: Nav, index: Int) {
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppCatalog.AppEntry>?>(null) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { SessionController.catalog.launchableApps(force = true) }
    }

    fun choose(type: RuleType, pkg: String?, label: String) {
        if (index in EditorDraft.steps.indices) {
            val d = EditorDraft.steps[index]
            EditorDraft.steps[index] = d.copy(type = type, packageName = pkg, label = label)
            EditorDraft.errors = EditorDraft.errors.filterNot { it.index == index }
        }
        nav.back()
    }

    val q = query.trim()
    ScreenScaffold("Selecione o aplicativo", onBack = { nav.back() }, subtitle = "Etapa ${index + 1}") {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Buscar aplicativo…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            if (q.isEmpty() || Step.LABEL_ALL.contains(q, ignoreCase = true)) {
                item { PickRow(RuleType.ALL, null, Step.LABEL_ALL, "Uso livre durante a etapa") { choose(RuleType.ALL, null, Step.LABEL_ALL) } }
            }
            if (q.isEmpty() || Step.LABEL_NONE.contains(q, ignoreCase = true)) {
                item { PickRow(RuleType.NONE, null, Step.LABEL_NONE, "Bloqueia todos os apps monitorados") { choose(RuleType.NONE, null, Step.LABEL_NONE) } }
            }
            val list = apps
            if (list == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else {
                val filtered = if (q.isEmpty()) list else list.filter { it.label.contains(q, ignoreCase = true) }
                items(filtered, key = { it.packageName }) { app ->
                    PickRow(RuleType.APP, app.packageName, app.label, null) { choose(RuleType.APP, app.packageName, app.label) }
                }
                if (filtered.isEmpty()) {
                    item { Text("Nenhum aplicativo encontrado.", color = Brand.Muted, modifier = Modifier.padding(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PickRow(type: RuleType, pkg: String?, label: String, hint: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RuleIcon(type, pkg, 36.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, fontSize = 16.sp, color = Brand.TextDark)
            if (hint != null) Text(hint, fontSize = 13.sp, color = Brand.Muted)
        }
    }
    HorizontalDivider(color = Brand.Field)
}

/** TELA 5 — Revisar programação. */
@Composable
fun ReviewScreen(nav: Nav) {
    val context = LocalContext.current
    val steps = EditorDraft.validated
    val tick = rememberSecondTick()
    val hasPin = remember(tick) { SessionController.pins.hasPin() }
    val a11y = remember(tick) { BlockerAccessibilityService.isEnabled(context) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmNoA11y by remember { mutableStateOf(false) }

    fun doStart() {
        when (val r = SessionController.start(steps)) {
            is OpResult.Ok -> nav.reset(Screen.Home, Screen.Running)
            is OpResult.Failure -> error = r.message
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { doStart() }

    fun startWithNotificationCheck() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            doStart()
        }
    }

    ScreenScaffold("Programação configurada", onBack = { nav.back() }) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(steps) { i, step ->
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, color = Brand.Navy, modifier = Modifier.width(24.dp))
                        RuleIcon(step.type, step.packageName, 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Etapa ${i + 1} — ${step.label}", fontWeight = FontWeight.SemiBold, color = Brand.TextDark)
                            Text(AccessDecider.ruleDescription(step), color = Brand.Muted, fontSize = 13.sp)
                        }
                        Text("${step.minutes} min", fontWeight = FontWeight.SemiBold, color = Brand.TextDark)
                    }
                }
            }
            item {
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Duração total", color = Brand.Muted, modifier = Modifier.weight(1f))
                        Text(TimeFormat.duration(steps.sumOf { it.durationMs }), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (!hasPin) {
                item {
                    NoticeCard(
                        "Crie um PIN antes de começar. Ele será exigido para pausar ou encerrar a sessão.",
                        color = Brand.Orange, action = "Criar PIN", onAction = { nav.go(Screen.PinSetup) },
                    )
                }
            }
            if (!a11y) {
                item {
                    NoticeCard(
                        "Acessibilidade desativada: sem ela o tempo é contado, mas os aplicativos não são bloqueados.",
                        color = Brand.Orange, action = "Ativar", onAction = { nav.go(Screen.Permissions(false)) },
                    )
                }
            }
            error?.let { item { NoticeCard(it) } }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Editar programação", onClick = { nav.back() })
            BigButton("Iniciar agora", icon = Icons.Filled.PlayArrow, enabled = steps.isNotEmpty(), onClick = {
                when {
                    !hasPin -> nav.go(Screen.PinSetup)
                    !a11y -> confirmNoA11y = true
                    else -> startWithNotificationCheck()
                }
            })
        }
    }

    if (confirmNoA11y) {
        AlertDialog(
            onDismissRequest = { confirmNoA11y = false },
            title = { Text("Bloqueio desativado") },
            text = { Text("Sem o serviço de acessibilidade, o AutoTdah conta o tempo e registra o histórico, mas não consegue bloquear aplicativos.") },
            confirmButton = {
                TextButton(onClick = { confirmNoA11y = false; nav.go(Screen.Permissions(false)) }) { Text("Ativar acessibilidade") }
            },
            dismissButton = {
                TextButton(onClick = { confirmNoA11y = false; startWithNotificationCheck() }) { Text("Iniciar mesmo assim") }
            },
        )
    }
}
