package com.pandora.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class ModelProviderMode { CODEX, CUSTOM }

data class CustomModelProvider(
    val baseUrl: String,
    val modelIds: List<String>,
) {
    val defaultModel: String get() = modelIds.first()
}

object ModelProviderSettings {
    private const val PREFERENCES = "pandora_model_provider"
    private const val MODE = "mode"
    private const val BASE_URL = "base_url"
    private const val MODEL_IDS = "model_ids"

    fun mode(context: Context): ModelProviderMode {
        val stored = preferences(context).getString(MODE, ModelProviderMode.CODEX.name)
        return runCatching { ModelProviderMode.valueOf(stored.orEmpty()) }
            .getOrDefault(ModelProviderMode.CODEX)
    }

    fun customProvider(context: Context): CustomModelProvider? {
        if (mode(context) != ModelProviderMode.CUSTOM) return null
        return savedCustomProvider(context)
    }

    /** Returns the saved OSS profile even while hosted Codex is active. */
    fun savedCustomProvider(context: Context): CustomModelProvider? {
        val baseUrl = preferences(context).getString(BASE_URL, null).orEmpty().trimEnd('/')
        val modelIds = parseModelIds(preferences(context).getString(MODEL_IDS, null).orEmpty())
        return if (isSupportedUrl(baseUrl) && modelIds.isNotEmpty()) {
            CustomModelProvider(baseUrl, modelIds)
        } else {
            null
        }
    }

    fun useCodex(context: Context) {
        preferences(context).edit().putString(MODE, ModelProviderMode.CODEX.name).apply()
    }

    fun useCustomProvider(context: Context, provider: CustomModelProvider) {
        require(isSupportedUrl(provider.baseUrl)) { "Use an HTTP or HTTPS provider URL." }
        require(provider.modelIds.isNotEmpty()) { "Add at least one model identifier." }
        preferences(context).edit()
            .putString(MODE, ModelProviderMode.CUSTOM.name)
            .putString(BASE_URL, provider.baseUrl.trimEnd('/'))
            .putString(MODEL_IDS, provider.modelIds.joinToString("\n"))
            .apply()
    }

    internal fun parseModelIds(value: String): List<String> = value
        .split(',', '\n')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

    internal fun isSupportedUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

/** Keeps provider keys out of config files and ordinary SharedPreferences. */
object ModelProviderSecretStore {
    private const val PREFERENCES = "pandora_model_provider_secrets"
    private const val ENCRYPTED_API_KEY = "encrypted_api_key"
    private const val KEY_ALIAS = "pandora_model_provider_key"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"

    fun saveApiKey(context: Context, apiKey: String) {
        require(apiKey.isNotBlank()) { "API key cannot be empty." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(ENCRYPTED_API_KEY, Base64.encodeToString(payload, Base64.NO_WRAP))
            .apply()
    }

    fun apiKey(context: Context): String? = runCatching {
        val encoded = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(ENCRYPTED_API_KEY, null)
            ?: return null
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        if (payload.size <= 12) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            encryptionKey(),
            GCMParameterSpec(128, payload.copyOfRange(0, 12)),
        )
        cipher.doFinal(payload.copyOfRange(12, payload.size)).toString(Charsets.UTF_8)
    }.getOrNull()

    fun hasApiKey(context: Context): Boolean = apiKey(context) != null

    fun clearApiKey(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove(ENCRYPTED_API_KEY)
            .apply()
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }
}
