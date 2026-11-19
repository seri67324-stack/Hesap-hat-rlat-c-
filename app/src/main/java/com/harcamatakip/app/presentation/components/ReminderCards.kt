package com.harcamatakip.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.harcamatakip.app.domain.usecase.SafeDailyLimit
import com.harcamatakip.app.domain.usecase.SafeSpending

/** Yedek vadesi geldiğinde görünen hafif kart. "Şimdi yedekle" mevcut yedekleme akışını başlatır. */
@Composable
fun BackupReminderCard(
    daysSinceLastBackup: Long?,
    onBackup: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Backup, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Yedek alma zamanı", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (daysSinceLastBackup == null) "Henüz hiç yedek almadın. Verilerin yalnızca bu cihazda duruyor."
                        else "Son yedeğin $daysSinceLastBackup gün önce alındı. Verilerin yalnızca bu cihazda duruyor.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSnooze) { Text("Yarın hatırlat") }
                Button(onClick = onBackup) { Text("Şimdi yedekle") }
            }
        }
    }
}

/** "Ay sonuna kadar günde ortalama X harcayabilirsin" tek satırlık özeti. */
@Composable
fun SafeSpendingCard(limit: SafeDailyLimit, symbol: String, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (limit.isExceeded) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Today, null)
            Spacer(Modifier.width(12.dp))
            Text(SafeSpending.summary(limit, symbol), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
