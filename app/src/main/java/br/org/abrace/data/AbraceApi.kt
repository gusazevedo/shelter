package br.org.abrace.data

import android.net.Uri
import br.org.abrace.BuildConfig
import br.org.abrace.domain.Snapshot
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ApiException(val status: Int, message: String) : IOException(message)

class AbraceApi(private val store: SessionStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    private val refreshLock = Mutex()
    private var session: JsonObject? =
        store.read()?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val configured
        get() =
            BuildConfig.SUPABASE_URL.startsWith("https://") &&
                BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val signedIn
        get() = session != null

    val needsPassword
        get() = session?.get("password_pending")?.jsonPrimitive?.booleanOrNull ?: false

    private val base
        get() = BuildConfig.SUPABASE_URL.trimEnd('/')

    private val media = "application/json; charset=utf-8".toMediaType()

    private fun persist(data: JsonObject, pending: Boolean = needsPassword) {
        session = buildJsonObject {
            data.forEach { (k, v) -> put(k, v) }
            put(
                "expires_at",
                Instant.now().epochSecond + (data["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600),
            )
            put("password_pending", pending)
        }
        store.write(session.toString())
    }

    fun forget() {
        session = null
        store.clear()
    }

    private suspend fun call(
        path: String,
        body: JsonElement? = null,
        method: String = "POST",
        token: String? = null,
        bytes: ByteArray? = null,
    ): JsonElement =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder().url(base + path).header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            token?.let { request.header("Authorization", "Bearer $it") }
            val content =
                bytes?.toRequestBody("image/jpeg".toMediaType())
                    ?: (body ?: buildJsonObject {}).toString().toRequestBody(media)
            if (method == "GET") request.get() else request.method(method, content)
            http.newCall(request.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val result = runCatching { json.parseToJsonElement(raw) }.getOrDefault(JsonNull)
                if (!response.isSuccessful) {
                    val obj = result as? JsonObject
                    val message =
                        listOf("message", "error_description", "msg", "error")
                            .firstNotNullOfOrNull { key ->
                                (obj?.get(key) as? JsonPrimitive)?.contentOrNull
                            }
                    throw ApiException(
                        response.code,
                        when {
                            response.code == 401 -> "Sua sessão expirou. Entre novamente."
                            message == "Invalid login credentials" -> "E-mail ou senha incorretos."
                            message?.contains("duplicate key") == true ->
                                "Já existe um cadastro com estes dados."
                            message?.contains("violates check constraint") == true ->
                                "Confira os campos obrigatórios e os valores informados."
                            else ->
                                message ?: "Não foi possível concluir a operação. Tente novamente."
                        },
                    )
                }
                result
            }
        }

    private suspend fun token(): String =
        refreshLock.withLock {
            val saved = session ?: throw ApiException(401, "Entre para acessar o Abrace.")
            val expires = saved["expires_at"]?.jsonPrimitive?.longOrNull ?: 0
            if (expires < Instant.now().epochSecond + 60) {
                try {
                    val result =
                        call(
                                "/auth/v1/token?grant_type=refresh_token",
                                buildJsonObject { put("refresh_token", saved["refresh_token"]!!) },
                            )
                            .jsonObject
                    persist(result)
                } catch (e: ApiException) {
                    if (e.status in listOf(400, 401, 403)) forget()
                    throw e
                }
            }
            session?.get("access_token")?.jsonPrimitive?.content
                ?: throw ApiException(401, "Entre novamente.")
        }

    private suspend fun authenticated(
        path: String,
        body: JsonElement? = null,
        method: String = "POST",
        bytes: ByteArray? = null,
    ): JsonElement = call(path, body, method, token(), bytes)

    suspend fun login(email: String, password: String) {
        val result =
            call(
                    "/auth/v1/token?grant_type=password",
                    buildJsonObject {
                        put("email", email.trim())
                        put("password", password)
                    },
                )
                .jsonObject
        persist(result, false)
    }

    suspend fun recover(email: String) {
        call(
            "/auth/v1/recover?redirect_to=${Uri.encode("abrace://auth")}",
            buildJsonObject { put("email", email.trim()) },
        )
    }

    suspend fun acceptLink(uri: Uri) {
        require(uri.scheme == "abrace" && uri.host == "auth") { "Link de acesso inválido." }
        val params =
            Uri.parse("https://local.invalid/?${uri.fragment ?: uri.encodedQuery.orEmpty()}")
        params.getQueryParameter("error_description")?.let {
            throw IOException("O link expirou ou já foi usado. Solicite um novo link.")
        }
        val access =
            params.getQueryParameter("access_token")
                ?: throw IOException("Link inválido ou expirado.")
        val refresh =
            params.getQueryParameter("refresh_token")
                ?: throw IOException("Link inválido ou expirado.")
        val type = params.getQueryParameter("type")
        require(type in listOf("invite", "recovery")) { "Tipo de link inválido." }
        call(
            "/auth/v1/user",
            method = "GET",
            token = access,
        ) // Validate against our own Auth server before storing.
        persist(
            buildJsonObject {
                put("access_token", access)
                put("refresh_token", refresh)
                put("expires_in", params.getQueryParameter("expires_in")?.toLongOrNull() ?: 3600)
            },
            true,
        )
    }

    suspend fun setPassword(password: String) {
        require(password.length >= 10) { "Use uma senha com pelo menos 10 caracteres." }
        authenticated("/auth/v1/user", buildJsonObject { put("password", password) }, "PUT")
        session = session?.let { JsonObject(it + ("password_pending" to JsonPrimitive(false))) }
        store.write(session.toString())
    }

    suspend fun logout() {
        try {
            if (signedIn) authenticated("/auth/v1/logout")
        } finally {
            forget()
        }
    }

    suspend fun rpc(name: String, args: JsonObject = buildJsonObject {}): JsonElement =
        authenticated("/rest/v1/rpc/$name", args)

    suspend fun snapshot(): Snapshot {
        val data = rpc("get_snapshot").jsonObject
        val snapshot = Snapshot(data)
        val paths =
            snapshot
                .records("animals")
                .filterNot { it.archived }
                .flatMap { it.strings("photo_paths") }
                .distinct()
        val urls =
            if (paths.isEmpty()) emptyMap()
            else
                runCatching {
                        val result =
                            authenticated(
                                    "/storage/v1/object/sign/animal-photos",
                                    buildJsonObject {
                                        put("expiresIn", 3600)
                                        put("paths", JsonArray(paths.map(::JsonPrimitive)))
                                    },
                                )
                                .jsonArray
                        result
                            .mapNotNull { item ->
                                val obj = item.jsonObject
                                val path =
                                    obj["path"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                val signed =
                                    (obj["signedURL"] ?: obj["signedUrl"])
                                        ?.jsonPrimitive
                                        ?.contentOrNull ?: return@mapNotNull null
                                path to
                                    if (signed.startsWith("https://")) signed
                                    else "$base/storage/v1$signed"
                            }
                            .toMap()
                    }
                    .getOrDefault(emptyMap())
        return snapshot.copy(photoUrls = urls)
    }

    suspend fun invite(data: JsonObject) {
        authenticated("/functions/v1/invite-volunteer", data)
    }

    suspend fun uploadPhoto(animalId: String, bytes: ByteArray): String {
        val path = "$animalId/${UUID.randomUUID()}.jpg"
        authenticated("/storage/v1/object/animal-photos/$path", bytes = bytes)
        return path
    }
}
