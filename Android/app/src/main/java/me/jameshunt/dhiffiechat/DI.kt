package me.jameshunt.dhiffiechat

import android.content.Context
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import me.jameshunt.dhiffiechat.crypto.toBase64String
import me.jameshunt.dhiffiechat.crypto.toDHPublicKey
import me.jameshunt.dhiffiechat.service.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.URL
import java.security.PublicKey
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class DI(val application: DhiffieChatApp) {
    private val fileLocationUtil = FileLocationUtil(application)
    private val prefManager = PrefManager(
        prefs = application.getSharedPreferences("dhiffieChat", Context.MODE_PRIVATE)
    )

    private val dbQueryManager = DBQueryManager(application, prefManager)

    private val moshi: Moshi = Moshi.Builder()
        .add(object {
            @ToJson
            fun toJson(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant)
            @FromJson
            fun fromJson(string: String): Instant = Instant.parse(string)
        })
        .add(object {
            @ToJson
            fun toJson(url: URL): String = url.toString()
            @FromJson
            fun fromJson(string: String): URL = URL(string)
        })
        .add(object {
            @ToJson
            fun toJson(publicKey: PublicKey): String = publicKey.toBase64String()
            @FromJson
            fun fromJson(base64: String): PublicKey = base64.toDHPublicKey() // TODO: re-evaluate, cause rsa keys now too
        })
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val identityManager = IdentityManager(dbQueryManager.getEncryptionKeyQueries())
    private val authManager = ServerAuthManager(identityManager, moshi)
    private val headerInterceptor = HeaderInterceptor(identityManager, authManager)

    private val okhttp = OkHttpClient
        .Builder()
//        .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        .addInterceptor(headerInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .client(okhttp)
        .baseUrl(BuildConfig.BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
        .build()

    private val api = retrofit.create(LambdaApi::class.java)
    private val userService = UserService(
        dbQueryManager.getAliasQueries(), api, authManager, identityManager, prefManager
    )
    private val ephemeralKeySyncService =
        EphemeralKeySyncService(api, dbQueryManager.getEncryptionKeyQueries(), identityManager)

    private val initService = InitService(api, userService, ephemeralKeySyncService)

    private val messageService =
        MessageService(identityManager, okhttp, api, userService, fileLocationUtil, dbQueryManager.getEncryptionKeyQueries())

    private val injectableComponents = mutableMapOf<String, Any>()

    init {
        register(moshi, messageService, userService, fileLocationUtil, initService)
    }

    private fun register(vararg entry: Any) {
        entry.forEach {
            injectableComponents[it::class.java.canonicalName!!] = it
        }
    }

    fun <T> createInjected(classToInject: Class<T>): T {
        val constructor = classToInject.constructors.first()
        val args = constructor.parameters.map {
            val canonicalName = it.type.canonicalName!!
            injectableComponents[canonicalName] ?: throw IllegalStateException(
                """
                $canonicalName has not been registered, 
                and cannot be injected into ${classToInject.canonicalName!!}
                """.trimIndent()
            )
        }

        @Suppress("UNCHECKED_CAST")
        return constructor.newInstance(*args.toTypedArray()) as T
    }
}
