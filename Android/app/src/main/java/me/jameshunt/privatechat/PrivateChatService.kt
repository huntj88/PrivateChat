package me.jameshunt.privatechat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import me.jameshunt.privatechat.PrivateChatApi.*
import me.jameshunt.privatechat.crypto.AESCrypto
import me.jameshunt.privatechat.crypto.toBase64String
import me.jameshunt.privatechat.crypto.toPublicKey
import me.jameshunt.privatechat.crypto.toUserId
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.http.*
import java.security.PublicKey
import javax.crypto.spec.IvParameterSpec


class PrivateChatService(private val api: PrivateChatApi, private val authManager: AuthManager) {

    suspend fun getNewMessages(): List<Unit> {
        val message = createIdentity().message
        Log.d("createIdentityMessage", message)
        val userPublicKey = getUserPublicKey(authManager.getIdentity().toUserId())
        Log.d("user public key", userPublicKey.toUserId())
        return emptyList()
    }

    suspend fun scanQR(scannedUserId: String): ResponseMessage {
        return api.scanQR(
            headers = standardHeaders(),
            qr = QR(scannedUserId = scannedUserId)
        )
    }

    suspend fun sendFile(recipientUserId: String, image: ByteArray): ResponseMessage {
        val recipientPublicKey =
            api.getUserPublicKey(standardHeaders(), recipientUserId).publicKey.toPublicKey()
        val userToUserCredentials = authManager.userToUserMessage(recipientPublicKey)

        val encryptedImage = AESCrypto.encrypt(image, userToUserCredentials.sharedSecret, userToUserCredentials.iv)
        val contentType = "application/octet-stream".toMediaTypeOrNull()

        return api.sendFile(
            headers = standardHeaders(),
            encryptedFile = encryptedImage.toRequestBody(contentType, 0, encryptedImage.size),
            recipientUserId = recipientUserId,
            iv = userToUserCredentials.iv.toBase64String()
        )
    }

    suspend fun getFile(senderUserId: String, fileKey: String, userUserIv: IvParameterSpec): Bitmap {
        val otherUserPublicKey = api.getUserPublicKey(standardHeaders(), senderUserId).publicKey.toPublicKey()
        val userToUserCredentials = authManager.userToUserMessage(otherUserPublicKey)

        val encryptedBody = api.getFile(standardHeaders(), fileKey).bytes()
        val file = AESCrypto.decrypt(encryptedBody, userToUserCredentials.sharedSecret, userUserIv)
        return BitmapFactory.decodeByteArray(file, 0, file.size)
    }

    private suspend fun createIdentity(): ResponseMessage {
        val userToServerCredentials = authManager.userToServerAuth(serverPublicKey = getServerPublicKey())

        return api.createIdentity(
            CreateIdentity(
                publicKey = authManager.getIdentity().public.toBase64String(),
                iv = userToServerCredentials.iv.toBase64String(),
                encryptedToken = userToServerCredentials.encryptedToken
            )
        )
    }

    // TODO: caching
    private suspend fun getServerPublicKey(): PublicKey {
        return api.getServerPublicKey().publicKey.toPublicKey()
    }

    private suspend fun getUserPublicKey(userId: String): PublicKey {
        return api.getUserPublicKey(headers = standardHeaders(), userId = userId).publicKey.toPublicKey()
    }

    private suspend fun standardHeaders(vararg additionalHeaders: Map<String, String>): Map<String, String> {
        val standard = mapOf("userId" to authManager.getIdentity().toUserId()) + userToServerHeaders()
        return additionalHeaders.fold(standard) { acc, next -> acc + next }
    }

    private suspend fun userToServerHeaders(): Map<String, String> {
        val userToServerCredentials = authManager.userToServerAuth(getServerPublicKey())
        return mapOf(
            "userServerIv" to userToServerCredentials.iv.toBase64String(),
            "userServerEncryptedToken" to userToServerCredentials.encryptedToken
        )
    }
}

interface PrivateChatApi {
    data class PublicKeyResponse(val publicKey: String)

    @GET("ServerPublicKey")
    suspend fun getServerPublicKey(): PublicKeyResponse

    @GET("UserPublicKey")
    suspend fun getUserPublicKey(
        @HeaderMap headers: Map<String, String>,
        @Query("userId") userId: String
    ): PublicKeyResponse

    data class CreateIdentity(
        val publicKey: String,
        val iv: String,
        val encryptedToken: String
    )

    data class ResponseMessage(val message: String)

    @POST("CreateIdentity")
    suspend fun createIdentity(@Body identity: CreateIdentity): ResponseMessage

    data class QR(val scannedUserId: String)

    @POST("ScanQR")
    suspend fun scanQR(@HeaderMap headers: Map<String, String>, @Body qr: QR): ResponseMessage

    @POST("SendFile")
    suspend fun sendFile(
        @HeaderMap headers: Map<String, String>,
        @Body encryptedFile: RequestBody,
        @Query("userId") recipientUserId: String,
        @Query("userUserIv") iv: String
    ): ResponseMessage

    @GET("GetFile")
    suspend fun getFile(
        @HeaderMap headers: Map<String, String>,
        @Query("fileKey") fileKey: String
    ): ResponseBody
}