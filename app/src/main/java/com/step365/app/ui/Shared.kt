package com.step365.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

fun stepNumber(value: Long): String = NumberFormat.getIntegerInstance().format(value)
fun decimal(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)
fun percent(fraction: Double): String = "${(fraction.coerceIn(0.0, 1.0) * 100).toInt()}%"

@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content
        )
    }
}

@Composable
fun ScreenTitle(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(eyebrow.uppercase(), color = Lavender, style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MutedText, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun StepCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun ProgressLine(fraction: Double, color: Color = Lavender) {
    LinearProgressIndicator(
        progress = { fraction.toFloat().coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        color = color,
        trackColor = Color(0xFF3B3349)
    )
}

@Composable
fun StatValue(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color(0xFFF5F0FF), detail: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(label, color = MutedText, style = MaterialTheme.typography.labelLarge)
        if (detail != null) Text(detail, color = MutedText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MutedText, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
fun StatusDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).background(color, RoundedCornerShape(10.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MutedText)
    }
}
