package br.org.abrace

import android.app.Application
import android.net.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.org.abrace.data.*
import br.org.abrace.domain.Snapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

data class AppState(
    val configured: Boolean = false,
    val online: Boolean = false,
    val signedIn: Boolean = false,
    val needsPassword: Boolean = false,
    val busy: Boolean = false,
    val snapshot: Snapshot? = null,
    val message: String? = null,
)

class AbraceViewModel(application: Application) : AndroidViewModel(application) {
    private val api = AbraceApi(SessionStore(application))
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)

    private fun connected(): Boolean =
        connectivity.activeNetwork?.let {
            connectivity
                .getNetworkCapabilities(it)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true

    private val mutable =
        MutableStateFlow(AppState(api.configured, connected(), api.signedIn, api.needsPassword))
    val state = mutable.asStateFlow()
    private var pendingLink: Uri? = null
    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) = connectionChanged()

            override fun onLost(network: Network) = connectionChanged()
        }

    init {
        connectivity.registerDefaultNetworkCallback(callback)
        if (mutable.value.online && api.signedIn && !api.needsPassword) refresh()
    }

    private fun connectionChanged() {
        val online = connected()
        val wasOnline = mutable.value.online
        mutable.update { it.copy(online = online, snapshot = if (online) it.snapshot else null) }
        if (online && !wasOnline) {
            val link = pendingLink
            if (link != null) {
                pendingLink = null
                acceptLink(link)
            } else if (api.signedIn && !api.needsPassword) refresh()
        }
    }

    private fun task(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        if (!connected()) {
            mutable.update {
                it.copy(
                    online = false,
                    snapshot = null,
                    message = "Conecte-se à internet para continuar.",
                )
            }
            return
        }
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (error is ApiException && error.status == 401) api.forget()
                mutable.update {
                    it.copy(
                        message =
                            when (error) {
                                is ApiException,
                                is IllegalArgumentException -> error.message
                                else ->
                                    "Não foi possível conectar ao Abrace. Verifique sua conexão e tente novamente."
                            },
                        snapshot =
                            if (
                                error is IllegalArgumentException ||
                                    (error is ApiException && error.status !in listOf(401, 403))
                            )
                                it.snapshot
                            else null,
                    )
                }
            } finally {
                mutable.update {
                    it.copy(
                        busy = false,
                        signedIn = api.signedIn,
                        needsPassword = api.needsPassword,
                        online = connected(),
                    )
                }
                pendingLink
                    ?.takeIf { connected() }
                    ?.let {
                        pendingLink = null
                        acceptLink(it)
                    }
            }
        }
    }

    private suspend fun load() {
        val result = api.snapshot()
        mutable.update { it.copy(snapshot = if (connected()) result else null) }
    }

    fun refresh() {
        if (api.configured && api.signedIn && !api.needsPassword) task { load() }
    }

    fun login(email: String, password: String) = task {
        api.login(email, password)
        load()
    }

    fun recover(email: String) = task {
        require(email.contains('@')) { "Informe seu e-mail." }
        api.recover(email)
        message("Se o e-mail estiver cadastrado, você receberá o link para criar uma nova senha.")
    }

    fun acceptLink(uri: Uri) {
        if (mutable.value.busy || !connected()) {
            pendingLink = uri
            return
        }
        task {
            api.acceptLink(uri)
            mutable.update { it.copy(snapshot = null, needsPassword = true) }
        }
    }

    fun setPassword(password: String, confirmation: String) = task {
        require(password == confirmation) { "As senhas precisam ser iguais." }
        api.setPassword(password)
        load()
    }

    fun logout() {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, snapshot = null) }
        viewModelScope.launch {
            try {
                api.logout()
            } catch (_: Exception) {
                api.forget()
            }
            mutable.update {
                AppState(api.configured, connected(), message = "Você saiu do Abrace.")
            }
        }
    }

    fun save(
        kind: String,
        data: JsonObject,
        photos: List<Uri> = emptyList(),
        done: (String) -> Unit,
    ) = task {
        val payload =
            if (kind == "animal" && photos.isNotEmpty()) {
                val id = data["id"]!!.jsonPrimitive.content
                val current = data["photo_paths"]?.jsonArray?.toList().orEmpty()
                require(current.size + photos.size <= 3) { "Cada animal pode ter até três fotos." }
                val paths = photos.map { api.uploadPhoto(id, compressPhoto(getApplication(), it)) }
                JsonObject(
                    data + ("photo_paths" to JsonArray(current + paths.map(::JsonPrimitive)))
                )
            } else data
        val result =
            api.rpc(
                "save_record",
                buildJsonObject {
                    put("p_kind", kind)
                    put("p_data", payload)
                },
            )
        val id = result.jsonPrimitive.content
        load()
        message("Cadastro salvo.")
        done(id)
    }

    fun rpc(
        name: String,
        args: JsonObject,
        success: String = "Registro salvo.",
        done: () -> Unit = {},
    ) = task {
        api.rpc(name, args)
        load()
        message(success)
        done()
    }

    fun invite(data: JsonObject, done: () -> Unit) = task {
        api.invite(data)
        load()
        message("Convite enviado por e-mail.")
        done()
    }

    fun message(value: String) {
        mutable.update { it.copy(message = value) }
    }

    fun consumeMessage() {
        mutable.update { it.copy(message = null) }
    }

    override fun onCleared() {
        connectivity.unregisterNetworkCallback(callback)
        super.onCleared()
    }
}
