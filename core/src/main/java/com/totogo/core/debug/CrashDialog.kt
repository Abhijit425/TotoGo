package com.totogo.core.debug

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shown once after a crash so the report can be shared with the developer. */
@Composable
fun CrashReportDialog() {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(CrashReporter.lastCrash(ctx)) }
    val t = text ?: return
    AlertDialog(
        onDismissRequest = { CrashReporter.clear(ctx); text = null },
        title = { Text("The app closed unexpectedly last time") },
        text = { Text(t.take(4000), fontSize = 11.sp, modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton({ CrashReporter.share(ctx, t); CrashReporter.clear(ctx); text = null }) { Text("Share report") } },
        dismissButton = { TextButton({ CrashReporter.clear(ctx); text = null }) { Text("Dismiss") } },
    )
}
