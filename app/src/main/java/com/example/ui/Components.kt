package com.example.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.example.data.MediaUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Circular avatar: gallery photo ("b64:" data) or remote URL, otherwise colored initials
@Composable
fun Avatar(name: String, avatarUrl: String, size: Dp = 44.dp) {
    val model: Any? = remember(avatarUrl) {
        when {
            avatarUrl.startsWith("b64:") -> MediaUtils.base64ToBytes(avatarUrl.removePrefix("b64:"))
            avatarUrl.isNotBlank() -> avatarUrl
            else -> null
        }
    }
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        val initials = name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = initials,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value / 2.6f).sp
            )
        }
    }
}

// Renders decrypted Base64 media through Coil, with GIF animation support
@Composable
fun Base64Image(
    base64: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
    val bytes = remember(base64) { MediaUtils.base64ToBytes(base64) }
    if (bytes != null) {
        AsyncImage(
            model = bytes,
            imageLoader = imageLoader,
            contentDescription = "Photo",
            contentScale = contentScale,
            modifier = modifier
        )
    }
}

// The one search/input pill style used across all screens
@Composable
fun CipherSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Search
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(leadingIcon, contentDescription = null) },
        singleLine = true,
        shape = CircleShape,
        modifier = modifier
    )
}

// "14:03" for today, "Yesterday", weekday for this week, date otherwise
fun formatTimestamp(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }

    return when {
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) == 1 ->
            "Yesterday"

        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) < 7 ->
            SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))

        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
    }
}

fun formatLastSeen(lastSeen: Long): String {
    val diff = System.currentTimeMillis() - lastSeen
    return when {
        diff < 60_000 -> "last seen just now"
        diff < 3_600_000 -> "last seen ${diff / 60_000}m ago"
        diff < 86_400_000 -> "last seen ${diff / 3_600_000}h ago"
        else -> "last seen ${formatTimestamp(lastSeen)}"
    }
}
