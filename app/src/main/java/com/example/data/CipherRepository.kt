package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyPair
import java.util.UUID

class CipherRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getDatabase(context)
    private val userDao = database.userDao()
    private val chatDao = database.chatDao()
    private val messageDao = database.messageDao()
    private val friendRequestDao = database.friendRequestDao()
    private val groupMemberDao = database.groupMemberDao()
    private val appSettingsDao = database.appSettingsDao()

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    // Flow Getters
    fun getMeFlow(): Flow<UserEntity?> = userDao.getMeFlow()
    fun getActiveChatsFlow(): Flow<List<ChatEntity>> = chatDao.getActiveChatsFlow()
    fun getArchivedChatsFlow(): Flow<List<ChatEntity>> = chatDao.getArchivedChatsFlow()
    fun getMessagesForChatFlow(chatId: String): Flow<List<MessageEntity>> = messageDao.getMessagesForChatFlow(chatId)
    fun getPendingRequestsFlow(): Flow<List<FriendRequestEntity>> = friendRequestDao.getPendingRequestsFlow()
    fun getSettingsFlow(): Flow<AppSettingsEntity?> = appSettingsDao.getSettingsFlow()
    fun getBlockedUsersFlow(): Flow<List<UserEntity>> = userDao.getBlockedUsersFlow()

    fun getFriendsFlow(myId: String): Flow<List<UserEntity>> = userDao.getFriendsFlow(myId)

    // Suspended Getters
    suspend fun getMe(): UserEntity? = userDao.getMe()
    suspend fun getUserById(userId: String): UserEntity? = userDao.getUserById(userId)
    suspend fun getChatById(chatId: String): ChatEntity? = chatDao.getChatById(chatId)
    suspend fun getSettings(): AppSettingsEntity? = appSettingsDao.getSettings()

    // Initialize Database with Seed Data if Empty
    suspend fun initializeDatabaseIfNeeded() {
        withContext(Dispatchers.IO) {
            val me = userDao.getMe()
            if (me == null) {
                // 1. Generate My Keys
                val myKeyPair = CryptoManager.generateRsaKeyPair()
                val myPublicStr = CryptoManager.publicKeyToString(myKeyPair.public)
                val myPrivateStr = CryptoManager.privateKeyToString(myKeyPair.private)
                
                // Secure Key Management: Encrypt private key using Android KeyStore
                val securedPrivateStr = SecureKeyStore.encrypt(myPrivateStr)

                val myUser = UserEntity(
                    id = "ME",
                    username = "alice_cipher",
                    displayName = "Alice Vance",
                    avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150",
                    bio = "🔐 Cryptographer & Privacy Advocate. Always secure your endpoints.",
                    publicKey = myPublicStr,
                    privateKey = securedPrivateStr,
                    isMe = true,
                    onlineStatus = "ONLINE"
                )
                userDao.insertUser(myUser)
                
                // Sync profile to Firestore
                FirestoreSyncManager.syncUserProfile(appContext, myUser)

                // 2. Generate Settings
                appSettingsDao.saveSettings(AppSettingsEntity())

                // 3. Create AI Contacts
                // Bot 1: Alpha Secure
                val bot1Keys = CryptoManager.generateRsaKeyPair()
                val bot1User = UserEntity(
                    id = "BOT_ALPHA",
                    username = "alpha_secure",
                    displayName = "Alpha Secure (Bot)",
                    avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150",
                    bio = "🛡️ AI Security Specialist. Ask me anything about zero-knowledge, SHA-256, and AES-GCM.",
                    publicKey = CryptoManager.publicKeyToString(bot1Keys.public),
                    privateKey = SecureKeyStore.encrypt(CryptoManager.privateKeyToString(bot1Keys.private)),
                    onlineStatus = "ONLINE"
                )
                userDao.insertUser(bot1User)
                FirestoreSyncManager.syncUserProfile(appContext, bot1User)

                // Bot 2: Omega Bot
                val bot2Keys = CryptoManager.generateRsaKeyPair()
                val bot2User = UserEntity(
                    id = "BOT_OMEGA",
                    username = "omega_crypt",
                    displayName = "Omega Crypt (Bot)",
                    avatarUrl = "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150",
                    bio = "🤖 Precision Cryptography Agent. Specializes in hybrid encryption systems and mathematical verification.",
                    publicKey = CryptoManager.publicKeyToString(bot2Keys.public),
                    privateKey = SecureKeyStore.encrypt(CryptoManager.privateKeyToString(bot2Keys.private)),
                    onlineStatus = "ONLINE"
                )
                userDao.insertUser(bot2User)
                FirestoreSyncManager.syncUserProfile(appContext, bot2User)

                // Bot 3: Privacy Advocate
                val bot3Keys = CryptoManager.generateRsaKeyPair()
                val bot3User = UserEntity(
                    id = "BOT_ADVOCATE",
                    username = "privacy_hawk",
                    displayName = "Privacy Hawk (Bot)",
                    avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
                    bio = "🦅 Digital Rights Advocate. Let's discuss open-source integrity, decentralized protocols, and digital privacy laws.",
                    publicKey = CryptoManager.publicKeyToString(bot3Keys.public),
                    privateKey = SecureKeyStore.encrypt(CryptoManager.privateKeyToString(bot3Keys.private)),
                    onlineStatus = "OFFLINE"
                )
                userDao.insertUser(bot3User)
                FirestoreSyncManager.syncUserProfile(appContext, bot3User)

                // 4. Create Chats
                val chatAlpha = ChatEntity(
                    id = "CHAT_ALPHA",
                    name = bot1User.displayName,
                    avatarUrl = bot1User.avatarUrl,
                    isGroup = false,
                    lastMessageText = "Initial connection established. Forward secrecy verified.",
                    lastMessageTime = System.currentTimeMillis() - 3600000,
                    unreadCount = 1
                )
                chatDao.insertChat(chatAlpha)

                val chatOmega = ChatEntity(
                    id = "CHAT_OMEGA",
                    name = bot2User.displayName,
                    avatarUrl = bot2User.avatarUrl,
                    isGroup = false,
                    lastMessageText = "Awaiting secure message payload...",
                    lastMessageTime = System.currentTimeMillis() - 7200000,
                    unreadCount = 0
                )
                chatDao.insertChat(chatOmega)

                // Create a Group Chat
                val chatGroup = ChatEntity(
                    id = "CHAT_GROUP_DEV",
                    name = "Cipher Sandbox",
                    avatarUrl = "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=150",
                    isGroup = true,
                    lastMessageText = "Alpha Secure joined the channel.",
                    lastMessageTime = System.currentTimeMillis() - 1800000,
                    inviteLink = "https://cipherchat.security/invite/sandbox-dev"
                )
                chatDao.insertChat(chatGroup)

                // Populate Group Members
                groupMemberDao.insertMember(GroupMemberEntity("GM1", "CHAT_GROUP_DEV", "ME", "OWNER"))
                groupMemberDao.insertMember(GroupMemberEntity("GM2", "CHAT_GROUP_DEV", "BOT_ALPHA", "ADMIN"))
                groupMemberDao.insertMember(GroupMemberEntity("GM3", "CHAT_GROUP_DEV", "BOT_OMEGA", "MEMBER"))

                // 5. Initial Messages
                // Seed some E2EE messages
                val initMsgAlpha1 = MessageEntity(
                    id = "MSG_A1",
                    chatId = "CHAT_ALPHA",
                    senderId = "BOT_ALPHA",
                    encryptedPayload = CryptoManager.encrypt(
                        "Hello Alice! Welcome to CipherChat. Your keys have been generated locally. Tap any message bubble in this chat to inspect its secure raw database ciphertext payload!",
                        myKeyPair.public
                    ),
                    timestamp = System.currentTimeMillis() - 3600000,
                    status = "READ"
                )
                messageDao.insertMessage(initMsgAlpha1)

                val initMsgGroup = MessageEntity(
                    id = "MSG_G1",
                    chatId = "CHAT_GROUP_DEV",
                    senderId = "BOT_ALPHA",
                    encryptedPayload = CryptoManager.encrypt(
                        "This is a secure group playground channel. All members of the group can view decrypted payloads locally.",
                        myKeyPair.public
                    ),
                    timestamp = System.currentTimeMillis() - 1800000,
                    status = "READ"
                )
                messageDao.insertMessage(initMsgGroup)

                // Add sample friend request
                val request = FriendRequestEntity(
                    id = "REQ_1",
                    senderId = "BOT_ADVOCATE",
                    senderName = bot3User.displayName,
                    senderAvatar = bot3User.avatarUrl,
                    receiverId = "ME",
                    status = "PENDING"
                )
                friendRequestDao.insertRequest(request)
            }
        }
    }

    // Decrypt a message helper
    suspend fun decryptMessage(message: MessageEntity): String {
        return withContext(Dispatchers.Default) {
            if (message.isDeletedForEveryone) {
                return@withContext "🚫 This message was deleted."
            }
            if (message.encryptedPayload.isEmpty()) {
                return@withContext "[Empty Payload]"
            }

            try {
                // If it's sent by me or received by me, we can decrypt it using our private key
                val me = userDao.getMe() ?: return@withContext "[Decryption Error: Keys Uninitialized]"
                if (me.privateKey.isEmpty()) {
                    return@withContext "[Decryption Error: Private Key Missing]"
                }

                val decryptedPrivateKeyStr = if (me.privateKey.contains(":")) {
                    SecureKeyStore.decrypt(me.privateKey)
                } else {
                    me.privateKey
                }
                val privateKey = CryptoManager.stringToPrivateKey(decryptedPrivateKeyStr)
                CryptoManager.decrypt(message.encryptedPayload, privateKey)
            } catch (e: Exception) {
                Log.e("CipherRepository", "Decryption failed: ${e.localizedMessage}")
                "[🔐 DECRYPTION ERROR: This payload is encrypted and can only be decrypted by the intended recipient.]"
            }
        }
    }

    // Send a message securely with true on-device hybrid encryption
    suspend fun sendSecureMessage(
        chatId: String,
        text: String,
        mediaUri: String? = null,
        mediaType: String? = null,
        replyToId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val me = userDao.getMe() ?: return@withContext "ERROR-ME-NULL"
        val chat = chatDao.getChatById(chatId) ?: return@withContext "ERROR-CHAT-NULL"

        // 1. Determine the recipient's public key
        // In a group, we encrypt with our own public key for the local record (or simulate group keying)
        // In 1-1, we encrypt with the recipient's public key
        val recipientPublicKeyStr: String
        var isAiContact = false
        var botId = ""

        if (chat.isGroup) {
            recipientPublicKeyStr = me.publicKey
        } else {
            // Find the contact user
            // In 1-1 chat, the chatId is usually CHAT_ALPHA, CHAT_OMEGA, etc., or the contact's ID
            val contactId = if (chatId == "CHAT_ALPHA") "BOT_ALPHA" else if (chatId == "CHAT_OMEGA") "BOT_OMEGA" else chatId
            val contact = userDao.getUserById(contactId)
            if (contact != null) {
                recipientPublicKeyStr = contact.publicKey
                if (contactId.startsWith("BOT_")) {
                    isAiContact = true
                    botId = contactId
                }
            } else {
                recipientPublicKeyStr = me.publicKey
            }
        }

        val recipientPublicKey = CryptoManager.stringToPublicKey(recipientPublicKeyStr)

        // 2. Perform on-device Encryption
        val encryptedPayload = CryptoManager.encrypt(text, recipientPublicKey)

        val messageId = UUID.randomUUID().toString()
        val message = MessageEntity(
            id = messageId,
            chatId = chatId,
            senderId = me.id,
            encryptedPayload = encryptedPayload,
            timestamp = System.currentTimeMillis(),
            status = "SENT",
            replyToId = replyToId,
            mediaUri = mediaUri,
            mediaType = mediaType
        )

        messageDao.insertMessage(message)

        // Sync encrypted message to Firestore (Remote E2EE storage)
        FirestoreSyncManager.sendEncryptedMessageToFirestore(appContext, message)

        // Update Chat last message
        chatDao.updateLastMessage(chatId, text, System.currentTimeMillis())

        // 3. Trigger AI Responder if the contact is an AI Bot
        if (isAiContact) {
            triggerAiResponse(chatId, botId, text)
        }

        return@withContext messageId
    }

    // Trigger AI Responses in background with typing states
    private fun triggerAiResponse(chatId: String, botId: String, userMessageText: String) {
        repositoryScope.launch {
            try {
                // Update bot status to TYPING
                userDao.updateUserStatus(botId, "TYPING", System.currentTimeMillis())

                // Simulate typical network latency and typing duration
                kotlinx.coroutines.delay(2000)

                // Retrieve bot info
                val botUser = userDao.getUserById(botId) ?: return@launch
                val me = userDao.getMe() ?: return@launch

                // Build context for the bot conversation
                // Retrieve last 10 messages from this chat
                val messagesFlow = messageDao.getMessagesForChatFlow(chatId).firstOrNull() ?: emptyList()
                val chatHistory = mutableListOf<Pair<String, String>>()

                // Decrypt existing history for Gemini's context
                for (msg in messagesFlow.takeLast(10)) {
                    val decrypted = decryptMessage(msg)
                    val senderRole = if (msg.senderId == me.id) "ME" else botId
                    chatHistory.add(Pair(senderRole, decrypted))
                }

                // Add current message to context if not already there
                if (chatHistory.none { it.second == userMessageText }) {
                    chatHistory.add(Pair("ME", userMessageText))
                }

                // Call Gemini
                val aiReplyText = GeminiService.generateSecureResponse(
                    botName = botUser.displayName,
                    botPersona = botUser.bio,
                    chatHistory = chatHistory
                )

                // Encrypt response with our local Public Key so Alice can decrypt it!
                val myPublicKey = CryptoManager.stringToPublicKey(me.publicKey)
                val encryptedReplyPayload = CryptoManager.encrypt(aiReplyText, myPublicKey)

                val replyMessageId = UUID.randomUUID().toString()
                val botReplyMessage = MessageEntity(
                    id = replyMessageId,
                    chatId = chatId,
                    senderId = botId,
                    encryptedPayload = encryptedReplyPayload,
                    timestamp = System.currentTimeMillis(),
                    status = "READ"
                )

                messageDao.insertMessage(botReplyMessage)

                // Sync Bot encrypted response to Firestore (Remote E2EE storage)
                FirestoreSyncManager.sendEncryptedMessageToFirestore(appContext, botReplyMessage)

                // Update chat metadata
                chatDao.updateLastMessage(chatId, aiReplyText, System.currentTimeMillis())

                // Reset bot status to ONLINE
                userDao.updateUserStatus(botId, "ONLINE", System.currentTimeMillis())

            } catch (e: Exception) {
                Log.e("CipherRepository", "Failed to get AI response: ${e.localizedMessage}")
                userDao.updateUserStatus(botId, "ONLINE", System.currentTimeMillis())
            }
        }
    }

    // Chat operations
    suspend fun createGroupChat(name: String, description: String, memberIds: List<String>): String {
        return withContext(Dispatchers.IO) {
            val groupId = "GROUP_" + UUID.randomUUID().toString().take(8)
            val chat = ChatEntity(
                id = groupId,
                name = name,
                avatarUrl = "https://images.unsplash.com/photo-1582213782179-e0d53f98f2ca?w=150",
                isGroup = true,
                lastMessageText = "Group created: $description",
                lastMessageTime = System.currentTimeMillis(),
                inviteLink = "https://cipherchat.security/invite/$groupId"
            )
            chatDao.insertChat(chat)

            // Insert Group Members
            groupMemberDao.insertMember(GroupMemberEntity(groupId + "_ME", groupId, "ME", "OWNER"))
            for (mId in memberIds) {
                groupMemberDao.insertMember(GroupMemberEntity(groupId + "_" + mId, groupId, mId, "MEMBER"))
            }

            // Send initial notification message
            val me = userDao.getMe() ?: return@withContext groupId
            val plainText = "🔒 Group '$name' initialized with End-to-End Cryptography."
            val encPayload = CryptoManager.encrypt(plainText, CryptoManager.stringToPublicKey(me.publicKey))
            messageDao.insertMessage(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    chatId = groupId,
                    senderId = "SYSTEM",
                    encryptedPayload = encPayload,
                    status = "READ"
                )
            )

            groupId
        }
    }

    suspend fun removeGroupMember(chatId: String, userId: String) {
        groupMemberDao.removeMember(chatId, userId)
    }

    // Friend operations
    suspend fun sendFriendRequest(username: String): Boolean {
        return withContext(Dispatchers.IO) {
            // Find user in local database matching username
            // In a real app we'd search Firebase, here we scan local users
            val usersFlow = userDao.getAllUsersFlow().firstOrNull() ?: emptyList()
            val target = usersFlow.find { it.username.lowercase() == username.lowercase() }
            if (target != null && target.id != "ME") {
                val requestId = "REQ_" + UUID.randomUUID().toString().take(6)
                val request = FriendRequestEntity(
                    id = requestId,
                    senderId = "ME",
                    senderName = "Alice Vance",
                    senderAvatar = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150",
                    receiverId = target.id,
                    status = "PENDING"
                )
                friendRequestDao.insertRequest(request)
                true
            } else {
                false
            }
        }
    }

    suspend fun acceptFriendRequest(requestId: String) {
        withContext(Dispatchers.IO) {
            friendRequestDao.updateRequestStatus(requestId, "ACCEPTED")
            // Find request to find friend ID
            val requests = friendRequestDao.getPendingRequestsFlow().firstOrNull() ?: emptyList()
            val req = requests.find { it.id == requestId }
            if (req != null) {
                // Change request status or create active chat!
                val chat = ChatEntity(
                    id = req.senderId,
                    name = req.senderName,
                    avatarUrl = req.senderAvatar,
                    isGroup = false,
                    lastMessageText = "Secure connection initialized. Tap to chat.",
                    lastMessageTime = System.currentTimeMillis()
                )
                chatDao.insertChat(chat)
                friendRequestDao.deleteRequest(requestId)
            }
        }
    }

    suspend fun rejectFriendRequest(requestId: String) {
        friendRequestDao.deleteRequest(requestId)
    }

    // Message interactions
    suspend fun setStarred(messageId: String, starred: Boolean) {
        messageDao.setMessageStarred(messageId, starred)
    }

    suspend fun editMessage(messageId: String, newText: String) {
        withContext(Dispatchers.IO) {
            val msg = messageDao.getMessageById(messageId)
            val me = userDao.getMe()
            if (msg != null && me != null) {
                val encPayload = CryptoManager.encrypt(newText, CryptoManager.stringToPublicKey(me.publicKey))
                val updatedMsg = msg.copy(
                    encryptedPayload = encPayload,
                    isEdited = true
                )
                messageDao.insertMessage(updatedMsg)
                chatDao.updateLastMessage(msg.chatId, "$newText (edited)", System.currentTimeMillis())
            }
        }
    }

    suspend fun deleteMessageForEveryone(messageId: String) {
        messageDao.deleteMessageForEveryone(messageId)
    }

    suspend fun deleteMessageForMe(messageId: String) {
        messageDao.deleteMessageForMe(messageId)
    }

    suspend fun addReaction(messageId: String, emoji: String) {
        withContext(Dispatchers.IO) {
            val msg = messageDao.getMessageById(messageId)
            if (msg != null) {
                // Simulating reactions JSON update
                // Simple list representation: "[{\"emoji\":\"❤️\",\"user\":\"Alice\"}]"
                val updatedReactions = "[{\"emoji\":\"$emoji\",\"user\":\"Alice\"}]"
                messageDao.updateMessageReactions(messageId, updatedReactions)
            }
        }
    }

    // Settings
    suspend fun updateSettings(settings: AppSettingsEntity) {
        appSettingsDao.saveSettings(settings)
    }

    suspend fun blockUser(userId: String) {
        userDao.updateUserBlockedStatus(userId, true)
    }

    suspend fun unblockUser(userId: String) {
        userDao.updateUserBlockedStatus(userId, false)
    }

    suspend fun updateMe(displayName: String, bio: String, avatarUrl: String) {
        withContext(Dispatchers.IO) {
            val me = userDao.getMe()
            if (me != null) {
                val updated = me.copy(
                    displayName = displayName,
                    bio = bio,
                    avatarUrl = avatarUrl
                )
                userDao.updateUser(updated)
                
                // Sync updated profile to Firestore
                FirestoreSyncManager.syncUserProfile(appContext, updated)
            }
        }
    }
}
