package com.example.demo.features.notifications.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.MessagingErrorCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

enum class PushDeliveryResult {
    SUCCESS,
    INVALID_TOKEN,
    FAILED,
}

@Component
class FirebasePushGateway(
    @Value("\${app.push.enabled:true}")
    private val pushEnabled: Boolean,
    @Value("\${app.push.firebase.service-account-json:}")
    private val serviceAccountJson: String,
    @Value("\${app.push.firebase.service-account-path:}")
    private val serviceAccountPath: String,
) {
    private val log = LoggerFactory.getLogger(FirebasePushGateway::class.java)

    private val messaging: FirebaseMessaging? by lazy {
        if (!pushEnabled) {
            log.info("Push gateway disabled by config app.push.enabled=false")
            return@lazy null
        }

        val stream = resolveServiceAccountStream()
        if (stream == null) {
            log.warn("Push gateway is enabled, but Firebase service account is not configured")
            return@lazy null
        }

        return@lazy stream.use { input ->
            runCatching {
                val appName = "pgk-food-backend"
                val app = FirebaseApp.getApps().firstOrNull { it.name == appName }
                    ?: FirebaseApp.initializeApp(
                        FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(input))
                            .build(),
                        appName
                    )
                FirebaseMessaging.getInstance(app)
            }.onFailure {
                log.error("Failed to initialize Firebase messaging gateway", it)
            }.getOrNull()
        }
    }

    fun sendToToken(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
    ): PushDeliveryResult {
        val firebase = messaging ?: return PushDeliveryResult.FAILED

        val message = Message.builder()
            .setToken(token)
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(
                        AndroidNotification.builder()
                            .setChannelId("pgk_food_general")
                            .build()
                    )
                    .build()
            )
            .putAllData(data)
            .build()

        return try {
            firebase.send(message)
            PushDeliveryResult.SUCCESS
        } catch (e: FirebaseMessagingException) {
            if (e.messagingErrorCode == MessagingErrorCode.UNREGISTERED ||
                e.messagingErrorCode == MessagingErrorCode.INVALID_ARGUMENT
            ) {
                PushDeliveryResult.INVALID_TOKEN
            } else {
                log.warn(
                    "Firebase push send failed: code={}, message={}",
                    e.messagingErrorCode,
                    e.message
                )
                PushDeliveryResult.FAILED
            }
        } catch (e: Exception) {
            log.warn("Unexpected push send failure", e)
            PushDeliveryResult.FAILED
        }
    }

    private fun resolveServiceAccountStream(): InputStream? {
        if (serviceAccountJson.isNotBlank()) {
            val normalized = serviceAccountJson.trim().replace("\\n", "\n")
            return ByteArrayInputStream(normalized.toByteArray(StandardCharsets.UTF_8))
        }

        if (serviceAccountPath.isBlank()) return null
        val file = File(serviceAccountPath)
        if (!file.exists() || !file.isFile) return null
        return file.inputStream()
    }
}
