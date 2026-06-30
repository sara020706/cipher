package com.example.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
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
            "timestamp" to message.timestamp,
            "status" to message.status,
            "isEdited" to message.isEdited,
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

            snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: return@mapNotNull null
                val senderId = doc.getString("senderId") ?: return@mapNotNull null
                val encryptedPayload = doc.getString("encryptedPayload") ?: ""
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                val status = doc.getString("status") ?: "SENT"
                val isEdited = doc.getBoolean("isEdited") ?: false
                val replyToId = doc.getString("replyToId")
                val mediaUri = doc.getString("mediaUri")
                val mediaType = doc.getString("mediaType")

                MessageEntity(
                    id = id,
                    chatId = chatId,
                    senderId = senderId,
                    encryptedPayload = encryptedPayload,
                    timestamp = timestamp,
                    status = status,
                    isEdited = isEdited,
                    replyToId = replyToId,
                    mediaUri = mediaUri,
                    mediaType = mediaType
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch messages from Firestore: ${e.message}")
            emptyList()
        }
    }
}
