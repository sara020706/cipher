package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.viewmodel.CipherViewModel

private enum class MainTab { Chats, Friends, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(viewModel: CipherViewModel, modifier: Modifier = Modifier) {
    val activeChatId by viewModel.activeChatId.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Chats) }

    // A chat is open → full-screen conversation with back navigation
    if (activeChatId != null) {
        BackHandler { viewModel.selectChat(null) }
        ChatScreen(viewModel = viewModel, onBack = { viewModel.selectChat(null) })
        return
    }

    val incomingCount = pendingRequests.count { it.receiverId == currentUser?.id }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            MainTab.Chats -> "Chats"
                            MainTab.Friends -> "Friends"
                            MainTab.Settings -> "Settings"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.Chats,
                    onClick = { selectedTab = MainTab.Chats },
                    icon = { Icon(Icons.Default.Forum, contentDescription = "Chats") },
                    label = { Text("Chats") }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Friends,
                    onClick = { selectedTab = MainTab.Friends },
                    icon = {
                        BadgedBox(badge = {
                            if (incomingCount > 0) Badge { Text("$incomingCount") }
                        }) {
                            Icon(Icons.Default.People, contentDescription = "Friends")
                        }
                    },
                    label = { Text("Friends") }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Settings,
                    onClick = { selectedTab = MainTab.Settings },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            MainTab.Chats -> androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                ChatListScreen(viewModel = viewModel, onOpenChat = { viewModel.selectChat(it) })
            }
            MainTab.Friends -> androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                FriendsScreen(viewModel = viewModel)
            }
            MainTab.Settings -> androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
