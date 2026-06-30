package com.example.ui

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.AppSettingsEntity
import com.example.data.CipherRepository
import com.example.data.FirestoreSyncManager
import com.example.data.ChatEntity
import com.example.data.CryptoManager
import com.example.data.FriendRequestEntity
import com.example.data.MessageEntity
import com.example.data.UserEntity
import com.example.viewmodel.CipherViewModel
import com.example.viewmodel.DecryptedMessage
import kotlinx.coroutines.launch
import kotlin.random.Random
import com.example.ui.theme.*

// --- AUTHENTICATION SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: CipherViewModel,
    modifier: Modifier = Modifier
) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val avatarUrls = listOf(
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
        "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150",
        "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150"
    )
    var selectedAvatar by remember { mutableStateOf(avatarUrls[0]) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .testTag("auth_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Logo
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Logo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isSignUp) "Create Secure Account" else "Welcome to CipherChat",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isSignUp) "Generate your local cryptographic keys" else "End-to-End Encrypted Messaging",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // Input Fields
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("email_input"),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Rounded.Password, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (isSignUp) ImeAction.Next else ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input"),
                    shape = RoundedCornerShape(14.dp)
                )

                if (isSignUp) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Rounded.AlternateEmail, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("Bio") },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Choose Profile Picture",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        avatarUrls.forEach { url ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (selectedAvatar == url) 3.dp else 0.dp,
                                        color = if (selectedAvatar == url) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedAvatar = url }
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "Avatar Options",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it }
                    )
                    Text(
                        text = "Remember Me",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (isSignUp) {
                            viewModel.signUp(username, displayName, bio, selectedAvatar)
                        } else {
                            viewModel.login(email)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = if (isSignUp) "Generate Keys & Sign Up" else "Secure Login",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Google Sign In Mock
                OutlinedButton(
                    onClick = {
                        viewModel.signUp("google_user", "Google User", "Signed in with Google", "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = "Google Logo",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Continue with Google")
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = { isSignUp = !isSignUp }
                ) {
                    Text(
                        text = if (isSignUp) "Already have an account? Sign In" else "New to CipherChat? Create Account",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}


// --- MAIN DASHBOARD SCREEN ---
@Composable
fun MainDashboard(
    viewModel: CipherViewModel,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf("chats") } // "chats", "friends", "settings", "archived"
    val activeChatId by viewModel.activeChatId.collectAsState()
    var showCreateGroup by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        if (isWideScreen) {
            // Tablet / Desktop 3-Panel Layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Panel 1: Left Sidebar
                LeftSidebar(
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it },
                    currentUser = viewModel.currentUser.collectAsState().value,
                    onLogout = { viewModel.logout() },
                    modifier = Modifier
                        .width(80.dp)
                        .fillMaxHeight()
                )

                // Panel 2: Conversation List (Center/Middle Sidebar)
                ConversationListPane(
                    viewModel = viewModel,
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it },
                    onChatSelected = { viewModel.selectChat(it) },
                    onCreateGroupClick = { showCreateGroup = true },
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(0.dp)
                        )
                )

                // Panel 3 & 4: Chat Screen + Info Panel
                Row(modifier = Modifier.weight(1f)) {
                    if (activeChatId != null) {
                        ChatPane(
                            viewModel = viewModel,
                            modifier = Modifier
                                .weight(1.5f)
                                .fillMaxHeight()
                        )

                        // Panel 5: Right Info Panel
                        RightInfoPane(
                            viewModel = viewModel,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(0.dp)
                                )
                        )
                    } else {
                        // Empty State
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.size(96.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Select a Chat to Start Crypting",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "End-to-End Encrypted channels verified locally.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Mobile Layout (Single screen switching or navigation backstack)
            if (activeChatId != null) {
                // Full screen chat
                ChatPane(
                    viewModel = viewModel,
                    onBackClick = { viewModel.selectChat(null) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Mobile List View with bottom bar
                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xFF16191E), // #16191E matching design
                            modifier = Modifier.drawBehind {
                                drawLine(
                                    color = Color(0xFF2D323B), // #2D323B border
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 1.dp.toPx()
                                )
                            },
                            tonalElevation = 0.dp
                        ) {
                            NavigationBarItem(
                                selected = activeTab == "chats",
                                onClick = { activeTab = "chats" },
                                icon = { Icon(Icons.Rounded.Chat, contentDescription = "Chats") },
                                label = { Text("Chats", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CipherPrimary,
                                    selectedTextColor = CipherPrimary,
                                    unselectedIconColor = Color(0xFF64748B),
                                    unselectedTextColor = Color(0xFF64748B),
                                    indicatorColor = Color.Transparent
                                )
                            )
                            NavigationBarItem(
                                selected = activeTab == "friends",
                                onClick = { activeTab = "friends" },
                                icon = { Icon(Icons.Rounded.People, contentDescription = "Friends") },
                                label = { Text("Groups", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) }, // Groups matching design label
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CipherPrimary,
                                    selectedTextColor = CipherPrimary,
                                    unselectedIconColor = Color(0xFF64748B),
                                    unselectedTextColor = Color(0xFF64748B),
                                    indicatorColor = Color.Transparent
                                )
                            )
                            NavigationBarItem(
                                selected = activeTab == "archived",
                                onClick = { activeTab = "archived" },
                                icon = { Icon(Icons.Rounded.Archive, contentDescription = "Archived") },
                                label = { Text("People", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) }, // People matching design label
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CipherPrimary,
                                    selectedTextColor = CipherPrimary,
                                    unselectedIconColor = Color(0xFF64748B),
                                    unselectedTextColor = Color(0xFF64748B),
                                    indicatorColor = Color.Transparent
                                )
                            )
                            NavigationBarItem(
                                selected = activeTab == "settings",
                                onClick = { activeTab = "settings" },
                                icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                                label = { Text("Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CipherPrimary,
                                    selectedTextColor = CipherPrimary,
                                    unselectedIconColor = Color(0xFF64748B),
                                    unselectedTextColor = Color(0xFF64748B),
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    ConversationListPane(
                        viewModel = viewModel,
                        activeTab = activeTab,
                        onTabSelected = { activeTab = it },
                        onChatSelected = { viewModel.selectChat(it) },
                        onCreateGroupClick = { showCreateGroup = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    if (showCreateGroup) {
        CreateGroupDialog(
            viewModel = viewModel,
            onDismiss = { showCreateGroup = false }
        )
    }
}


// --- PANEL 1: LEFT COMPACT BAR ---
@Composable
fun LeftSidebar(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    currentUser: UserEntity?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NavigationRailItem(
                    selected = activeTab == "chats",
                    onClick = { onTabSelected("chats") },
                    icon = { Icon(Icons.Rounded.Chat, contentDescription = "Chats") }
                )
                NavigationRailItem(
                    selected = activeTab == "friends",
                    onClick = { onTabSelected("friends") },
                    icon = { Icon(Icons.Rounded.People, contentDescription = "Friends") }
                )
                NavigationRailItem(
                    selected = activeTab == "archived",
                    onClick = { onTabSelected("archived") },
                    icon = { Icon(Icons.Rounded.Archive, contentDescription = "Archived") }
                )
                NavigationRailItem(
                    selected = activeTab == "settings",
                    onClick = { onTabSelected("settings") },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
                )
            }

            // User Profile Avatar and Logout button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                if (currentUser != null) {
                    AsyncImage(
                        model = currentUser.avatarUrl,
                        contentDescription = "My Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                IconButton(onClick = onLogout) {
                    Icon(
                        imageVector = Icons.Rounded.Logout,
                        contentDescription = "Logout",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}


// --- PANEL 2: CONVERSATION / LIST PANEL ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListPane(
    viewModel: CipherViewModel,
    activeTab: String,
    onTabSelected: (String) -> Unit,
    onChatSelected: (String) -> Unit,
    onCreateGroupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeChats by viewModel.filteredChats.collectAsState()
    val archivedChats by viewModel.allArchivedChats.collectAsState()
    val friends by viewModel.friendsList.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // App Name and Logo Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = activeTab.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (activeTab == "chats") {
                IconButton(onClick = onCreateGroupClick) {
                    Icon(
                        imageVector = Icons.Rounded.GroupAdd,
                        contentDescription = "Create Group",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search chats, messages...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
                .testTag("search_bar"),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )

        Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                "chats" -> {
                    if (activeChats.isEmpty()) {
                        EmptyPanePlaceholder(
                            icon = Icons.Rounded.ChatBubbleOutline,
                            title = "No active conversations",
                            subtitle = "Click Friends to start chatting!"
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(activeChats) { chat ->
                                ChatListItem(
                                    chat = chat,
                                    onChatSelected = { onChatSelected(chat.id) }
                                )
                            }
                        }
                    }
                }
                "friends" -> {
                    FriendsPaneContent(
                        friends = friends,
                        requests = pendingRequests,
                        viewModel = viewModel
                    )
                }
                "archived" -> {
                    if (archivedChats.isEmpty()) {
                        EmptyPanePlaceholder(
                            icon = Icons.Rounded.Archive,
                            title = "No archived conversations",
                            subtitle = "Swipe or pin chats to organize your tray."
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(archivedChats) { chat ->
                                ChatListItem(
                                    chat = chat,
                                    onChatSelected = { onChatSelected(chat.id) }
                                )
                            }
                        }
                    }
                }
                "settings" -> {
                    SettingsPaneContent(viewModel = viewModel)
                }
            }
        }
    }
}


@Composable
fun ChatListItem(
    chat: ChatEntity,
    onChatSelected: () -> Unit
) {
    val isHighlighted = chat.pinned || chat.unreadCount > 0
    val backgroundColor = if (isHighlighted) {
        MaterialTheme.colorScheme.surface // #1C1F26
    } else {
        Color.Transparent
    }
    val borderStroke = if (isHighlighted) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant) // #2D323B
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onChatSelected),
        shape = RoundedCornerShape(24.dp), // rounded-3xl
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = borderStroke,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(modifier = Modifier.size(56.dp)) {
                AsyncImage(
                    model = chat.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )

                // Status Indicator (Offline-safe mock)
                if (!chat.isGroup) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(CipherGreen)
                            .align(Alignment.BottomEnd)
                            .border(2.dp, if (isHighlighted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Timestamp
                    val timeString = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                        .format(java.util.Date(chat.lastMessageTime))
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (chat.unreadCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isEncryptedText = chat.lastMessageText?.startsWith("🔒") == true || chat.lastMessageText != null
                    Text(
                        text = chat.lastMessageText ?: "No messages yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (chat.unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontStyle = if (isEncryptedText) FontStyle.Italic else FontStyle.Normal,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (chat.pinned) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(14.dp)
                                    .padding(end = 4.dp)
                            )
                        }

                        if (chat.muted) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeOff,
                                contentDescription = "Muted",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(14.dp)
                                    .padding(end = 4.dp)
                            )
                        }

                        if (chat.unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chat.unreadCount.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// --- CHAT CONVERSATION VIEW (CENTER) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPane(
    viewModel: CipherViewModel,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null
) {
    val activeChat by viewModel.activeChat.collectAsState()
    val messages by viewModel.activeMessages.collectAsState()
    val messageInput by viewModel.messageInput.collectAsState()
    val replyToMessage by viewModel.replyToMessage.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var showEmojiPicker by remember { mutableStateOf(false) }
    var selectedMessageForInspection by remember { mutableStateOf<MessageEntity?>(null) }
    var expandedMenuMessageId by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (activeChat == null) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (settings.chatWallpaper != null) {
                    // Render simple mock pattern backgrounds or solid
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.background
                }
            )
            .drawBehind {
                // If a wallpaper setting is configured, render elegant overlay
                if (settings.chatWallpaper == "stars") {
                    drawRect(color = Color(0xFF020617))
                    // Simple simulated stars
                    val random = Random(42)
                    for (i in 0..60) {
                        drawCircle(
                            color = Color.White.copy(alpha = random.nextFloat() * 0.4f + 0.1f),
                            radius = random.nextFloat() * 2f + 1f,
                            center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
                        )
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = activeChat!!.avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = activeChat!!.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            // Subtitle
                            Text(
                                text = "Perfect Forward Secrecy Enabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = CipherGreen,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { /* Call feature mock */ }) {
                        Icon(Icons.Rounded.Call, contentDescription = "Voice Call")
                    }
                    IconButton(onClick = { /* Video Call feature mock */ }) {
                        Icon(Icons.Rounded.Videocam, contentDescription = "Video Call")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            )

            // Secure Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    .padding(vertical = 6.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "End-to-End Encrypted. Tap bubbles to inspect SQLite database cypher payload.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Message History Panel
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
                ) {
                    items(messages) { decrypted ->
                        val isMe = decrypted.entity.senderId == "ME"

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                                // Chat bubble card
                                Card(
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isMe) 16.dp else 4.dp,
                                        bottomEnd = if (isMe) 4.dp else 16.dp
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier
                                        .widthIn(max = 280.dp)
                                        .clickable { selectedMessageForInspection = decrypted.entity }
                                        .testTag("message_bubble")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        // Sender name for Group chats
                                        if (activeChat!!.isGroup && !isMe) {
                                            Text(
                                                text = if (decrypted.entity.senderId == "BOT_ALPHA") "Alpha Secure" else "Omega Crypt",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        }

                                        // Reply layout
                                        if (decrypted.entity.replyToId != null) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                    .padding(8.dp)
                                                    .padding(start = 4.dp)
                                            ) {
                                                Text(
                                                    text = "Replying to another secure frame...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }

                                        // Media Attachment Renderer
                                        if (decrypted.entity.mediaUri != null) {
                                            if (decrypted.entity.mediaType == "IMAGE") {
                                                AsyncImage(
                                                    model = decrypted.entity.mediaUri,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(150.dp)
                                                        .clip(RoundedCornerShape(8.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                            } else if (decrypted.entity.mediaType == "VOICE") {
                                                VoicePlayerMock()
                                                Spacer(modifier = Modifier.height(6.dp))
                                            }
                                        }

                                        // Text
                                        Text(
                                            text = decrypted.decryptedText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = settings.fontSize.sp,
                                            color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))

                                        // Time & Status Row
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End,
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                                            Text(
                                                text = formatter.format(java.util.Date(decrypted.entity.timestamp)),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 9.sp,
                                                color = (if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f)
                                            )

                                            if (isMe) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = if (decrypted.entity.status == "READ") Icons.Rounded.DoneAll else Icons.Rounded.Check,
                                                    contentDescription = decrypted.entity.status,
                                                    tint = if (decrypted.entity.status == "READ") CipherAccent else Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Message Reaction display badge
                                if (decrypted.entity.reactions != "[]") {
                                    Box(
                                        modifier = Modifier
                                            .offset(y = (-8).dp)
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = "❤️", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Reply composition panel
            if (replyToMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Replying to encrypted frame",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "SECURE MESSAGE CONTAINER",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { viewModel.setReplyTo(null) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel reply")
                    }
                }
            }

            // Message Input bar
            BottomComposer(
                text = messageInput,
                onTextChange = { viewModel.setMessageInput(it) },
                onSend = { viewModel.sendChatMessage(messageInput) },
                onAttachClick = {
                    // Simulate random image attachment
                    viewModel.sendChatMessage(
                        text = "Sent a secure media photo.",
                        mediaUri = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=300",
                        mediaType = "IMAGE"
                    )
                },
                onMicClick = {
                    // Simulate voice message attachment
                    viewModel.sendChatMessage(
                        text = "Voice message",
                        mediaUri = "voice_file_temp",
                        mediaType = "VOICE"
                    )
                }
            )
        }
    }

    // Modal: Inspect Ciphertext
    if (selectedMessageForInspection != null) {
        InspectMessageDialog(
            message = selectedMessageForInspection!!,
            viewModel = viewModel,
            onDismiss = { selectedMessageForInspection = null }
        )
    }
}


@Composable
fun VoicePlayerMock() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        // Simulated sound waves
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
        ) {
            val steps = 20
            val width = size.width
            val stepWidth = width / steps
            for (i in 0 until steps) {
                val waveHeight = (Math.sin(i.toDouble() * 0.5) * 8 + 12).toFloat()
                drawLine(
                    color = Color.White,
                    start = Offset(i * stepWidth, size.height / 2 - waveHeight / 2),
                    end = Offset(i * stepWidth, size.height / 2 + waveHeight / 2),
                    strokeWidth = 3f
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text("0:14", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit,
    onMicClick: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttachClick) {
                Icon(Icons.Rounded.AttachFile, contentDescription = "Attach File", tint = MaterialTheme.colorScheme.primary)
            }

            // Input TextField
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Write encrypted message...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("message_input_field"),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )

            Spacer(modifier = Modifier.width(4.dp))

            if (text.trim().isNotEmpty()) {
                IconButton(
                    onClick = onSend,
                    modifier = Modifier.testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                IconButton(onClick = onMicClick) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Voice Note", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}


// --- INSPECT PAYLOAD DIALOG ---
@Composable
fun InspectMessageDialog(
    message: MessageEntity,
    viewModel: CipherViewModel,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var decryptedText by remember { mutableStateOf("Decrypting...") }
    val me = viewModel.currentUser.collectAsState().value

    LaunchedEffect(message) {
        // Run simulated or actual decryption view
        val repository = CipherRepository(viewModel.getApplication())
        decryptedText = repository.decryptMessage(message)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "E2E Message Metadata",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Field 1: Message ID
                Text("MESSAGE ID", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(message.id, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontSize = 11.sp)

                Spacer(modifier = Modifier.height(12.dp))

                // Field 2: SQLite Ciphertext
                Text("SQLITE DATABASE CIPHERTEXT PAYLOAD", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = message.encryptedPayload.ifEmpty { "DELETED_OR_EMPTY" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = CipherAccent,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Field 3: RSA key verification
                Text("RECIPIENT PUBLIC KEY FINGERPRINT", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                val fingerprint = if (me != null) CryptoManager.getFingerprint(me.publicKey).take(30) + "..." else "GENERATING..."
                Text(
                    text = fingerprint,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Field 4: Decrypted plain text
                Text("DECRYPTED CLEAR TEXT (ON-DEVICE)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = CipherGreen)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CipherGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = decryptedText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = CipherGreen
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        viewModel.addReactionToMessage(message.id, "❤️")
                        onDismiss()
                    }) {
                        Text("React ❤️")
                    }
                    TextButton(onClick = {
                        viewModel.starMessage(message.id, !message.isStarred)
                        onDismiss()
                    }) {
                        Text(if (message.isStarred) "Unstar" else "Star Message")
                    }
                    TextButton(onClick = {
                        viewModel.deleteMessageForEveryone(message.id)
                        onDismiss()
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}


// --- PANEL 3: RIGHT PANEL (PROFILE & METADATA) ---
@Composable
fun RightInfoPane(
    viewModel: CipherViewModel,
    modifier: Modifier = Modifier
) {
    val activeChat by viewModel.activeChat.collectAsState()
    val me by viewModel.currentUser.collectAsState()

    if (activeChat == null) return

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        AsyncImage(
            model = activeChat!!.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = activeChat!!.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = if (activeChat!!.isGroup) "Group Conversation" else "@secured_contact",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Fingerprint QR Section
        Text(
            text = "E2EE Fingerprint Key",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Visual QR code using dynamic Canvas drawing
        val keyFingerprint = if (me != null) CryptoManager.getFingerprint(me!!.publicKey) else "KEYS"
        Canvas(
            modifier = Modifier
                .size(140.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            val random = Random(keyFingerprint.hashCode())
            val rows = 6
            val cols = 6
            val cellWidth = size.width / cols
            val cellHeight = size.height / rows

            // Draw Pseudo QR blocks
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    // Force corner anchors to mimic standard QR
                    val isAnchor = (r < 2 && c < 2) || (r >= rows - 2 && c < 2) || (r < 2 && c >= cols - 2)
                    if (isAnchor || random.nextBoolean()) {
                        drawRect(
                            color = Color(0xFF0F172A),
                            topLeft = Offset(c * cellWidth, r * cellHeight),
                            size = Size(cellWidth, cellHeight)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = keyFingerprint.take(32) + "\n" + keyFingerprint.drop(32).take(32),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Links and files section
        Divider(color = MaterialTheme.colorScheme.surfaceVariant)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Shared Media",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Media items grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AsyncImage(
                model = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=100",
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            AsyncImage(
                model = "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=100",
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("+12", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}


// --- FRIENDS PANE CONTENT ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsPaneContent(
    friends: List<UserEntity>,
    requests: List<FriendRequestEntity>,
    viewModel: CipherViewModel
) {
    var searchFriendName by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Friend Request Search Field
        Text(
            text = "Add Friend",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchFriendName,
                onValueChange = { searchFriendName = it },
                placeholder = { Text("Search by @username...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    viewModel.sendFriendRequest(searchFriendName) { success ->
                        scope.launch {
                            if (success) {
                                searchFriendName = ""
                                snackbarHostState.showSnackbar("Friend Request Sent!")
                            } else {
                                snackbarHostState.showSnackbar("User not found!")
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Invite")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Pending Requests
        if (requests.isNotEmpty()) {
            Text(
                text = "Pending Invites (${requests.size})",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = CipherOrange
            )

            Spacer(modifier = Modifier.height(8.dp))

            requests.forEach { req ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = req.senderAvatar,
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(req.senderName, style = MaterialTheme.typography.bodyMedium)
                    }

                    Row {
                        IconButton(onClick = { viewModel.acceptFriendRequest(req.id) }) {
                            Icon(Icons.Rounded.Check, contentDescription = "Accept", tint = CipherGreen)
                        }
                        IconButton(onClick = { viewModel.rejectFriendRequest(req.id) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Reject", tint = CipherRed)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Friends List
        Text(
            text = "My Encrypted Circle",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (friends.isEmpty()) {
            Text(
                text = "Your secure network is empty. Invite friends using @username above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn {
                items(friends) { friend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectChat(friend.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = friend.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(friend.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("@${friend.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        IconButton(onClick = { viewModel.blockUser(friend.id) }) {
                            Icon(Icons.Rounded.Block, contentDescription = "Block User", tint = CipherRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}


// --- SETTINGS PANE ---
@Composable
fun SettingsPaneContent(viewModel: CipherViewModel) {
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "App Identity & Styling",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Theme selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dark Mode Theme", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = settings.themeMode == "DARK",
                onCheckedChange = { viewModel.updateThemeMode(if (it) "DARK" else "LIGHT") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Wallpaper selector
        Text("Chat Wallpaper", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp, 40.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                    .border(
                        width = if (settings.chatWallpaper == null) 2.dp else 0.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { viewModel.updateChatWallpaper(null) },
                contentAlignment = Alignment.Center
            ) {
                Text("Default", color = Color.White, fontSize = 11.sp)
            }

            Box(
                modifier = Modifier
                    .size(70.dp, 40.dp)
                    .background(Color(0xFF020617), RoundedCornerShape(8.dp))
                    .border(
                        width = if (settings.chatWallpaper == "stars") 2.dp else 0.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { viewModel.updateChatWallpaper("stars") },
                contentAlignment = Alignment.Center
            ) {
                Text("Cosmic", color = CipherAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Font Size selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Text Font Size", style = MaterialTheme.typography.bodyMedium)
            Row {
                listOf(12, 14, 16, 18).forEach { size ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (settings.fontSize == size) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { viewModel.updateFontSize(size) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            size.toString(),
                            color = if (settings.fontSize == size) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Privacy & Notifications",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Push Notifications", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = settings.notificationsEnabled,
                onCheckedChange = { viewModel.updateNotificationsEnabled(it) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Block Unknown Senders", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = settings.blockUnknownSenders,
                onCheckedChange = { viewModel.updateBlockUnknownSenders(it) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Security & Infrastructure Status",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Item 1: On-Device Keys Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EnhancedEncryption,
                        contentDescription = "On-Device Keys Status",
                        tint = CipherGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Key Protection",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Android KeyStore (Hardware AES-GCM)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Item 2: Firestore Sync Status
                val context = LocalContext.current
                val isFirebaseAvailable = remember { FirestoreSyncManager.isFirebaseAvailable(context) }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isFirebaseAvailable) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                        contentDescription = "Firestore Sync Status",
                        tint = if (isFirebaseAvailable) CipherGreen else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Firebase Firestore Sync",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isFirebaseAvailable) {
                                "Connected & Encrypted Remote Sync"
                            } else {
                                "Offline secure fallback (Place google-services.json to sync)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isFirebaseAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}


// --- MOCK DIALOGS ---
@Composable
fun CreateGroupDialog(
    viewModel: CipherViewModel,
    onDismiss: () -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    val friends by viewModel.friendsList.collectAsState()
    val selectedMembers = remember { mutableStateListOf<String>() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "New Secure Group",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = groupDescription,
                    onValueChange = { groupDescription = it },
                    label = { Text("Topic/Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Select Members", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(modifier = Modifier.height(120.dp)) {
                    items(friends) { friend ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedMembers.contains(friend.id)) {
                                        selectedMembers.remove(friend.id)
                                    } else {
                                        selectedMembers.add(friend.id)
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(friend.displayName)
                            Checkbox(
                                checked = selectedMembers.contains(friend.id),
                                onCheckedChange = {
                                    if (it == true) selectedMembers.add(friend.id) else selectedMembers.remove(friend.id)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.createGroup(groupName, groupDescription, selectedMembers) {
                                onDismiss()
                            }
                        },
                        enabled = groupName.trim().isNotEmpty()
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}


@Composable
fun EmptyPanePlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
