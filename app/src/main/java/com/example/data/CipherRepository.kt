package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.PrivateKey
import java.security.PublicKey
import java.util.UUID

// Result of decrypting a message envelope: text plus optional embedded media
data class DecryptedContent(
    val text: String,
    val mediaBase64: String? = null
)

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

    // The chat currently open on screen: its messages are marked READ immediately
    // and don't trigger notifications
    @Volatile
    private var activeChatId: String? = null

    // Flow Getters
    fun getMeFlow(): Flow<UserEntity?> = userDao.getMeFlow()
    fun getActiveChatsFlow(): Flow<List<ChatEntity>> = chatDao.getActiveChatsFlow()
    fun getArchivedChatsFlow(): Flow<List<ChatEntity>> = chatDao.getArchivedChatsFlow()
    fun getMessagesForChatFlow(chatId: String): Flow<List<MessageEntity>> = messageDao.getMessagesForChatFlow(chatId)
    fun getPendingRequestsFlow(): Flow<List<FriendRequestEntity>> = friendRequestDao.getPendingRequestsFlow()
    fun getSettingsFlow(): Flow<AppSettingsEntity?> = appSettingsDao.getSettingsFlow()
    fun getBlockedUsersFlow(): Flow<List<UserEntity>> = userDao.getBlockedUsersFlow()

    fun getFriendsFlow(myId: String): Flow<List<UserEntity>> = userDao.getFriendsFlow(myId)
    fun getUserFlow(userId: String): Flow<UserEntity?> = userDao.getUserFlow(userId)

    // Suspended Getters
    suspend fun getMe(): UserEntity? = userDao.getMe()
    suspend fun getUserById(userId: String): UserEntity? = userDao.getUserById(userId)
    suspend fun getChatById(chatId: String): ChatEntity? = chatDao.getChatById(chatId)
    suspend fun getSettings(): AppSettingsEntity? = appSettingsDao.getSettings()

    // Initialize Database with settings if empty
    suspend fun initializeDatabaseIfNeeded() {
        withContext(Dispatchers.IO) {
            val settings = appSettingsDao.getSettings()
            if (settings == null) {
                appSettingsDao.saveSettings(AppSettingsEntity())
            }
        }
    }

    // Set up a new user when signing up
    suspend fun setupNewUser(
        firebaseUid: String,
        username: String,
        displayName: String,
        bio: String,
        avatarUrl: String
    ) {
        withContext(Dispatchers.IO) {
            val myKeyPair = CryptoManager.generateRsaKeyPair()
            val myPublicStr = CryptoManager.publicKeyToString(myKeyPair.public)
            val myPrivateStr = CryptoManager.privateKeyToString(myKeyPair.private)
            val securedPrivateStr = SecureKeyStore.encrypt(myPrivateStr)

            val myUser = UserEntity(
                id = firebaseUid,
                username = username,
                displayName = displayName,
                avatarUrl = avatarUrl,
                bio = bio,
                publicKey = myPublicStr,
                privateKey = securedPrivateStr,
                isMe = true,
                onlineStatus = "ONLINE"
            )
            userDao.insertUser(myUser)
            FirestoreSyncManager.syncUserProfile(appContext, myUser)

            if (appSettingsDao.getSettings() == null) {
                appSettingsDao.saveSettings(AppSettingsEntity())
            }
        }
    }

    // Set up an existing user when logging in on a device
    suspend fun setupExistingUser(firebaseUid: String): Boolean {
        return withContext(Dispatchers.IO) {
            val localMe = userDao.getMe()
            if (localMe != null && localMe.id == firebaseUid) {
                return@withContext true
            }

            val firestoreUser = FirestoreSyncManager.fetchUserProfile(appContext, firebaseUid) ?: return@withContext false
            userDao.insertUser(firestoreUser)

            if (appSettingsDao.getSettings() == null) {
                appSettingsDao.saveSettings(AppSettingsEntity())
            }
            true
        }
    }

    // Clear local data on logout
    suspend fun clearLocalDatabase() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
    }

    // ==================== Presence ====================

    suspend fun setPresence(online: Boolean) {
        withContext(Dispatchers.IO) {
            val me = userDao.getMe() ?: return@withContext
            val status = if (online) "ONLINE" else "OFFLINE"
            val now = System.currentTimeMillis()
            userDao.updateUserStatus(me.id, status, now)
            FirestoreSyncManager.syncPresence(appContext, me.id, status, now)
        }
    }

    // ==================== Global Sync Listeners ====================

    private var chatListListenerRegistration: ListenerRegistration? = null
    private val friendRequestListenerRegistrations = mutableListOf<ListenerRegistration>()
    private val chatMessageListenerRegistrations = mutableMapOf<String, ListenerRegistration>()
    private val userListenerRegistrations = mutableMapOf<String, ListenerRegistration>()

    // Start all account-level listeners: chats I'm a member of, and friend requests
    fun startGlobalSync(myId: String) {
        if (!FirestoreSyncManager.isFirebaseAvailable(appContext)) return
        startListeningForChatList(myId)
        startListeningForFriendRequests(myId)
    }

    fun stopGlobalSync() {
        chatListListenerRegistration?.remove()
        chatListListenerRegistration = null
        friendRequestListenerRegistrations.forEach { it.remove() }
        friendRequestListenerRegistrations.clear()
        chatMessageListenerRegistrations.values.forEach { it.remove() }
        chatMessageListenerRegistrations.clear()
        userListenerRegistrations.values.forEach { it.remove() }
        userListenerRegistrations.clear()
    }

    // Mark a chat as opened: clear badge, send READ receipts, suppress its notifications
    suspend fun setActiveChat(chatId: String?) {
        activeChatId = chatId
        if (chatId == null) return
        withContext(Dispatchers.IO) {
            chatDao.setChatUnreadCount(chatId, 0)
            val me = userDao.getMe() ?: return@withContext
            val messages = messageDao.getMessagesForChatOnce(chatId)
            for (msg in messages) {
                if (msg.senderId != me.id && msg.status != "READ") {
                    messageDao.updateMessageStatus(msg.id, "READ")
                    FirestoreSyncManager.updateMessageFields(
                        appContext, chatId, msg.id, mapOf("status" to "READ")
                    )
                }
            }
        }
    }

    // Discover chats in Firestore whose members list contains my UID
    private fun startListeningForChatList(myId: String) {
        chatListListenerRegistration?.remove()
        val db = FirebaseFirestore.getInstance()
        chatListListenerRegistration = db.collection("chats")
            .whereArrayContains("members", myId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("CipherRepository", "Chat list listen failed.", e)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                repositoryScope.launch(Dispatchers.IO) {
                    for (doc in snapshot.documents) {
                        val chatId = doc.getString("id") ?: doc.id
                        val members = (doc.get("members") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList()

                        // Make sure every member's profile (and public key) exists locally
                        for (uid in members) {
                            if (uid != myId && userDao.getUserById(uid) == null) {
                                FirestoreSyncManager.fetchUserProfile(appContext, uid, isMe = false)
                                    ?.let { userDao.insertUser(it) }
                            }
                        }

                        if (chatDao.getChatById(chatId) == null) {
                            val isGroup = doc.getBoolean("isGroup") ?: false
                            // For 1:1 chats display the peer's name, not whatever the creator stored
                            val peer = members.firstOrNull { it != myId }?.let { userDao.getUserById(it) }
                            val chat = ChatEntity(
                                id = chatId,
                                name = if (!isGroup && peer != null) peer.displayName else doc.getString("name") ?: "Secure Chat",
                                avatarUrl = if (!isGroup && peer != null) peer.avatarUrl else doc.getString("avatarUrl") ?: "",
                                isGroup = isGroup,
                                lastMessageText = "Secure connection initialized. Tap to chat.",
                                lastMessageTime = doc.getLong("lastMessageTime") ?: System.currentTimeMillis(),
                                inviteLink = doc.getString("inviteLink"),
                                members = members.joinToString(",")
                            )
                            chatDao.insertChat(chat)
                            for (uid in members) {
                                groupMemberDao.insertMember(GroupMemberEntity("${chatId}_$uid", chatId, uid, "MEMBER"))
                            }
                        }

                        attachChatMessageListener(chatId, myId)
                        members.filter { it != myId }.forEach { attachUserListener(it) }
                    }
                }
            }
    }

    // Keep a peer's local profile (presence, name, avatar, key) in sync with Firestore
    private fun attachUserListener(userId: String) {
        if (userListenerRegistrations.containsKey(userId)) return
        val db = FirebaseFirestore.getInstance()
        val registration = db.collection("users").document(userId)
            .addSnapshotListener { doc, e ->
                if (e != null || doc == null || !doc.exists()) return@addSnapshotListener
                repositoryScope.launch(Dispatchers.IO) {
                    val existing = userDao.getUserById(userId)
                    val updated = UserEntity(
                        id = userId,
                        username = doc.getString("username") ?: existing?.username ?: "",
                        displayName = doc.getString("displayName") ?: existing?.displayName ?: "",
                        avatarUrl = doc.getString("avatarUrl") ?: existing?.avatarUrl ?: "",
                        bio = doc.getString("bio") ?: existing?.bio ?: "",
                        publicKey = doc.getString("publicKey") ?: existing?.publicKey ?: "",
                        privateKey = "",
                        isMe = false,
                        onlineStatus = doc.getString("onlineStatus") ?: "OFFLINE",
                        lastSeen = doc.getLong("lastSeen") ?: System.currentTimeMillis(),
                        isBlocked = existing?.isBlocked ?: false
                    )
                    userDao.insertUser(updated)
                }
            }
        userListenerRegistrations[userId] = registration
    }

    // Background listener for one chat: pulls new messages and remote message updates
    // (receipts, edits, deletes, reactions) even when the chat is not open
    private fun attachChatMessageListener(chatId: String, myId: String) {
        if (chatMessageListenerRegistrations.containsKey(chatId)) return
        val db = FirebaseFirestore.getInstance()
        val registration = db.collection("chats").document(chatId)
            .collection("messages")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("CipherRepository", "Message listen failed for $chatId.", e)
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.isEmpty) return@addSnapshotListener

                repositoryScope.launch(Dispatchers.IO) {
                    for (change in snapshot.documentChanges) {
                        val msg = FirestoreSyncManager.documentToMessage(change.document, chatId) ?: continue
                        when (change.type) {
                            DocumentChange.Type.ADDED -> handleIncomingMessage(chatId, myId, msg)
                            DocumentChange.Type.MODIFIED -> handleModifiedMessage(msg)
                            else -> {}
                        }
                    }
                }
            }
        chatMessageListenerRegistrations[chatId] = registration
    }

    private suspend fun handleIncomingMessage(chatId: String, myId: String, msg: MessageEntity) {
        if (messageDao.getMessageById(msg.id) != null) return
        if (msg.senderId == myId) {
            // My own message echoed from another of my devices
            messageDao.insertMessage(msg)
            return
        }

        // Enforce blocking: drop messages from blocked users entirely
        val sender = userDao.getUserById(msg.senderId)
        if (sender?.isBlocked == true) return

        val isActive = activeChatId == chatId
        val stored = msg.copy(status = if (isActive) "READ" else "DELIVERED")
        messageDao.insertMessage(stored)

        // Send the delivery/read receipt back to the sender
        FirestoreSyncManager.updateMessageFields(
            appContext, chatId, msg.id, mapOf("status" to stored.status)
        )

        val content = decryptMessage(stored)
        val preview = if (content.mediaBase64 != null) "📷 Photo" else content.text
        chatDao.updateLastMessage(chatId, preview, msg.timestamp)

        if (!isActive) {
            chatDao.incrementUnreadCount(chatId)
            maybeNotify(chatId, msg.senderId, preview)
        }
    }

    private suspend fun handleModifiedMessage(remote: MessageEntity) {
        val local = messageDao.getMessageById(remote.id) ?: return
        // Merge remote-authoritative fields, preserving local-only flags
        val merged = local.copy(
            encryptedPayload = remote.encryptedPayload,
            selfEncryptedPayload = remote.selfEncryptedPayload,
            status = remote.status,
            isEdited = remote.isEdited,
            reactions = remote.reactions,
            isDeletedForEveryone = local.isDeletedForEveryone || remote.encryptedPayload.isEmpty()
        )
        messageDao.insertMessage(merged)
    }

    private suspend fun maybeNotify(chatId: String, senderId: String, preview: String) {
        val settings = appSettingsDao.getSettings()
        if (settings?.notificationsEnabled == false) return
        val chat = chatDao.getChatById(chatId) ?: return
        if (chat.muted) return
        val senderName = userDao.getUserById(senderId)?.displayName ?: chat.name
        val title = if (chat.isGroup) "${chat.name} · $senderName" else senderName
        NotificationHelper.showMessageNotification(appContext, chatId, title, preview)
    }

    // Listen for friend requests addressed to me, and for acceptance of ones I sent
    private fun startListeningForFriendRequests(myId: String) {
        friendRequestListenerRegistrations.forEach { it.remove() }
        friendRequestListenerRegistrations.clear()
        val db = FirebaseFirestore.getInstance()

        // Incoming requests → surface them locally as pending
        friendRequestListenerRegistrations += db.collection("friend_requests")
            .whereEqualTo("receiverId", myId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("CipherRepository", "Friend request listen failed.", e)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                repositoryScope.launch(Dispatchers.IO) {
                    for (doc in snapshot.documents) {
                        if (doc.getString("status") != "PENDING") continue
                        val senderId = doc.getString("senderId") ?: continue
                        // Enforce blocking on requests too
                        if (userDao.getUserById(senderId)?.isBlocked == true) continue
                        val request = FriendRequestEntity(
                            id = doc.getString("id") ?: doc.id,
                            senderId = senderId,
                            senderName = doc.getString("senderName") ?: "Unknown",
                            senderAvatar = doc.getString("senderAvatar") ?: "",
                            receiverId = myId,
                            status = "PENDING"
                        )
                        friendRequestDao.insertRequest(request)
                    }
                }
            }

        // Requests I sent that were accepted → clean up local + remote copies
        friendRequestListenerRegistrations += db.collection("friend_requests")
            .whereEqualTo("senderId", myId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                repositoryScope.launch(Dispatchers.IO) {
                    for (doc in snapshot.documents) {
                        if (doc.getString("status") != "ACCEPTED") continue
                        val requestId = doc.getString("id") ?: doc.id
                        friendRequestDao.deleteRequest(requestId)
                        FirestoreSyncManager.deleteFriendRequestFromFirestore(appContext, requestId)
                    }
                }
            }
    }

    // ==================== Encryption / Decryption ====================

    private suspend fun getMyPrivateKey(me: UserEntity): PrivateKey? {
        if (me.privateKey.isEmpty()) return null
        return try {
            val decrypted = if (me.privateKey.contains(":")) {
                SecureKeyStore.decrypt(me.privateKey)
            } else {
                me.privateKey
            }
            CryptoManager.stringToPrivateKey(decrypted)
        } catch (e: Exception) {
            Log.e("CipherRepository", "Failed to load private key: ${e.localizedMessage}")
            null
        }
    }

    // Collect every chat member's public key (including my own), fetching missing
    // profiles from Firestore so brand-new contacts work immediately
    private suspend fun resolveRecipientKeys(chat: ChatEntity, me: UserEntity): Map<String, PublicKey> {
        val memberIds = chat.members.split(",").filter { it.isNotBlank() }.ifEmpty { listOf(me.id) }
        val keys = mutableMapOf<String, PublicKey>()
        for (uid in (memberIds + me.id).distinct()) {
            val user = if (uid == me.id) me else {
                userDao.getUserById(uid)
                    ?: FirestoreSyncManager.fetchUserProfile(appContext, uid, isMe = false)
                        ?.also { userDao.insertUser(it) }
            }
            val keyStr = user?.publicKey
            if (keyStr.isNullOrEmpty()) {
                Log.w("CipherRepository", "No public key for member $uid; they won't be able to decrypt")
                continue
            }
            try {
                keys[uid] = CryptoManager.stringToPublicKey(keyStr)
            } catch (e: Exception) {
                Log.e("CipherRepository", "Invalid public key for $uid")
            }
        }
        return keys
    }

    private fun buildContentJson(text: String, mediaBase64: String?): String {
        val obj = JSONObject().put("text", text)
        if (mediaBase64 != null) obj.put("media", mediaBase64)
        return obj.toString()
    }

    // Decrypt a message into text + optional media
    suspend fun decryptMessage(message: MessageEntity): DecryptedContent {
        return withContext(Dispatchers.Default) {
            if (message.isDeletedForEveryone) {
                return@withContext DecryptedContent("🚫 This message was deleted.")
            }
            if (message.encryptedPayload.isEmpty()) {
                return@withContext DecryptedContent("[Empty Payload]")
            }

            try {
                val me = userDao.getMe() ?: return@withContext DecryptedContent("[Decryption Error: Keys Uninitialized]")
                val privateKey = getMyPrivateKey(me)
                    ?: return@withContext DecryptedContent("[Decryption Error: Private Key Missing]")

                if (CryptoManager.isEnvelope(message.encryptedPayload)) {
                    // v2 envelope: one ciphertext, per-member wrapped keys
                    val contentJson = CryptoManager.decryptEnvelope(message.encryptedPayload, me.id, privateKey)
                    val obj = JSONObject(contentJson)
                    DecryptedContent(
                        text = obj.optString("text", ""),
                        mediaBase64 = if (obj.has("media")) obj.getString("media") else null
                    )
                } else {
                    // Legacy compound format
                    val payload = if (message.senderId == me.id && message.selfEncryptedPayload.isNotEmpty()) {
                        message.selfEncryptedPayload
                    } else {
                        message.encryptedPayload
                    }
                    DecryptedContent(CryptoManager.decrypt(payload, privateKey))
                }
            } catch (e: Exception) {
                Log.e("CipherRepository", "Decryption failed: ${e.localizedMessage}")
                DecryptedContent("[🔐 Encrypted — only chat members can read this message.]")
            }
        }
    }

    // Send a message: encrypted once, readable by every chat member (and myself)
    suspend fun sendSecureMessage(
        chatId: String,
        text: String,
        mediaBase64: String? = null,
        mediaType: String? = null,
        replyToId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val me = userDao.getMe() ?: return@withContext "ERROR-ME-NULL"
        val chat = chatDao.getChatById(chatId) ?: return@withContext "ERROR-CHAT-NULL"

        val recipientKeys = resolveRecipientKeys(chat, me)
        if (recipientKeys.isEmpty()) return@withContext "ERROR-NO-KEYS"

        val envelope = CryptoManager.encryptEnvelope(buildContentJson(text, mediaBase64), recipientKeys)

        // Firestore rejects documents over ~1MB; fail loudly instead of silently
        // saving a message the other side will never receive
        if (envelope.length > 950_000) {
            Log.e("CipherRepository", "Encrypted payload too large for Firestore: ${envelope.length} bytes")
            return@withContext "ERROR-TOO-LARGE"
        }

        val messageId = UUID.randomUUID().toString()
        val message = MessageEntity(
            id = messageId,
            chatId = chatId,
            senderId = me.id,
            encryptedPayload = envelope,
            timestamp = System.currentTimeMillis(),
            status = "SENT",
            replyToId = replyToId,
            mediaType = mediaType
        )

        messageDao.insertMessage(message)
        FirestoreSyncManager.sendEncryptedMessageToFirestore(appContext, message)

        val preview = if (mediaBase64 != null) "📷 Photo" else text
        chatDao.updateLastMessage(chatId, preview, System.currentTimeMillis())

        return@withContext messageId
    }

    // ==================== Chat operations ====================

    suspend fun createGroupChat(name: String, description: String, memberIds: List<String>): String {
        return withContext(Dispatchers.IO) {
            val groupId = "GROUP_" + UUID.randomUUID().toString().take(8)
            val me = userDao.getMe() ?: return@withContext groupId
            val allMemberIds = (listOf(me.id) + memberIds).distinct()
            val chat = ChatEntity(
                id = groupId,
                name = name,
                avatarUrl = "",
                isGroup = true,
                lastMessageText = "Group created: $description",
                lastMessageTime = System.currentTimeMillis(),
                inviteLink = "https://cipherchat.security/invite/$groupId",
                members = allMemberIds.joinToString(",")
            )
            chatDao.insertChat(chat)

            groupMemberDao.insertMember(GroupMemberEntity(groupId + "_" + me.id, groupId, me.id, "OWNER"))
            for (mId in memberIds) {
                groupMemberDao.insertMember(GroupMemberEntity(groupId + "_" + mId, groupId, mId, "MEMBER"))
            }

            // Publish group metadata so members' devices discover it
            FirestoreSyncManager.syncChatMetadata(appContext, chat)

            // System message announcing the group, readable by all members
            val recipientKeys = resolveRecipientKeys(chat, me)
            if (recipientKeys.isNotEmpty()) {
                val plainText = "🔒 Group '$name' initialized with End-to-End Cryptography."
                val envelope = CryptoManager.encryptEnvelope(buildContentJson(plainText, null), recipientKeys)
                val sysMessage = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    chatId = groupId,
                    senderId = me.id,
                    encryptedPayload = envelope,
                    status = "SENT"
                )
                messageDao.insertMessage(sysMessage)
                FirestoreSyncManager.sendEncryptedMessageToFirestore(appContext, sysMessage)
            }

            groupId
        }
    }

    suspend fun removeGroupMember(chatId: String, userId: String) {
        withContext(Dispatchers.IO) {
            groupMemberDao.removeMember(chatId, userId)
            val chat = chatDao.getChatById(chatId) ?: return@withContext
            val remaining = chat.members.split(",").filter { it.isNotBlank() && it != userId }
            val updated = chat.copy(members = remaining.joinToString(","))
            chatDao.updateChat(updated)
            FirestoreSyncManager.syncChatMetadata(appContext, updated)
        }
    }

    // ==================== Friend operations ====================

    // Searches the remote /users collection so users registered on other devices are discoverable
    suspend fun sendFriendRequest(username: String): Boolean {
        return withContext(Dispatchers.IO) {
            val me = userDao.getMe() ?: return@withContext false
            val target = FirestoreSyncManager.searchUserByUsername(appContext, username)
            if (target == null || target.id == me.id) return@withContext false

            // Cache the contact (and their public key) locally for encryption later
            userDao.insertUser(target)

            val requestId = "REQ_" + UUID.randomUUID().toString().take(6)
            val request = FriendRequestEntity(
                id = requestId,
                senderId = me.id,
                senderName = me.displayName,
                senderAvatar = me.avatarUrl,
                receiverId = target.id,
                status = "PENDING"
            )
            friendRequestDao.insertRequest(request)
            FirestoreSyncManager.sendFriendRequestToFirestore(appContext, request)
            true
        }
    }

    suspend fun acceptFriendRequest(requestId: String) {
        withContext(Dispatchers.IO) {
            val requests = friendRequestDao.getPendingRequestsFlow().firstOrNull() ?: emptyList()
            val req = requests.find { it.id == requestId } ?: return@withContext
            val me = userDao.getMe() ?: return@withContext

            // Make sure the sender's profile and public key are available locally
            if (userDao.getUserById(req.senderId) == null) {
                FirestoreSyncManager.fetchUserProfile(appContext, req.senderId, isMe = false)
                    ?.let { userDao.insertUser(it) }
            }

            // Deterministic 1:1 chat id so both devices resolve the same chat
            val chatId = listOf(me.id, req.senderId).sorted().joinToString("_")
            val chat = ChatEntity(
                id = chatId,
                name = req.senderName,
                avatarUrl = req.senderAvatar,
                isGroup = false,
                lastMessageText = "Secure connection initialized. Tap to chat.",
                lastMessageTime = System.currentTimeMillis(),
                members = "${me.id},${req.senderId}"
            )
            chatDao.insertChat(chat)
            groupMemberDao.insertMember(GroupMemberEntity("${chatId}_${me.id}", chatId, me.id, "MEMBER"))
            groupMemberDao.insertMember(GroupMemberEntity("${chatId}_${req.senderId}", chatId, req.senderId, "MEMBER"))

            // Publish the chat so the sender's device discovers it via the chat-list listener
            FirestoreSyncManager.syncChatMetadata(appContext, chat)
            FirestoreSyncManager.updateFriendRequestStatusInFirestore(appContext, requestId, "ACCEPTED")
            friendRequestDao.deleteRequest(requestId)
        }
    }

    suspend fun rejectFriendRequest(requestId: String) {
        withContext(Dispatchers.IO) {
            friendRequestDao.deleteRequest(requestId)
            FirestoreSyncManager.deleteFriendRequestFromFirestore(appContext, requestId)
        }
    }

    // ==================== Message interactions (synced) ====================

    suspend fun setStarred(messageId: String, starred: Boolean) {
        messageDao.setMessageStarred(messageId, starred)
    }

    suspend fun editMessage(messageId: String, newText: String) {
        withContext(Dispatchers.IO) {
            val msg = messageDao.getMessageById(messageId) ?: return@withContext
            val me = userDao.getMe() ?: return@withContext
            if (msg.senderId != me.id) return@withContext
            val chat = chatDao.getChatById(msg.chatId) ?: return@withContext

            val recipientKeys = resolveRecipientKeys(chat, me)
            if (recipientKeys.isEmpty()) return@withContext
            val envelope = CryptoManager.encryptEnvelope(buildContentJson(newText, null), recipientKeys)

            val updatedMsg = msg.copy(encryptedPayload = envelope, isEdited = true)
            messageDao.insertMessage(updatedMsg)
            chatDao.updateLastMessage(msg.chatId, "$newText (edited)", System.currentTimeMillis())

            FirestoreSyncManager.updateMessageFields(
                appContext, msg.chatId, messageId,
                mapOf("encryptedPayload" to envelope, "isEdited" to true)
            )
        }
    }

    suspend fun deleteMessageForEveryone(messageId: String) {
        withContext(Dispatchers.IO) {
            val msg = messageDao.getMessageById(messageId) ?: return@withContext
            messageDao.deleteMessageForEveryone(messageId)
            FirestoreSyncManager.updateMessageFields(
                appContext, msg.chatId, messageId,
                mapOf("encryptedPayload" to "", "isDeletedForEveryone" to true)
            )
        }
    }

    suspend fun deleteMessageForMe(messageId: String) {
        messageDao.deleteMessageForMe(messageId)
    }

    // Toggle a reaction; reactions are stored as a JSON array and synced remotely
    suspend fun addReaction(messageId: String, emoji: String) {
        withContext(Dispatchers.IO) {
            val msg = messageDao.getMessageById(messageId) ?: return@withContext
            val me = userDao.getMe() ?: return@withContext

            val reactions = try {
                JSONArray(msg.reactions)
            } catch (e: Exception) {
                JSONArray()
            }

            // If I already reacted with this emoji, remove it; otherwise add it
            var existingIndex = -1
            for (i in 0 until reactions.length()) {
                val r = reactions.optJSONObject(i) ?: continue
                if (r.optString("userId") == me.id && r.optString("emoji") == emoji) {
                    existingIndex = i
                    break
                }
            }
            if (existingIndex >= 0) {
                reactions.remove(existingIndex)
            } else {
                reactions.put(
                    JSONObject()
                        .put("emoji", emoji)
                        .put("userId", me.id)
                        .put("user", me.displayName)
                )
            }

            val json = reactions.toString()
            messageDao.updateMessageReactions(messageId, json)
            FirestoreSyncManager.updateMessageFields(
                appContext, msg.chatId, messageId, mapOf("reactions" to json)
            )
        }
    }

    // ==================== Settings / Profile ====================

    suspend fun updateSettings(settings: AppSettingsEntity) {
        appSettingsDao.saveSettings(settings)
    }

    suspend fun blockUser(userId: String) {
        userDao.updateUserBlockedStatus(userId, true)
    }

    suspend fun unblockUser(userId: String) {
        userDao.updateUserBlockedStatus(userId, false)
    }

    suspend fun clearUnreadCount(chatId: String) {
        withContext(Dispatchers.IO) {
            chatDao.setChatUnreadCount(chatId, 0)
        }
    }

    suspend fun setChatPinned(chatId: String, pinned: Boolean) = chatDao.setChatPinned(chatId, pinned)
    suspend fun setChatArchived(chatId: String, archived: Boolean) = chatDao.setChatArchived(chatId, archived)
    suspend fun setChatMuted(chatId: String, muted: Boolean) = chatDao.setChatMuted(chatId, muted)

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
