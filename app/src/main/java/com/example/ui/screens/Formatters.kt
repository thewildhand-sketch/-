package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalLedgerColors
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

fun formatMoney(amount: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return nf.format(amount)
}

@Composable
fun BalanceStatusPill(
    debit: Double,
    credit: Double,
    modifier: Modifier = Modifier,
) {
    val ledgerColors = LocalLedgerColors.current
    val diff = debit - credit
    val balanced = abs(diff) < 0.01 && debit > 0.0
    val bg = if (balanced) ledgerColors.debitBg else ledgerColors.warningBg
    val fg = if (balanced) ledgerColors.debit else ledgerColors.warning
    val label = when {
        balanced -> "متوازن"
        debit == 0.0 && credit == 0.0 -> "قيد صفري"
        else -> "فرق ${formatMoney(abs(diff))}"
    }

    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (balanced) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
            )
        }
    }
}
