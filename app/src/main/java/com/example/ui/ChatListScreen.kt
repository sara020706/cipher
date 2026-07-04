package com.example.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatEntity
import com.example.viewmodel.CipherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: CipherViewModel,
    onOpenChat: (String) -> Unit
) {
    val chats by viewModel.filteredChats.collectAsState()
    val archivedChats by viewModel.allArchivedChats.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var showArchived by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showGroupDialog = true }) {
                Icon(Icons.Default.GroupAdd, contentDescription = "New group")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            CipherSearchField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = "Search chats",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (archivedChats.isNotEmpty()) {
                TextButton(
                    onClick = { showArchived = !showArchived },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Archived (${archivedChats.size})")
                }
            }

            val listToShow = if (showArchived) archivedChats else chats

            if (listToShow.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (showArchived) "No archived chats"
                        else "No chats yet.\nGo to Friends and add someone by username to start a secure conversation.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(listToShow, key = { it.id }) { chat ->
                        ChatListItem(
                            chat = chat,
                            onClick = { onOpenChat(chat.id) },
                            onPin = { viewModel.setChatPinned(chat.id, !chat.pinned) },
                            onArchive = { viewModel.setChatArchived(chat.id, !chat.archived) },
                            onMute = { viewModel.setChatMuted(chat.id, !chat.muted) }
                        )
                    }
                }
            }
        }
    }

    if (showGroupDialog) {
        CreateGroupDialog(
            viewModel = viewModel,
            onDismiss = { showGroupDialog = false },
            onCreated = { groupId ->
                showGroupDialog = false
                onOpenChat(groupId)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListItem(
    chat: ChatEntity,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onMute: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Avatar(name = chat.name, avatarUrl = chat.avatarUrl, size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (chat.pinned) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (chat.muted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Muted",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = chat.lastMessageText ?: "",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatTimestamp(chat.lastMessageTime),
                    fontSize = 12.sp,
                    color = if (chat.unreadCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (chat.unreadCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text("${chat.unreadCount}", modifier = Modifier.defaultMinSize(minWidth = 14.dp))
                    }
                }
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(if (chat.pinned) "Unpin" else "Pin") },
                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                onClick = { onPin(); showMenu = false }
            )
            DropdownMenuItem(
                text = { Text(if (chat.muted) "Unmute" else "Mute") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null) },
                onClick = { onMute(); showMenu = false }
            )
            DropdownMenuItem(
                text = { Text(if (chat.archived) "Unarchive" else "Archive") },
                leadingIcon = {
                    Icon(
                        if (chat.archived) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = null
                    )
                },
                onClick = { onArchive(); showMenu = false }
            )
        }
    }
}
