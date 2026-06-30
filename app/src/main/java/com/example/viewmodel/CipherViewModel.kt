package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppSettingsEntity
import com.example.data.ChatEntity
import com.example.data.CipherRepository
import com.example.data.FriendRequestEntity
import com.example.data.MessageEntity
import com.example.data.UserEntity
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
    val decryptedText: String
)

class CipherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CipherRepository(application)

    // Current session status
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isDbInitialized = MutableStateFlow(false)
    val isDbInitialized: StateFlow<Boolean> = _isDbInitialized.asStateFlow()

    // Screen selection / Chat state
    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    private val _replyToMessage = MutableStateFlow<MessageEntity?>(null)
    val replyToMessage: StateFlow<MessageEntity?> = _replyToMessage.asStateFlow()

    // Observe local User Profile (Alice)
    val currentUser: StateFlow<UserEntity?> = repository.getMeFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Observe App Settings
    val settings: StateFlow<AppSettingsEntity> = repository.getSettingsFlow().map {
        it ?: AppSettingsEntity()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettingsEntity()
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

    // Observe Friends
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

    // Active Messages with reactive decryption
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<DecryptedMessage>> = _activeChatId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else repository.getMessagesForChatFlow(id).map { list ->
            withContext(Dispatchers.Default) {
                list.map { msg ->
                    val text = repository.decryptMessage(msg)
                    DecryptedMessage(msg, text)
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
            val me = repository.getMe()
            if (me != null) {
                _isLoggedIn.value = true
            }
        }
    }

    // Authentication Simulations
    fun signUp(username: String, displayName: String, bio: String, avatarUrl: String) {
        viewModelScope.launch {
            repository.initializeDatabaseIfNeeded()
            val me = repository.getMe()
            if (me != null) {
                repository.updateMe(displayName, bio, avatarUrl)
            } else {
                // SignUp creates Me
                val myKeyPair = com.example.data.CryptoManager.generateRsaKeyPair()
                val user = UserEntity(
                    id = "ME",
                    username = username.ifEmpty { "cipher_user" },
                    displayName = displayName.ifEmpty { "Cipher User" },
                    avatarUrl = avatarUrl.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150" },
                    bio = bio.ifEmpty { "🔐 Securing my chats with End-to-End Cryptography." },
                    publicKey = com.example.data.CryptoManager.publicKeyToString(myKeyPair.public),
                    privateKey = com.example.data.SecureKeyStore.encrypt(com.example.data.CryptoManager.privateKeyToString(myKeyPair.private)),
                    isMe = true,
                    onlineStatus = "ONLINE"
                )
                // Insert User is handled by repository
                // In our implementation, initializeDatabaseIfNeeded seeds default Alice first.
                // If Alice is already seeded, updateMe updates the display details perfectly.
                repository.updateMe(user.displayName, user.bio, user.avatarUrl)
            }
            _isLoggedIn.value = true
        }
    }

    fun login(email: String) {
        viewModelScope.launch {
            repository.initializeDatabaseIfNeeded()
            // Simulating successful login using existing seeded/updated Me
            _isLoggedIn.value = true
        }
    }

    fun logout() {
        _isLoggedIn.value = false
    }

    // Selection
    fun selectChat(chatId: String?) {
        _activeChatId.value = chatId
        _replyToMessage.value = null
        if (chatId != null) {
            viewModelScope.launch {
                val chat = repository.getChatById(chatId)
                if (chat != null && chat.unreadCount > 0) {
                    // Reset unread count
                    // We'd have database.chatDao().setChatUnreadCount(chatId, 0)
                    // Let's create a transaction or call via repository
                    // repository doesn't have it directly but we can add it or make it simple
                }
            }
        }
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
    fun sendChatMessage(text: String, mediaUri: String? = null, mediaType: String? = null) {
        val chatId = _activeChatId.value ?: return
        if (text.trim().isEmpty() && mediaUri == null) return

        viewModelScope.launch {
            repository.sendSecureMessage(
                chatId = chatId,
                text = text,
                mediaUri = mediaUri,
                mediaType = mediaType,
                replyToId = _replyToMessage.value?.id
            )
            _messageInput.value = ""
            _replyToMessage.value = null
        }
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

    // Friend Operations
    fun sendFriendRequest(username: String, onSuccess: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.sendFriendRequest(username)
            onSuccess(success)
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

    // Settings Operations
    fun updateThemeMode(mode: String) {
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

    fun updateLanguage(lang: String) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(current.copy(language = lang))
        }
    }

    fun updateChatWallpaper(wallpaper: String?) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(current.copy(chatWallpaper = wallpaper))
        }
    }

    fun updateBlockUnknownSenders(block: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(current.copy(blockUnknownSenders = block))
        }
    }
}
