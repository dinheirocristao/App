package br.com.autotdah.regulacaoproduzmais.ui

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import br.com.autotdah.regulacaoproduzmais.R
import br.com.autotdah.regulacaoproduzmais.model.RuleType
import br.com.autotdah.regulacaoproduzmais.service.SessionController
import br.com.autotdah.regulacaoproduzmais.ui.theme.Brand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay

/** "Auto" em branco (ou azul-marinho sobre fundo claro) e "Tdah" em laranja. */
@Composable
fun BrandName(size: TextUnit = 40.sp, onDark: Boolean = true) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = if (onDark) Brand.White else Brand.Navy)) { append("Auto") }
            withStyle(SpanStyle(color = Brand.Orange)) { append("Tdah") }
        },
        fontSize = size,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun Slogan(onDark: Boolean = true, size: TextUnit = 16.sp) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = if (onDark) Brand.White else Brand.Navy)) { append("Regulação produz ") }
            withStyle(SpanStyle(color = Brand.Orange, fontWeight = FontWeight.Bold)) { append("+") }
        },
        fontSize = size,
    )
}

@Composable
fun Logo(size: Dp) {
    Image(painterResource(R.drawable.ic_logo), contentDescription = "Símbolo do AutoTdah", modifier = Modifier.size(size))
}

/** Estrutura padrão de tela: barra superior azul-marinho + conteúdo. */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    dark: Boolean = false,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(if (dark) Brand.Navy else Brand.Surface)) {
        Row(
            Modifier.fillMaxWidth().background(Brand.Navy).statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Brand.White)
                }
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(title, color = Brand.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, color = Brand.MutedOnDark, fontSize = 13.sp)
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().imePadding(), content = content)
    }
}

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Brand.Orange,
    contentColor: Color = Brand.White,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (onDark) Brand.MutedOnDark else Brand.Navy.copy(alpha = 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (onDark) Brand.White else Brand.Navy),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(text, fontSize = 16.sp)
    }
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, dark: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (dark) Brand.NavySoft else Brand.Card),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun NoticeCard(text: String, color: Color = Brand.Red, action: String? = null, onAction: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(text, color = Brand.TextDark, fontSize = 14.sp)
            if (action != null && onAction != null) {
                Text(
                    action,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp).clickable(onClick = onAction),
                )
            }
        }
    }
}

@Composable
fun StatusChip(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

object IconCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun get(pkg: String): ImageBitmap? = cache[pkg] ?: try {
        SessionController.catalog.icon(pkg)?.toBitmap(96, 96)?.asImageBitmap()?.also { cache[pkg] = it }
    } catch (_: Exception) {
        null
    }
}

/** Ícone da regra: ícone do app, “todos” (verde) ou “nenhum” (vermelho). */
@Composable
fun RuleIcon(type: RuleType?, pkg: String?, size: Dp = 36.dp) {
    when (type) {
        RuleType.APP -> {
            val bmp = remember(pkg) { pkg?.let { IconCache.get(it) } }
            if (bmp != null) {
                Image(bmp, contentDescription = null, modifier = Modifier.size(size))
            } else {
                RoundIcon(Icons.Filled.Check, Brand.Muted, size)
            }
        }
        RuleType.ALL -> RoundIcon(Icons.Filled.Check, Brand.Green, size)
        RuleType.NONE -> RoundIcon(Icons.Filled.Lock, Brand.Red, size)
        null -> Box(Modifier.size(size).clip(CircleShape).background(Brand.Field))
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, color: Color, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Brand.White, modifier = Modifier.size(size * 0.6f))
    }
}

/** Anel de progresso com o tempo restante no centro. */
@Composable
fun ProgressRing(fraction: Float, color: Color, size: Dp, center: @Composable () -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = 14.dp.toPx()
            drawArc(color = Brand.NavyLine, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(stroke))
            drawArc(
                color = color, startAngle = -90f, sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        center()
    }
}

/** Símbolo de bloqueio (círculo cortado). */
@Composable
fun BlockedSymbol(dim: Dp) {
    Canvas(Modifier.size(dim)) {
        val stroke = size.minDimension * 0.11f
        val r = size.minDimension / 2 - stroke / 2
        drawCircle(Brand.Red, radius = r, style = Stroke(stroke))
        val d = r * 0.7071f
        drawLine(Brand.Red, Offset(center.x - d, center.y - d), Offset(center.x + d, center.y + d), strokeWidth = stroke)
    }
}

/**
 * Pulso de 1 s para a interface. A cada pulso pede ao controlador que avance a sessão;
 * a contagem em si é calculada pelo relógio monotônico e não depende deste laço.
 */
@Composable
fun rememberSecondTick(): Long {
    var t by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            SessionController.tick()
            t = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    return t
}

private val dateFormat = ThreadLocal.withInitial { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")) }
private val timeFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")) }

fun formatDateTime(ms: Long): String = dateFormat.get()!!.format(Date(ms))
fun formatTime(ms: Long): String = timeFormat.get()!!.format(Date(ms))

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Brand.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

val ScreenPadding = Modifier.padding(horizontal = 16.dp)

val Spaced8 = Arrangement.spacedBy(8.dp)
val Spaced12 = Arrangement.spacedBy(12.dp)
