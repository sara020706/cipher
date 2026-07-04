package com.example.data

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val RSA_ALGORITHM = "RSA"
    private const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
    private const val AES_ALGORITHM = "AES"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    // Generate a new 2048-bit RSA Key Pair
    fun generateRsaKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance(RSA_ALGORITHM)
        keyGen.initialize(2048)
        return keyGen.generateKeyPair()
    }

    // Helper: Convert PublicKey to Base64 String
    fun publicKeyToString(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    // Helper: Convert PrivateKey to Base64 String
    fun privateKeyToString(privateKey: PrivateKey): String {
        return Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
    }

    // Helper: Reconstruct PublicKey from String
    fun stringToPublicKey(keyString: String): PublicKey {
        val keyBytes = Base64.decode(keyString, Base64.NO_WRAP)
        val spec = X509EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance(RSA_ALGORITHM)
        return kf.generatePublic(spec)
    }

    // Helper: Reconstruct PrivateKey from String
    fun stringToPrivateKey(keyString: String): PrivateKey {
        val keyBytes = Base64.decode(keyString, Base64.NO_WRAP)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance(RSA_ALGORITHM)
        return kf.generatePrivate(spec)
    }

    // Generate SHA-256 Fingerprint for verification (QR Code / Fingerprint view)
    fun getFingerprint(publicKeyString: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(publicKeyString.toByteArray(Charsets.UTF_8))
            hash.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            "ERROR-FINGERPRINT"
        }
    }

    // Hybrid Encryption: Encrypt plaintext using Recipient's RSA Public Key
    // Returns a compound string "Base64(encryptedAESKey):Base64(iv):Base64(encryptedPayload)"
    fun encrypt(plainText: String, recipientPublicKey: PublicKey): String {
        // 1. Generate random AES-256 key
        val keyGen = KeyGenerator.getInstance(AES_ALGORITHM)
        keyGen.init(256)
        val secretKey: SecretKey = keyGen.generateKey()

        // 2. Encrypt plaintext with AES-GCM
        val aesCipher = Cipher.getInstance(AES_TRANSFORMATION)
        aesCipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = aesCipher.iv
        val encryptedPayloadBytes = aesCipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // 3. Encrypt AES key with Recipient's RSA Public Key
        val rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION)
        rsaCipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey)
        val encryptedAesKeyBytes = rsaCipher.doFinal(secretKey.encoded)

        // 4. Base64 Encode everything
        val encAesKeyStr = Base64.encodeToString(encryptedAesKeyBytes, Base64.NO_WRAP)
        val ivStr = Base64.encodeToString(iv, Base64.NO_WRAP)
        val encPayloadStr = Base64.encodeToString(encryptedPayloadBytes, Base64.NO_WRAP)

        return "$encAesKeyStr:$ivStr:$encPayloadStr"
    }

    // Hybrid Decryption: Decrypt ciphertext using Recipient's RSA Private Key
    fun decrypt(compoundCipher: String, recipientPrivateKey: PrivateKey): String {
        val parts = compoundCipher.split(":")
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid compound cipher format. Must be encryptedKey:iv:payload")
        }
        val encAesKeyStr = parts[0]
        val ivStr = parts[1]
        val encPayloadStr = parts[2]

        // 1. Decrypt AES key with RSA Private Key
        val encAesKeyBytes = Base64.decode(encAesKeyStr, Base64.NO_WRAP)
        val rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION)
        rsaCipher.init(Cipher.DECRYPT_MODE, recipientPrivateKey)
        val aesKeyBytes = rsaCipher.doFinal(encAesKeyBytes)
        val secretKey = SecretKeySpec(aesKeyBytes, AES_ALGORITHM)

        // 2. Decrypt Payload with AES-GCM
        val iv = Base64.decode(ivStr, Base64.NO_WRAP)
        val encPayloadBytes = Base64.decode(encPayloadStr, Base64.NO_WRAP)
        val aesCipher = Cipher.getInstance(AES_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        aesCipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val plainTextBytes = aesCipher.doFinal(encPayloadBytes)

        return String(plainTextBytes, Charsets.UTF_8)
    }

    // ==================== Envelope Encryption (v2) ====================
    // Encrypts the content ONCE with a random AES-256 key, then RSA-wraps that
    // key separately for every recipient. This gives true multi-recipient E2EE
    // (groups, sender-readable history) with a single ciphertext copy — critical
    // for large payloads like Base64 media.
    //
    // Envelope JSON: {"v":2,"iv":"...","ct":"...","keys":{"<uid>":"<wrappedKey>", ...}}

    fun isEnvelope(payload: String): Boolean {
        return payload.startsWith("{") && payload.contains("\"keys\"")
    }

    fun encryptEnvelope(plainContent: String, recipientKeys: Map<String, PublicKey>): String {
        // 1. Generate random AES-256 key and encrypt content once
        val keyGen = KeyGenerator.getInstance(AES_ALGORITHM)
        keyGen.init(256)
        val secretKey: SecretKey = keyGen.generateKey()

        val aesCipher = Cipher.getInstance(AES_TRANSFORMATION)
        aesCipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = aesCipher.iv
        val cipherBytes = aesCipher.doFinal(plainContent.toByteArray(Charsets.UTF_8))

        // 2. Wrap the AES key for every recipient
        val keysObj = JSONObject()
        for ((uid, publicKey) in recipientKeys) {
            val rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION)
            rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val wrapped = rsaCipher.doFinal(secretKey.encoded)
            keysObj.put(uid, Base64.encodeToString(wrapped, Base64.NO_WRAP))
        }

        return JSONObject()
            .put("v", 2)
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("ct", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
            .put("keys", keysObj)
            .toString()
    }

    // Returns the decrypted content, or throws if this uid has no wrapped key
    fun decryptEnvelope(envelopeJson: String, myUid: String, privateKey: PrivateKey): String {
        val envelope = JSONObject(envelopeJson)
        val keysObj = envelope.getJSONObject("keys")
        if (!keysObj.has(myUid)) {
            throw IllegalArgumentException("No wrapped key for uid $myUid")
        }

        val rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION)
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
        val aesKeyBytes = rsaCipher.doFinal(Base64.decode(keysObj.getString(myUid), Base64.NO_WRAP))
        val secretKey = SecretKeySpec(aesKeyBytes, AES_ALGORITHM)

        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val cipherBytes = Base64.decode(envelope.getString("ct"), Base64.NO_WRAP)
        val aesCipher = Cipher.getInstance(AES_TRANSFORMATION)
        aesCipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(aesCipher.doFinal(cipherBytes), Charsets.UTF_8)
    }
}
