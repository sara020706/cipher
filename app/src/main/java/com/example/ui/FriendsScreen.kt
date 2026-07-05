package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.ui.theme.CipherGreen
import com.example.ui.theme.CipherRed
import com.example.viewmodel.CipherViewModel

@Composable
fun FriendsScreen(viewModel: CipherViewModel) {
    val friends by viewModel.friendsList.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()
    val blockedUsers by viewModel.blockedUsers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    var searchUsername by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResultMessage by remember { mutableStateOf<String?>(null) }

    // Requests addressed to me (incoming); ones I sent are outgoing
    val incoming = pendingRequests.filter { it.receiverId == currentUser?.id }
    val outgoing = pendingRequests.filter { it.senderId == currentUser?.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        // Add friend by username
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CipherSearchField(
                        value = searchUsername,
                        onValueChange = {
                            searchUsername = it.trim().lowercase()
                            searchResultMessage = null
                        },
                        placeholder = "Add by username",
                        leadingIcon = Icons.Default.PersonAdd,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            isSearching = true
                            searchResultMessage = null
                            viewModel.sendFriendRequest(searchUsername) { success ->
                                isSearching = false
                                searchResultMessage = if (success) {
                                    "✅ Request sent to @$searchUsername"
                                } else {
                                    "❌ No user found with that username"
                                }
                                if (success) searchUsername = ""
                            }
                        },
                        enabled = searchUsername.isNotBlank() && !isSearching
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Send friend request")
                        }
                    }
                }
                val message = searchResultMessage
                if (message != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Incoming requests
        if (incoming.isNotEmpty()) {
            item { SectionHeader("Friend requests (${incoming.size})") }
            items(incoming, key = { it.id }) { req ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Avatar(name = req.senderName, avatarUrl = req.senderAvatar, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(req.senderName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            "wants to connect securely",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.acceptFriendRequest(req.id) }) {
                        Icon(Icons.Default.Check, contentDescription = "Accept", tint = CipherGreen)
                    }
                    IconButton(onClick = { viewModel.rejectFriendRequest(req.id) }) {
                        Icon(Icons.Default.Close, contentDescription = "Reject", tint = CipherRed)
                    }
                }
            }
        }

        // Outgoing requests
        if (outgoing.isNotEmpty()) {
            item { SectionHeader("Sent requests") }
            items(outgoing, key = { it.id }) { req ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Request pending…",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { viewModel.rejectFriendRequest(req.id) }) {
                        Text("Cancel")
                    }
                }
            }
        }

        // Contacts
        item { SectionHeader("Contacts (${friends.size})") }
        if (friends.isEmpty()) {
            item {
                Text(
                    text = "No contacts yet. Search a username above to send your first request.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        } else {
            items(friends, key = { it.id }) { friend ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openChatWithFriend(friend.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Avatar(name = friend.displayName, avatarUrl = friend.avatarUrl, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                friend.displayName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (friend.onlineStatus == "ONLINE") {
                                Spacer(Modifier.width(6.dp))
                                Text("●", color = CipherGreen, fontSize = 10.sp)
                            }
                        }
                        Text(
                            "@${friend.username}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.blockUser(friend.id) }) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = "Block",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Blocked users
        if (blockedUsers.isNotEmpty()) {
            item { SectionHeader("Blocked (${blockedUsers.size})") }
            items(blockedUsers, key = { it.id }) { blocked ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Avatar(name = blocked.displayName, avatarUrl = blocked.avatarUrl, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        blocked.displayName,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { viewModel.unblockUser(blocked.id) }) {
                        Text("Unblock")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp)
    )
}
