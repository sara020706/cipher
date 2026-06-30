package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val bio: String,
    val publicKey: String,
    val privateKey: String = "", // Only populated if isMe is true
    val isMe: Boolean = false,
    val onlineStatus: String = "OFFLINE", // "ONLINE", "OFFLINE", "TYPING"
    val lastSeen: Long = System.currentTimeMillis(),
    val joinDate: Long = System.currentTimeMillis(),
    val privacySetting: String = "EVERYONE", // "EVERYONE", "FRIENDS"
    val isBlocked: Boolean = false
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUrl: String,
    val isGroup: Boolean,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val muted: Boolean = false,
    val wallpaper: String? = null,
    val lastMessageText: String? = null,
    val lastMessageTime: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val inviteLink: String? = null
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val encryptedPayload: String, // Hybrid encrypted string
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SENT", // "SENT", "DELIVERED", "READ"
    val isEdited: Boolean = false,
    val replyToId: String? = null,
    val reactions: String = "[]", // JSON array of reactions
    val isStarred: Boolean = false,
    val isDeletedForEveryone: Boolean = false,
    val isDeletedForMe: Boolean = false,
    val mediaUri: String? = null, // URI or description of shared asset
    val mediaType: String? = null // "IMAGE", "VIDEO", "VOICE", "FILE", null
)

@Entity(tableName = "friend_requests")
data class FriendRequestEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String,
    val receiverId: String,
    val status: String = "PENDING" // "PENDING", "ACCEPTED", "REJECTED"
)

@Entity(tableName = "group_members")
data class GroupMemberEntity(
    @PrimaryKey val id: String, // Composite e.g. chatId + "_" + userId
    val chatId: String,
    val userId: String,
    val role: String = "MEMBER" // "OWNER", "ADMIN", "MEMBER"
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val themeMode: String = "DARK", // "DARK", "LIGHT"
    val notificationsEnabled: Boolean = true,
    val fontSize: Int = 14, // 12, 14, 16, 18, 20
    val language: String = "en",
    val chatWallpaper: String? = null,
    val blockUnknownSenders: Boolean = false
)
