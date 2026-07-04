package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY displayName ASC")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun getUserFlow(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE isMe = 1 LIMIT 1")
    suspend fun getMe(): UserEntity?

    @Query("SELECT * FROM users WHERE isMe = 1 LIMIT 1")
    fun getMeFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id != :myId AND isBlocked = 0")
    fun getFriendsFlow(myId: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE isBlocked = 1")
    fun getBlockedUsersFlow(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET onlineStatus = :status, lastSeen = :lastSeen WHERE id = :userId")
    suspend fun updateUserStatus(userId: String, status: String, lastSeen: Long)

    @Query("UPDATE users SET isBlocked = :blocked WHERE id = :userId")
    suspend fun updateUserBlockedStatus(userId: String, blocked: Boolean)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY pinned DESC, lastMessageTime DESC")
    fun getAllChatsFlow(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE archived = 1 ORDER BY lastMessageTime DESC")
    fun getArchivedChatsFlow(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE archived = 0 ORDER BY pinned DESC, lastMessageTime DESC")
    fun getActiveChatsFlow(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    fun getChatFlow(chatId: String): Flow<ChatEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query("UPDATE chats SET pinned = :pinned WHERE id = :chatId")
    suspend fun setChatPinned(chatId: String, pinned: Boolean)

    @Query("UPDATE chats SET archived = :archived WHERE id = :chatId")
    suspend fun setChatArchived(chatId: String, archived: Boolean)

    @Query("UPDATE chats SET muted = :muted WHERE id = :chatId")
    suspend fun setChatMuted(chatId: String, muted: Boolean)

    @Query("UPDATE chats SET wallpaper = :wallpaper WHERE id = :chatId")
    suspend fun setChatWallpaper(chatId: String, wallpaper: String?)

    @Query("UPDATE chats SET unreadCount = :unreadCount WHERE id = :chatId")
    suspend fun setChatUnreadCount(chatId: String, unreadCount: Int)

    @Query("UPDATE chats SET unreadCount = unreadCount + 1 WHERE id = :chatId")
    suspend fun incrementUnreadCount(chatId: String)

    @Query("UPDATE chats SET lastMessageText = :text, lastMessageTime = :time WHERE id = :chatId")
    suspend fun updateLastMessage(chatId: String, text: String, time: Long)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isDeletedForMe = 0 ORDER BY timestamp ASC")
    fun getMessagesForChatFlow(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesForChatOnce(chatId: String): List<MessageEntity>

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("SELECT * FROM messages WHERE isStarred = 1 AND isDeletedForMe = 0 ORDER BY timestamp DESC")
    fun getStarredMessagesFlow(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET isStarred = :starred WHERE id = :messageId")
    suspend fun setMessageStarred(messageId: String, starred: Boolean)

    @Query("UPDATE messages SET isDeletedForEveryone = 1, encryptedPayload = '' WHERE id = :messageId")
    suspend fun deleteMessageForEveryone(messageId: String)

    @Query("UPDATE messages SET isDeletedForMe = 1 WHERE id = :messageId")
    suspend fun deleteMessageForMe(messageId: String)

    @Query("UPDATE messages SET reactions = :reactionsJson WHERE id = :messageId")
    suspend fun updateMessageReactions(messageId: String, reactionsJson: String)

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND (encryptedPayload LIKE :query OR id IN (SELECT id FROM messages WHERE chatId = :chatId))")
    suspend fun searchMessagesInChat(chatId: String, query: String): List<MessageEntity>
}

@Dao
interface FriendRequestDao {
    @Query("SELECT * FROM friend_requests WHERE status = 'PENDING' ORDER BY id DESC")
    fun getPendingRequestsFlow(): Flow<List<FriendRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: FriendRequestEntity)

    @Query("UPDATE friend_requests SET status = :status WHERE id = :requestId")
    suspend fun updateRequestStatus(requestId: String, status: String)

    @Query("DELETE FROM friend_requests WHERE id = :requestId")
    suspend fun deleteRequest(requestId: String)
}

@Dao
interface GroupMemberDao {
    @Query("SELECT * FROM group_members WHERE chatId = :chatId")
    fun getMembersForGroupFlow(chatId: String): Flow<List<GroupMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity)

    @Query("DELETE FROM group_members WHERE chatId = :chatId AND userId = :userId")
    suspend fun removeMember(chatId: String, userId: String)

    @Query("DELETE FROM group_members WHERE chatId = :chatId")
    suspend fun removeAllMembers(chatId: String)
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettingsEntity)
}
