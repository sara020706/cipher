package com.example.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object FirestoreSyncManager {
    private const val TAG = "FirestoreSyncManager"

    // Check if Firebase is properly configured on device
    fun isFirebaseAvailable(context: Context): Boolean {
        return try {
            val apps = FirebaseApp.getApps(context)
            apps.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private val firestoreInstance: FirebaseFirestore?
        get() = try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Firestore is not initialized. Please add google-services.json to configure Firebase.")
            null
        }

    // Sync a user's public key and profile to Firestore (End-to-End Key Exchange)
    suspend fun syncUserProfile(context: Context, user: UserEntity) = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable(context)) {
            Log.d(TAG, "Firebase unavailable, skipping remote user sync for: ${user.username}")
            return@withContext
        }

        val db = firestoreInstance ?: return@withContext
        val userMap = hashMapOf(
            "id" to user.id,
            "username" to user.username,
            "displayName" to user.displayName,
            "avatarUrl" to user.avatarUrl,
            "bio" to user.bio,
            "publicKey" to user.publicKey,
            "onlineStatus" to user.onlineStatus,
            "lastSeen" to user.lastSeen
        )

        try {
            val task = db.collection("users").document(user.id).set(userMap, SetOptions.merge())
            Tasks.await(task, 10, TimeUnit.SECONDS)
            Log.d(TAG, "Successfully synced user: ${user.username} to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync user to Firestore: ${e.message}")
        }
    }

    // Send an encrypted message to Firestore
    suspend fun sendEncryptedMessageToFirestore(context: Context, message: MessageEntity) = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable(context)) {
            Log.d(TAG, "Firebase unavailable, message ${message.id} saved locally only")
            return@withContext
        }

        val db = firestoreInstance ?: return@withContext
        val msgMap = hashMapOf(
            "id" to message.id,
            "chatId" to message.chatId,
            "senderId" to message.senderId,
            "encryptedPayload" to message.encryptedPayload,
            "selfEncryptedPayload" to message.selfEncryptedPayload,
            "timestamp" to message.timestamp,
            "status" to message.status,
            "isEdited" to message.isEdited,
            "reactions" to message.reactions,
            "replyToId" to message.replyToId,
            "mediaUri" to message.mediaUri,
            "mediaType" to message.mediaType
        )

        try {
            val task = db.collection("chats").document(message.chatId)
                .collection("messages").document(message.id)
                .set(msgMap)
            Tasks.await(task, 10, TimeUnit.SECONDS)
            Log.d(TAG, "Successfully sent encrypted message ${message.id} to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message to Firestore: ${e.message}")
        }
    }

    // Fetch new messages from Firestore for a specific chat
    suspend fun fetchMessagesFromFirestore(context: Context, chatId: String): List<MessageEntity> = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable(context)) {
            Log.d(TAG, "Firebase unavailable, skipping remote message fetch")
            return@withContext emptyList()
        }

        val db = firestoreInstance ?: return@withContext emptyList()
        try {
            val task = db.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("timestamp")
                .get()
            val snapshot = Tasks.await(task, 10, TimeUnit.SECONDS)

            snapshot.documents.mapNotNull { doc -> documentToMessage(doc, chatId) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch messages from Firestore: ${e.message}")
            emptyList()
        }
    }

    // Convert a Firestore message document into a MessageEntity
    fun documentToMessage(doc: DocumentSnapshot, chatId: String): MessageEntity? {
        val id = doc.getString("id") ?: return null
        val senderId = doc.getString("senderId") ?: return null
        return MessageEntity(
            id = id,
            chatId = chatId,
            senderId = senderId,
            encryptedPayload = doc.getString("encryptedPayload") ?: "",
            selfEncryptedPayload = doc.getString("selfEncryptedPayload") ?: "",
            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
            status = doc.getString("status") ?: "SENT",
            isEdited = doc.getBoolean("isEdited") ?: false,
            reactions = doc.getString("reactions") ?: "[]",
            replyToId = doc.getString("replyToId"),
            mediaUri = doc.getString("mediaUri"),
            mediaType = doc.getString("mediaType")
        )
    }

    // Convert a Firestore user document into a UserEntity (never carries a private key)
    private fun documentToUser(doc: DocumentSnapshot, isMe: Boolean): UserEntity? {
        val id = doc.getString("id") ?: doc.id
        val username = doc.getString("username") ?: return null
        return UserEntity(
            id = id,
            username = username,
            displayName = doc.getString("displayName") ?: username,
            avatarUrl = doc.getString("avatarUrl") ?: "",
            bio = doc.getString("bio") ?: "",
            publicKey = doc.getString("publicKey") ?: "",
            privateKey = "", // Private key is never synced
            isMe = isMe,
            onlineStatus = doc.getString("onlineStatus") ?: "OFFLINE",
            lastSeen = doc.getLong("lastSeen") ?: System.currentTimeMillis()
        )
    }

    // Search the remote /users collection by exact username
    suspend fun searchUserByUsername(context: Context, username: String): UserEntity? = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable(context)) return@withContext null
        val db = firestoreInstance ?: return@withContext null
        try {
            var task = db.collection("users").whereEqualTo("username", username).limit(1).get()
            var snapshot = Tasks.await(task, 10, TimeUnit.SECONDS)
            if (snapshot.isEmpty && username != username.lowercase()) {
                task = db.collection("users").whereEqualTo("username", username.lowercase()).limit(1).get()
                snapshot = Tasks.await(task, 10, TimeUnit.SECONDS)
            }
            snapshot.documents.firstOrNull()?.let { documentToUser(it, isMe = false) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search user '$username': ${e.message}")
            null
        }
    }

    // Write chat metadata to /chats/{chatId} so members' devices can discover it
    suspend fun syncChatMetadata(context: Context, chat: ChatEntity) = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable(context)) return@withContext
        val db = firestoreInstance ?: return@withContext
        val chatMap = hashMapOf(
            "id" to chat.id,
            "name" to chat.name,
            "avatarUrl" to chat.avatarUrl,
            "isGroup" to chat.isGroup,
            "members" to chat.members.split(",").filter { it.isNotBlank() },
            "lastMessageTime" to chat.lastMessageTime,
            "inviteLink" to chat.inviteLink
        )
        try {
            val task = db.collection("chats").document(chat.id).set(chatMap, SetOptions.merge())
            Tasks.await(task, 10, TimeUnit.SECONDS)
            Log.d(TAG, "Successfully synced chat metadata: ${chat.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync chat metadata: ${e.message}")
        }
    }

    // Send a friend request to /friend_requests/{requestId}
    suspend fun sendFriendRequestToFirestore(context: Context, request: FriendRequestEntity) = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable(context)) return@withContext
        val db = firestoreInstance ?: return@withContext
        val requestMap = hashMapOf(
            "id" to request.id,
            "senderId" to request.senderId,
            "senderName" to request.senderName,
            "senderAvatar" to request.senderAvatar,
            "receiverId" to request.receiverId,
            "status" to request.status
        )
        try {
            val task = db.collection("friend_requests").document(request.id).set(requestMap)
            Tasks.await(task, 10, TimeUnit.SECONDS)
            Log.d(TAG, "Successfully sent friend request ${request.id} to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send friend request to Firestore: ${e.message}")
        }
    }

    // Merge arbitrary fields into a message document (receipts, edits, deletes, reactions)
    suspend fun updateMessageFields(context: Context, chatId: String, messageId: String, fields: Map<String, Any?>) = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable(context)) return@withContext
        val db = firestoreInstance ?: return@withContext
        try {
            val task = db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .set(fields, SetOptions.merge())
            Tasks.await(task, 10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update message fields: ${e.message}")
        }
    }

    // Publish online/offline presence to my user document
    suspend fun syncPresence(context: Context, userId: String, status: String, lastSeen: Long) = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable(context)) return@withContext
        val db = firestoreInstance ?: return@withContext
        try {
            val task = db.collection("users").document(userId)
                .set(hashMapOf("onlineStatus" to status, "lastSeen" to lastSeen), SetOptions.merge())
            Tasks.await(task, 10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync presence: ${e.message}")
        }
    }

    suspend fun updateFriendRequestStatusInFirestore(context: Context, requestId: String, status: String) = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable(context)) return@withContext
        val db = firestoreInstance ?: return@withContext
        try {
            val task = db.collection("friend_requests").document(requestId)
                .set(hashMapOf("status" to status), SetOptions.merge())
            Tasks.await(task, 10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update friend request status: ${e.message}")
        }
    }

    suspend fun deleteFriendRequestFromFirestore(context: Context, requestId: String) = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable(context)) return@withContext
        val db = firestoreInstance ?: return@withContext
        try {
            val task = db.collection("friend_requests").document(requestId).delete()
            Tasks.await(task, 10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete friend request: ${e.message}")
        }
    }

    // Fetch user profile from Firestore (isMe = true only when restoring my own account)
    suspend fun fetchUserProfile(context: Context, userId: String, isMe: Boolean = true): UserEntity? = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable(context)) return@withContext null
        val db = firestoreInstance ?: return@withContext null
        try {
            val task = db.collection("users").document(userId).get()
            val doc = Tasks.await(task, 10, TimeUnit.SECONDS)
            if (doc.exists()) documentToUser(doc, isMe) else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user profile: ${e.message}")
            null
        }
    }
}
