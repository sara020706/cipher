package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ChatEntity
import com.example.ui.theme.CipherGreen
import com.example.viewmodel.CipherViewModel
import com.example.viewmodel.DecryptedMessage
import org.json.JSONArray

private val QUICK_REACTIONS = listOf("❤️", "👍", "😂", "😮", "😢")

// Long messages collapse with a "Read more" toggle, like WhatsApp
private const val READ_MORE_THRESHOLD = 1200

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: CipherViewModel,
    onBack: () -> Unit
) {
    val chat by viewModel.activeChat.collectAsState()
    val peer by viewModel.activeChatPeer.collectAsState()
    val messages by viewModel.activeMessages.collectAsState()
    val replyTo by viewModel.replyToMessage.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isSendingMedia by viewModel.isSendingMedia.collectAsState()

    var editingMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var inspectingMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var viewingImage by remember { mutableStateOf<String?>(null) }

    val composerState = rememberTextFieldState()

    // reverseLayout anchors the newest messages to the bottom, so opening the
    // keyboard keeps the latest conversation visible instead of pushing it away
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.sendImageMessage(uri, composerState.text.toString())
            composerState.clearText()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    val c = chat
                    if (c != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(name = c.name, avatarUrl = c.avatarUrl, size = 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    c.name,
                                    fontSize = 17.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = chatSubtitle(c, peer?.onlineStatus, peer?.lastSeen),
                                    fontSize = 12.sp,
                                    color = if (peer?.onlineStatus == "ONLINE") CipherGreen
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    val peerUser = peer
                    if (peerUser != null) {
                        IconButton(onClick = { viewModel.blockUser(peerUser.id) }) {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = "Block user",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).imePadding().fillMaxSize()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                items(messages.asReversed(), key = { it.entity.id }) { dm ->
                    MessageBubble(
                        dm = dm,
                        isMine = dm.entity.senderId == currentUser?.id,
                        isGroup = chat?.isGroup == true,
                        fontSize = settings.fontSize,
                        viewModel = viewModel,
                        onReply = { viewModel.setReplyTo(dm.entity) },
                        onEdit = { editingMessage = dm },
                        onInspect = { inspectingMessage = dm },
                        onViewImage = { viewingImage = it }
                    )
                }
            }

            // Reply banner
            val reply = replyTo
            if (reply != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Replying to a message",
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.setReplyTo(null) }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Composer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                IconButton(
                    onClick = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !isSendingMedia
                ) {
                    if (isSendingMedia) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = "Attach photo",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    BasicTextField(
                        state = composerState,
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                        modifier = Modifier
                            .fillMaxWidth()
                            // Accept GIFs/stickers/images inserted from the keyboard (Gboard etc.)
                            .contentReceiver { transferableContent ->
                                if (transferableContent.hasMediaType(MediaType.Image)) {
                                    transferableContent.consume { item ->
                                        val uri = item.uri
                                        if (uri != null) {
                                            viewModel.sendImageMessage(uri)
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                } else {
                                    transferableContent
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        decorator = { innerTextField ->
                            Box {
                                if (composerState.text.isEmpty()) {
                                    Text(
                                        "Message",
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        viewModel.sendChatMessage(composerState.text.toString())
                        composerState.clearText()
                    },
                    enabled = composerState.text.isNotBlank(),
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    // Edit dialog
    val editing = editingMessage
    if (editing != null) {
        var editText by remember(editing.entity.id) { mutableStateOf(editing.decryptedText) }
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit message") },
            text = {
                OutlinedTextField(value = editText, onValueChange = { editText = it }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = {
                    if (editText.isNotBlank()) viewModel.editMessage(editing.entity.id, editText.trim())
                    editingMessage = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("Cancel") }
            }
        )
    }

    // Encrypted payload inspector
    val inspecting = inspectingMessage
    if (inspecting != null) {
        AlertDialog(
            onDismissRequest = { inspectingMessage = null },
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
            title = { Text("Encrypted payload") },
            text = {
                Column {
                    Text(
                        "This is exactly what is stored in the cloud — unreadable without a member's private key:",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = inspecting.entity.encryptedPayload.take(600) +
                            if (inspecting.entity.encryptedPayload.length > 600) "…" else "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.heightIn(max = 260.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingMessage = null }) { Text("Close") }
            }
        )
    }

    // Full-screen photo viewer
    val viewing = viewingImage
    if (viewing != null) {
        FullScreenImageViewer(base64 = viewing, onClose = { viewingImage = null })
    }
}

// Full-screen photo viewer with pinch-to-zoom and pan
@Composable
private fun FullScreenImageViewer(base64: String, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                }
        ) {
            Base64Image(
                base64 = base64,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

private fun chatSubtitle(chat: ChatEntity, peerStatus: String?, peerLastSeen: Long?): String {
    if (chat.isGroup) {
        val count = chat.members.split(",").count { it.isNotBlank() }
        return "$count members · encrypted"
    }
    return when (peerStatus) {
        "ONLINE" -> "online"
        "TYPING" -> "typing…"
        else -> if (peerLastSeen != null) formatLastSeen(peerLastSeen) else "encrypted"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    dm: DecryptedMessage,
    isMine: Boolean,
    isGroup: Boolean,
    fontSize: Int,
    viewModel: CipherViewModel,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onInspect: () -> Unit,
    onViewImage: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val msg = dm.entity

    Column(
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 16.dp
                ),
                color = if (isMine) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(onClick = {}, onLongClick = { showMenu = true })
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (dm.mediaBase64 != null) {
                        Base64Image(
                            base64 = dm.mediaBase64,
                            modifier = Modifier
                                .widthIn(max = 276.dp)
                                .heightIn(max = 320.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = { onViewImage(dm.mediaBase64) },
                                    onLongClick = { showMenu = true }
                                )
                        )
                        if (dm.decryptedText.isNotBlank()) Spacer(Modifier.height(6.dp))
                    }
                    if (dm.decryptedText.isNotBlank()) {
                        var expanded by remember(msg.id) { mutableStateOf(false) }
                        val isLong = dm.decryptedText.length > READ_MORE_THRESHOLD
                        val displayText = if (isLong && !expanded) {
                            dm.decryptedText.take(READ_MORE_THRESHOLD).trimEnd() + "…"
                        } else {
                            dm.decryptedText
                        }
                        Text(
                            text = displayText,
                            fontSize = fontSize.sp,
                            fontStyle = if (msg.isDeletedForEveryone) FontStyle.Italic else FontStyle.Normal,
                            color = if (isMine) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (isLong && !expanded) {
                            Text(
                                text = "Read more",
                                fontSize = fontSize.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isMine) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .combinedClickable(onClick = { expanded = true })
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    // No fillMaxWidth here — the bubble must hug its content
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        if (msg.isEdited) {
                            Text(
                                "edited · ",
                                fontSize = 10.sp,
                                color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatTimestamp(msg.timestamp),
                            fontSize = 10.sp,
                            color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isMine && !msg.isDeletedForEveryone) {
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                imageVector = if (msg.status == "SENT") Icons.Default.Done else Icons.Default.DoneAll,
                                contentDescription = msg.status,
                                tint = when (msg.status) {
                                    "READ" -> CipherGreen
                                    else -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                },
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (msg.isStarred) {
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Starred",
                                tint = com.example.ui.theme.CipherOrange,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                // Quick reactions row
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    QUICK_REACTIONS.forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .combinedClickable(onClick = {
                                    viewModel.addReactionToMessage(msg.id, emoji)
                                    showMenu = false
                                })
                        )
                    }
                }
                DropdownMenuItem(
                    text = { Text("Reply") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                    onClick = { onReply(); showMenu = false }
                )
                if (isMine && !msg.isDeletedForEveryone && dm.mediaBase64 == null) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { onEdit(); showMenu = false }
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (msg.isStarred) "Unstar" else "Star") },
                    leadingIcon = {
                        Icon(
                            if (msg.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null
                        )
                    },
                    onClick = { viewModel.starMessage(msg.id, !msg.isStarred); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("View encrypted payload") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    onClick = { onInspect(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Delete for me") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { viewModel.deleteMessageForMe(msg.id); showMenu = false }
                )
                if (isMine && !msg.isDeletedForEveryone) {
                    DropdownMenuItem(
                        text = { Text("Delete for everyone") },
                        leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                        onClick = { viewModel.deleteMessageForEveryone(msg.id); showMenu = false }
                    )
                }
            }
        }

        // Reactions row under the bubble
        val reactionCounts = remember(msg.reactions) { parseReactions(msg.reactions) }
        if (reactionCounts.isNotEmpty()) {
            Row(Modifier.padding(top = 2.dp)) {
                reactionCounts.forEach { (emoji, count) ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = if (count > 1) "$emoji $count" else emoji,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun parseReactions(reactionsJson: String): List<Pair<String, Int>> {
    return try {
        val array = JSONArray(reactionsJson)
        val counts = linkedMapOf<String, Int>()
        for (i in 0 until array.length()) {
            val emoji = array.optJSONObject(i)?.optString("emoji") ?: continue
            if (emoji.isNotEmpty()) counts[emoji] = (counts[emoji] ?: 0) + 1
        }
        counts.toList()
    } catch (e: Exception) {
        emptyList()
    }
}
