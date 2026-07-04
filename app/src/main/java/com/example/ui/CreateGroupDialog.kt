package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.CipherViewModel

@Composable
fun CreateGroupDialog(
    viewModel: CipherViewModel,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    val friends by viewModel.friendsList.collectAsState()
    var groupName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New group") },
        text = {
            Column {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.padding(4.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.padding(6.dp))
                Text(
                    "Members",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (friends.isEmpty()) {
                    Text(
                        "Add friends first to create a group.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(friends, key = { it.id }) { friend ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectedIds.contains(friend.id)) selectedIds.remove(friend.id)
                                        else selectedIds.add(friend.id)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = selectedIds.contains(friend.id),
                                    onCheckedChange = {
                                        if (it) selectedIds.add(friend.id) else selectedIds.remove(friend.id)
                                    }
                                )
                                Avatar(name = friend.displayName, avatarUrl = friend.avatarUrl, size = 34.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(friend.displayName, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.createGroup(groupName.trim(), description.trim(), selectedIds.toList()) { groupId ->
                        onCreated(groupId)
                    }
                },
                enabled = groupName.isNotBlank() && selectedIds.isNotEmpty()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
