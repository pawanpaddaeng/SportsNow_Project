package com.example.cpen321application.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsPrimary
import com.example.cpen321application.ui.theme.SportsPrimaryContainer

// thin loading bar while the list updates and the error of a failed update, the current items stay visible
fun LazyListScope.loadStatus(isLoading: Boolean, errorMessage: String?, onRetry: (() -> Unit)?) {
    if (isLoading) {
        item(key = "loading") {
            LinearProgressIndicator(
                color = SportsPrimary,
                trackColor = SportsPrimaryContainer,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (errorMessage != null) {
        item(key = "error") {
            RetryMessage(message = errorMessage, onRetry = onRetry, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

// a small centered note in a list, e.g. when it's empty
@Composable
fun ListNote(text: String) {
    Text(
        text = text,
        color = SportsOnSurfaceVariant,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
