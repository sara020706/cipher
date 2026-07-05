package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppSettingsEntity
import com.example.data.ChatEntity
import com.example.data.CipherRepository
import com.example.data.FriendRequestEntity
import com.example.data.MediaUtils
import com.example.data.MessageEntity
import com.example.data.UserEntity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DecryptedMessage(
    val entity: MessageEntity,
    val decryptedText: String,
    val mediaBase64: String? = null
)

class CipherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CipherRepository(application)

    // Theme is mirrored to SharedPreferences so the very first frame renders
    // with the right theme (Room loads too late and causes a dark→light flash)
    private val prefs = application.getSharedPreferences("cipher_prefs", Context.MODE_PRIVATE)
    private val startupTheme: String = prefs.getString("theme", "DARK") ?: "DARK"

    // Current session status
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isDbInitialized = MutableStateFlow(false)
    val isDbInitialized: StateFlow<Boolean> = _isDbInitialized.asStateFlow()

    // True while restoring the previous session at startup — the UI shows a
    // splash instead of flashing the auth screen
    private val _isSessionLoading = MutableStateFlow(true)
    val isSessionLoading: StateFlow<Boolean> = _isSessionLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    // Screen selection / Chat state
    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    private val _replyToMessage = MutableStateFlow<MessageEntity?>(null)
    val replyToMessage: StateFlow<MessageEntity?> = _replyToMessage.asStateFlow()

    private val _isSendingMedia = MutableStateFlow(false)
    val isSendingMedia: StateFlow<Boolean> = _isSendingMedia.asStateFlow()

    // Observe local User Profile
    val currentUser: StateFlow<UserEntity?> = repository.getMeFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Observe App Settings
    val settings: StateFlow<AppSettingsEntity> = repository.getSettingsFlow().map {
        it ?: AppSettingsEntity(themeMode = startupTheme)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettingsEntity(themeMode = startupTheme)
    )

    // Chats & Friends Flow
    val allActiveChats: StateFlow<List<ChatEntity>> = repository.getActiveChatsFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allArchivedChats: StateFlow<List<ChatEntity>> = repository.getArchivedChatsFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered chats based on search query
    val filteredChats: StateFlow<List<ChatEntity>> = combine(allActiveChats, searchQuery) { chats, query ->
        if (query.isEmpty()) chats
        else chats.filter { it.name.contains(query, ignoreCase = true) || (it.lastMessageText?.contains(query, ignoreCase = true) == true) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Observe Friends (all known non-blocked users except me)
    @OptIn(ExperimentalCoroutinesApi::class)
    val friendsList: StateFlow<List<UserEntity>> = currentUser.flatMapLatest { me ->
        if (me == null) flowOf(emptyList())
        else repository.getFriendsFlow(me.id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Observe Pending Requests
    val pendingRequests: StateFlow<List<FriendRequestEntity>> = repository.getPendingRequestsFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Observe Blocked Users
    val blockedUsers: StateFlow<List<UserEntity>> = repository.getBlockedUsersFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Active Chat flow
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeChat: StateFlow<ChatEntity?> = _activeChatId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else repository.getActiveChatsFlow().map { list -> list.find { it.id == id } }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // The peer user of the active 1:1 chat (for presence display)
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeChatPeer: StateFlow<UserEntity?> = combine(activeChat, currentUser) { chat, me ->
        if (chat == null || me == null || chat.isGroup) null
        else chat.members.split(",").firstOrNull { it.isNotBlank() && it != me.id }
    }.flatMapLatest { peerId ->
        if (peerId == null) flowOf(null)
        else repository.getUserFlow(peerId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Active Messages with reactive decryption
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<DecryptedMessage>> = _activeChatId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else repository.getMessagesForChatFlow(id).map { list ->
            withContext(Dispatchers.Default) {
                list.map { msg ->
                    val content = repository.decryptMessage(msg)
                    DecryptedMessage(msg, content.text, content.mediaBase64)
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            repository.initializeDatabaseIfNeeded()
            _isDbInitialized.value = true

            val firebaseUser = FirebaseAuth.getInstance().currentUser
            if (firebaseUser != null) {
                val success = repository.setupExistingUser(firebaseUser.uid)
                if (success) {
                    onLoggedIn(firebaseUser.uid)
                }
            }
            _isSessionLoading.value = false
        }
    }

    private fun onLoggedIn(uid: String) {
        _isLoggedIn.value = true
        repository.startGlobalSync(uid)
        viewModelScope.launch { repository.setPresence(true) }
    }

    // Real Firebase Authentication
    fun signUp(email: String, password: String, username: String, displayName: String, bio: String, avatarUrl: String) {
        if (email.isEmpty() || password.isEmpty() || username.isEmpty()) {
            _authError.value = "Email, password, and username are required."
            return
        }
        _isAuthLoading.value = true
        _authError.value = null
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: return@addOnSuccessListener
                viewModelScope.launch {
                    repository.setupNewUser(uid, username, displayName.ifEmpty { username }, bio, avatarUrl)
                    _isAuthLoading.value = false
                    onLoggedIn(uid)
                }
            }
            .addOnFailureListener { e ->
                _isAuthLoading.value = false
                _authError.value = e.localizedMessage ?: "Sign up failed."
                Log.e("CipherViewModel", "Sign up failed: ${e.localizedMessage}")
            }
    }

    fun login(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            _authError.value = "Email and password are required."
            return
        }
        _isAuthLoading.value = true
        _authError.value = null
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: return@addOnSuccessListener
                viewModelScope.launch {
                    val success = repository.setupExistingUser(uid)
                    _isAuthLoading.value = false
                    if (success) {
                        onLoggedIn(uid)
                    } else {
                        _authError.value = "Could not restore your account profile."
                        Log.e("CipherViewModel", "Existing user setup failed")
                    }
                }
            }
            .addOnFailureListener { e ->
                _isAuthLoading.value = false
                _authError.value = e.localizedMessage ?: "Login failed."
                Log.e("CipherViewModel", "Login failed: ${e.localizedMessage}")
            }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun logout() {
        viewModelScope.launch {
            repository.setPresence(false)
            repository.stopGlobalSync()
            FirebaseAuth.getInstance().signOut()
            repository.clearLocalDatabase()
            _activeChatId.value = null
            _isLoggedIn.value = false
        }
    }

    // Presence hooks driven by Activity lifecycle
    fun onAppForeground() {
        if (_isLoggedIn.value) {
            viewModelScope.launch { repository.setPresence(true) }
        }
    }

    fun onAppBackground() {
        if (_isLoggedIn.value) {
            viewModelScope.launch { repository.setPresence(false) }
        }
    }

    // Selection
    fun selectChat(chatId: String?) {
        _activeChatId.value = chatId
        _replyToMessage.value = null
        viewModelScope.launch {
            repository.setActiveChat(chatId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopGlobalSync()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setMessageInput(input: String) {
        _messageInput.value = input
    }

    fun setReplyTo(message: MessageEntity?) {
        _replyToMessage.value = message
    }

    // Chat Actions
    fun sendChatMessage(text: String) {
        val chatId = _activeChatId.value ?: return
        if (text.trim().isEmpty()) return

        viewModelScope.launch {
            repository.sendSecureMessage(
                chatId = chatId,
                text = text.trim(),
                replyToId = _replyToMessage.value?.id
            )
            _messageInput.value = ""
            _replyToMessage.value = null
        }
    }

    // Compress the picked image and send it inside the encrypted envelope
    fun sendImageMessage(uri: Uri, caption: String = "") {
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            _isSendingMedia.value = true
            try {
                // GIFs are sent as-is (recompressing to JPEG would freeze the animation)
                val isGif = MediaUtils.getMimeType(getApplication(), uri) == "image/gif"
                val base64 = if (isGif) {
                    MediaUtils.uriToRawBase64(getApplication(), uri)
                } else {
                    MediaUtils.uriToCompressedBase64(getApplication(), uri)
                }
                if (base64 != null) {
                    val result = repository.sendSecureMessage(
                        chatId = chatId,
                        text = caption.trim(),
                        mediaBase64 = base64,
                        mediaType = if (isGif) "GIF" else "IMAGE",
                        replyToId = _replyToMessage.value?.id
                    )
                    if (result.startsWith("ERROR")) {
                        showToast("Could not send the photo ($result)")
                    } else {
                        _messageInput.value = ""
                        _replyToMessage.value = null
                    }
                } else if (isGif) {
                    showToast("GIF too large — only GIFs under ~300KB can be sent")
                } else {
                    showToast("Could not read or compress that image")
                }
            } catch (e: Exception) {
                Log.e("CipherViewModel", "Image send failed", e)
                showToast("Photo failed: ${e.localizedMessage}")
            } finally {
                _isSendingMedia.value = false
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
    }

    fun addReactionToMessage(messageId: String, emoji: String) {
        viewModelScope.launch {
            repository.addReaction(messageId, emoji)
        }
    }

    fun starMessage(messageId: String, star: Boolean) {
        viewModelScope.launch {
            repository.setStarred(messageId, star)
        }
    }

    fun editMessage(messageId: String, newText: String) {
        viewModelScope.launch {
            repository.editMessage(messageId, newText)
        }
    }

    fun deleteMessageForMe(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessageForMe(messageId)
        }
    }

    fun deleteMessageForEveryone(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessageForEveryone(messageId)
        }
    }

    // Chat list item actions
    fun setChatPinned(chatId: String, pinned: Boolean) {
        viewModelScope.launch { repository.setChatPinned(chatId, pinned) }
    }

    fun setChatArchived(chatId: String, archived: Boolean) {
        viewModelScope.launch { repository.setChatArchived(chatId, archived) }
    }

    fun setChatMuted(chatId: String, muted: Boolean) {
        viewModelScope.launch { repository.setChatMuted(chatId, muted) }
    }

    // Friend Operations
    fun sendFriendRequest(username: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.sendFriendRequest(username)
            onResult(success)
        }
    }

    // Tap a friend → open (or create) the 1:1 chat
    fun openChatWithFriend(friendId: String) {
        viewModelScope.launch {
            val chatId = repository.openDirectChat(friendId)
            if (chatId != null) {
                selectChat(chatId)
            } else {
                showToast("Could not open the chat")
            }
        }
    }

    fun acceptFriendRequest(requestId: String) {
        viewModelScope.launch {
            repository.acceptFriendRequest(requestId)
        }
    }

    fun rejectFriendRequest(requestId: String) {
        viewModelScope.launch {
            repository.rejectFriendRequest(requestId)
        }
    }

    fun blockUser(userId: String) {
        viewModelScope.launch {
            repository.blockUser(userId)
        }
    }

    fun unblockUser(userId: String) {
        viewModelScope.launch {
            repository.unblockUser(userId)
        }
    }

    // Group Operations
    fun createGroup(name: String, description: String, memberIds: List<String>, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val gid = repository.createGroupChat(name, description, memberIds)
            onCreated(gid)
        }
    }

    // Profile
    fun updateProfile(displayName: String, bio: String, avatarUrl: String) {
        viewModelScope.launch {
            repository.updateMe(displayName, bio, avatarUrl)
        }
    }

    // Settings Operations
    fun updateThemeMode(mode: String) {
        prefs.edit().putString("theme", mode).apply()
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(current.copy(themeMode = mode))
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(current.copy(notificationsEnabled = enabled))
        }
    }

    fun updateFontSize(size: Int) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(current.copy(fontSize = size))
        }
    }
}
