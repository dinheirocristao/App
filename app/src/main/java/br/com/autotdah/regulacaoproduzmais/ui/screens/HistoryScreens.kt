package br.com.autotdah.regulacaoproduzmais.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.autotdah.regulacaoproduzmais.domain.TimeFormat
import br.com.autotdah.regulacaoproduzmais.history.SYSTEM_SESSION_ID
import br.com.autotdah.regulacaoproduzmais.history.displayStatus
import br.com.autotdah.regulacaoproduzmais.model.EventType
import br.com.autotdah.regulacaoproduzmais.model.HistoryEvent
import br.com.autotdah.regulacaoproduzmais.model.SessionStatus
import br.com.autotdah.regulacaoproduzmais.model.SessionSummary
import br.com.autotdah.regulacaoproduzmais.service.SessionController
import br.com.autotdah.regulacaoproduzmais.ui.InfoCard
import br.com.autotdah.regulacaoproduzmais.ui.Nav
import br.com.autotdah.regulacaoproduzmais.ui.RuleIcon
import br.com.autotdah.regulacaoproduzmais.ui.Screen
import br.com.autotdah.regulacaoproduzmais.ui.ScreenScaffold
import br.com.autotdah.regulacaoproduzmais.ui.SectionTitle
import br.com.autotdah.regulacaoproduzmais.ui.StatusChip
import br.com.autotdah.regulacaoproduzmais.ui.VSpace
import br.com.autotdah.regulacaoproduzmais.ui.formatDateTime
import br.com.autotdah.regulacaoproduzmais.ui.formatTime
import br.com.autotdah.regulacaoproduzmais.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun statusColor(text: String): Color = when (text) {
    SessionStatus.COMPLETED.label -> Brand.Green
    SessionStatus.ENDED_EARLY.label -> Brand.Orange
    "Em andamento" -> Brand.Green
    "Pausada" -> Brand.Orange
    else -> Brand.Red
}

/** TELA 10 — Histórico de sessões. */
@Composable
fun HistoryScreen(nav: Nav) {
    var sessions by remember { mutableStateOf<List<SessionSummary>?>(null) }
    var system by remember { mutableStateOf<List<HistoryEvent>>(emptyList()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = SessionController.history
            val list = db.sessions()
            val sys = db.events(SYSTEM_SESSION_ID)
            withContext(Dispatchers.Main) {
                sessions = list
                system = sys
            }
        }
    }
    val activeId = SessionController.session.value?.takeIf { it.status.isActive }?.id

    ScreenScaffold("Histórico de sessões", onBack = { nav.back() }) {
        val list = sessions
        if (list == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list.isEmpty()) {
                item { Text("Nenhuma sessão registrada ainda.", color = Brand.Muted) }
            }
            items(list, key = { it.id }) { s ->
                val status = displayStatus(s, activeId)
                InfoCard(Modifier.clickable { nav.go(Screen.HistoryDetail(s.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.startedWallMs?.let { formatDateTime(it) } ?: "Data desconhecida",
                            fontWeight = FontWeight.SemiBold, color = Brand.TextDark, modifier = Modifier.weight(1f),
                        )
                        StatusChip(status, statusColor(status))
                    }
                    VSpace(6.dp)
                    Text(
                        "Planejado ${TimeFormat.duration(s.plannedMs)} · executado ${TimeFormat.duration(s.executedMs)}",
                        color = Brand.Muted, fontSize = 14.sp,
                    )
                    Text(
                        "${s.stepsCompleted} de ${s.steps.size} etapas concluídas" +
                            (if (s.stepsInterrupted > 0) " · ${s.stepsInterrupted} interrompida" else "") +
                            (if (s.pauses > 0) " · ${s.pauses} pausa(s)" else ""),
                        color = Brand.Muted, fontSize = 14.sp,
                    )
                }
            }
            if (system.isNotEmpty()) {
                item { SectionTitle("Ocorrências técnicas") }
                items(system, key = { it.id }) { e -> EventRow(e) }
            }
        }
    }
}

@Composable
fun HistoryDetailScreen(nav: Nav, id: String) {
    var summary by remember { mutableStateOf<SessionSummary?>(null) }
    var events by remember { mutableStateOf<List<HistoryEvent>>(emptyList()) }
    LaunchedEffect(id) {
        val (s, e) = withContext(Dispatchers.IO) {
            SessionController.history.sessions().firstOrNull { it.id == id } to SessionController.history.events(id)
        }
        summary = s
        events = e
    }
    val activeId = SessionController.session.value?.takeIf { it.status.isActive }?.id

    ScreenScaffold("Detalhes da sessão", onBack = { nav.back() }) {
        val s = summary
        if (s == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val status = displayStatus(s, activeId)
            item {
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.startedWallMs?.let { formatDateTime(it) } ?: "—", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusChip(status, statusColor(status))
                    }
                    VSpace(6.dp)
                    Text("Planejado: ${TimeFormat.duration(s.plannedMs)}", color = Brand.Muted)
                    Text("Executado: ${TimeFormat.duration(s.executedMs)}", color = Brand.Muted)
                    s.endedWallMs?.let { Text("Término: ${formatDateTime(it)}", color = Brand.Muted) }
                    if (status == "Estado desconhecido") {
                        VSpace(6.dp)
                        Text(
                            "Não há registro confiável de como esta sessão terminou (por exemplo, dados apagados ou ilegíveis).",
                            color = Brand.Red, fontSize = 13.sp,
                        )
                    }
                }
            }
            item { SectionTitle("Etapas") }
            itemsIndexed(s.steps) { i, step ->
                val started = events.firstOrNull { it.type == EventType.STEP_STARTED && it.stepIndex == i }
                val done = events.firstOrNull { it.type == EventType.STEP_COMPLETED && it.stepIndex == i }
                val cut = events.firstOrNull { it.type == EventType.STEP_INTERRUPTED && it.stepIndex == i }
                val (label, color) = when {
                    done != null -> "Concluída" to Brand.Green
                    cut != null -> "Interrompida" to Brand.Orange
                    started != null -> (if (status == "Em andamento" || status == "Pausada") "Em curso" else "Sem registro de término") to Brand.Muted
                    else -> "Não executada" to Brand.Muted
                }
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RuleIcon(step.type, step.packageName, 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Etapa ${i + 1}: ${step.label}", fontWeight = FontWeight.SemiBold)
                            val end = done ?: cut
                            Text(
                                if (started != null) {
                                    formatTime(started.wallMs) + (end?.let { " – " + formatTime(it.wallMs) } ?: "") + " (${step.minutes} min)"
                                } else {
                                    "${step.minutes} min"
                                },
                                color = Brand.Muted, fontSize = 13.sp,
                            )
                        }
                        StatusChip(label, color)
                    }
                }
            }
            item { SectionTitle("Eventos") }
            items(events, key = { it.id }) { e -> EventRow(e) }
        }
    }
}

@Composable
private fun EventRow(e: HistoryEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(formatDateTime(e.wallMs), color = Brand.Muted, fontSize = 12.sp, modifier = Modifier.width(110.dp))
        Column(Modifier.weight(1f)) {
            Text(e.type.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Brand.TextDark)
            e.detail?.let { Text(it, fontSize = 13.sp, color = Brand.Muted) }
        }
    }
}
