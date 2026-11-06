@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package br.org.abrace.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.org.abrace.AbraceViewModel
import br.org.abrace.AppState
import br.org.abrace.domain.Snapshot
import kotlinx.serialization.json.*

enum class MainTab(val label: String) {
    HOME("Início"),
    ANIMALS("Animais"),
    ADOPTIONS("Adoções"),
    VOLUNTEERS("Voluntários"),
}

data class Detail(val kind: String, val id: String, val section: Int = 0)

data class PendingAction(
    val title: String,
    val explanation: String,
    val rpc: String,
    val args: JsonObject,
    val success: String,
    val pop: Boolean = false,
)

@Composable
fun AbraceApp(vm: AbraceViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snack.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    AbraceTheme {
        Surface(Modifier.fillMaxSize()) {
            Box {
                when {
                    !state.configured ->
                        StatusScreen(
                            Icons.Outlined.Pets,
                            "Abrace está sendo preparado",
                            "O acesso será liberado quando a configuração inicial estiver concluída.",
                        )
                    !state.online ->
                        StatusScreen(
                            Icons.Outlined.CloudOff,
                            "Precisamos de internet",
                            "Conecte seu celular para acessar os dados compartilhados da Abrace.",
                        )
                    state.needsPassword -> PasswordScreen(state, vm)
                    !state.signedIn -> LoginScreen(state, vm)
                    state.snapshot == null ->
                        StatusScreen(
                            Icons.Outlined.CloudQueue,
                            if (state.busy) "Carregando o Abrace…"
                            else "Não foi possível carregar os dados",
                            if (state.busy) "Aguarde um instante."
                            else
                                "Tente novamente. Se seu acesso foi desativado, fale com um administrador.",
                            loading = state.busy,
                            action = if (state.busy) null else ({ vm.refresh() }),
                            secondary = { vm.logout() },
                        )
                    else -> Workspace(state, state.snapshot!!, vm)
                }
                SnackbarHost(
                    snack,
                    Modifier.align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(state: AppState, vm: AbraceViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                Icons.Outlined.Pets,
                null,
                Modifier.padding(22.dp).size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text("Abrace", style = MaterialTheme.typography.displayMedium)
        Text(
            "Cada cuidado faz parte de uma nova história.",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "Entre com o acesso recebido por convite.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            enabled = !state.busy,
            label = { Text("E-mail") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            enabled = !state.busy,
            label = { Text("Senha") },
            singleLine = true,
            visualTransformation =
                if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        if (visible) "Ocultar senha" else "Mostrar senha",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Button(
            onClick = { vm.login(email, password) },
            enabled = !state.busy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("Entrar")
        }
        TextButton(
            onClick = { vm.recover(email) },
            enabled = !state.busy,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Esqueci minha senha")
        }
    }
}

@Composable
private fun PasswordScreen(state: AppState, vm: AbraceViewModel) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Icon(
            Icons.Outlined.Lock,
            null,
            Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text("Crie sua senha", style = MaterialTheme.typography.headlineLarge)
        Text("Use pelo menos 10 caracteres para acessar o Abrace.")
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            enabled = !state.busy,
            label = { Text("Nova senha") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        OutlinedTextField(
            value = confirmation,
            onValueChange = { confirmation = it },
            enabled = !state.busy,
            label = { Text("Repita a senha") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Button(
            onClick = { vm.setPassword(password, confirmation) },
            enabled = !state.busy && password.length >= 10 && confirmation == password,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.busy) "Salvando…" else "Salvar senha e entrar")
        }
        TextButton(onClick = { vm.logout() }, enabled = !state.busy) { Text("Cancelar") }
    }
}

@Composable
private fun Workspace(state: AppState, s: Snapshot, vm: AbraceViewModel) {
    var tab by remember { mutableStateOf(MainTab.HOME) }
    val stack = remember { mutableStateListOf<Detail>() }
    val forms = remember { mutableStateListOf<FormRequest>() }
    var adoptionSection by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<PendingAction?>(null) }
    val detail = stack.lastOrNull()
    fun openForm(kind: FormKind, target: String = "", record: br.org.abrace.domain.Record? = null) {
        forms.add(FormRequest(kind, target, record, s))
    }
    BackHandler(enabled = stack.isNotEmpty() || forms.isNotEmpty()) {
        if (!state.busy) {
            if (forms.isNotEmpty()) forms.removeAt(forms.lastIndex)
            else stack.removeAt(stack.lastIndex)
        }
    }
    val currentForm = forms.lastOrNull()
    if (currentForm != null) {
        FormScreen(
            currentForm,
            s,
            state.busy,
            vm,
            onBack = { forms.removeAt(forms.lastIndex) },
            onSaved = { id ->
                forms.removeAt(forms.lastIndex)
                if (
                    currentForm.kind == FormKind.ADOPTER &&
                        forms.lastOrNull()?.kind == FormKind.ADOPTION
                )
                    forms.last().values["adopter_id"] = id
                else if (
                    currentForm.kind in listOf(FormKind.ANIMAL, FormKind.ADOPTER) && id.isNotBlank()
                ) {
                    val next =
                        Detail(if (currentForm.kind == FormKind.ANIMAL) "animal" else "adopter", id)
                    if (stack.lastOrNull() != next) stack.add(next)
                }
            },
            newAdopter = { openForm(FormKind.ADOPTER) },
        )
        return
    }
    val detailRecord = detail?.let { s.find(tableFor(it.kind), it.id) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (detail == null) if (tab == MainTab.HOME) "Abrace" else tab.label
                            else
                                when (detail.kind) {
                                    "animal" -> detailRecord?.text("name")
                                    "adopter",
                                    "volunteer" -> detailRecord?.text("full_name")
                                    "adoption" -> "Adoção"
                                    else -> "Visita"
                                }.orEmpty(),
                            maxLines = 1,
                        )
                        if (detail == null)
                            Text(
                                s.role.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                    }
                },
                navigationIcon = {
                    if (detail != null)
                        IconButton(
                            onClick = { stack.removeAt(stack.lastIndex) },
                            enabled = !state.busy,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                        }
                },
                actions = {
                    if (
                        detail != null &&
                            detailRecord != null &&
                            !detailRecord.archived &&
                            ((detail.kind in listOf("animal", "adopter") && s.canEdit) ||
                                (detail.kind == "volunteer" && s.isAdmin))
                    )
                        IconButton(
                            enabled = !state.busy,
                            onClick = {
                                openForm(
                                    when (detail.kind) {
                                        "animal" -> FormKind.ANIMAL
                                        "adopter" -> FormKind.ADOPTER
                                        else -> FormKind.VOLUNTEER
                                    },
                                    record = detailRecord,
                                )
                            },
                        ) {
                            Icon(Icons.Outlined.Edit, "Editar cadastro")
                        }
                    IconButton(onClick = vm::refresh, enabled = !state.busy) {
                        Icon(Icons.Outlined.Refresh, "Atualizar dados")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Outlined.MoreVert, "Mais opções")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            Text(
                                s.me?.text("full_name").orEmpty(),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            if (
                                s.isAdmin &&
                                    detail != null &&
                                    detailRecord != null &&
                                    detail.kind in listOf("animal", "adopter", "volunteer")
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (detailRecord.archived) "Restaurar cadastro"
                                            else if (detail.kind == "volunteer")
                                                "Desativar voluntário"
                                            else "Arquivar cadastro"
                                        )
                                    },
                                    onClick = {
                                        menu = false
                                        confirmation =
                                            PendingAction(
                                                if (detailRecord.archived) "Restaurar cadastro?"
                                                else "Arquivar cadastro?",
                                                "O histórico será preservado.",
                                                if (detailRecord.archived) "restore_record"
                                                else "archive_record",
                                                buildJsonObject {
                                                    put("p_kind", detail.kind)
                                                    put("p_id", detail.id)
                                                },
                                                "Cadastro atualizado.",
                                                true,
                                            )
                                    },
                                )
                                if (!detailRecord.archived && detail.kind != "volunteer")
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Excluir cadastro sem histórico",
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            menu = false
                                            confirmation =
                                                PendingAction(
                                                    "Excluir definitivamente?",
                                                    "Use apenas para um cadastro feito por engano e sem histórico vinculado. Esta ação não pode ser desfeita.",
                                                    "archive_record",
                                                    buildJsonObject {
                                                        put("p_kind", detail.kind)
                                                        put("p_id", detail.id)
                                                        put("p_permanent", true)
                                                    },
                                                    "Cadastro excluído.",
                                                    true,
                                                )
                                        },
                                    )
                            }
                            DropdownMenuItem(
                                text = { Text("Sair") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                                onClick = {
                                    menu = false
                                    vm.logout()
                                },
                                enabled = !state.busy,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = {
                            tab = item
                            stack.clear()
                        },
                        icon = {
                            Icon(
                                when (item) {
                                    MainTab.HOME -> Icons.Outlined.Home
                                    MainTab.ANIMALS -> Icons.Outlined.Pets
                                    MainTab.ADOPTIONS -> Icons.Outlined.FavoriteBorder
                                    MainTab.VOLUNTEERS -> Icons.Outlined.Groups
                                },
                                null,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (
                detail == null &&
                    ((tab in listOf(MainTab.ANIMALS, MainTab.ADOPTIONS) && s.canEdit) ||
                        (tab == MainTab.VOLUNTEERS && s.isAdmin))
            )
                ExtendedFloatingActionButton(
                    onClick = {
                        when (tab) {
                            MainTab.ANIMALS -> openForm(FormKind.ANIMAL)
                            MainTab.ADOPTIONS ->
                                openForm(
                                    if (adoptionSection == 0) FormKind.ADOPTION
                                    else FormKind.ADOPTER
                                )
                            MainTab.VOLUNTEERS -> openForm(FormKind.INVITE)
                            else -> Unit
                        }
                    },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = {
                        Text(
                            when (tab) {
                                MainTab.ANIMALS -> "Adicionar animal"
                                MainTab.ADOPTIONS ->
                                    if (adoptionSection == 0) "Registrar adoção"
                                    else "Novo adotante"
                                else -> "Convidar"
                            }
                        )
                    },
                )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (detail != null)
                DetailScreen(
                    detail,
                    s,
                    state.busy,
                    onOpen = { stack.add(it) },
                    onForm = { kind, target, record -> openForm(kind, target, record) },
                    onAction = { confirmation = it },
                )
            else
                when (tab) {
                    MainTab.HOME -> HomeScreen(s, onOpen = { stack.add(it) })
                    MainTab.ANIMALS ->
                        AnimalsScreen(s, onOpen = { stack.add(Detail("animal", it)) })
                    MainTab.ADOPTIONS ->
                        AdoptionsScreen(
                            s,
                            adoptionSection,
                            onSection = { adoptionSection = it },
                            onOpen = { stack.add(it) },
                        )
                    MainTab.VOLUNTEERS ->
                        VolunteersScreen(s, onOpen = { stack.add(Detail("volunteer", it)) })
                }
        }
    }
    confirmation?.let { action ->
        AlertDialog(
            onDismissRequest = { if (!state.busy) confirmation = null },
            title = { Text(action.title) },
            text = { Text(action.explanation) },
            confirmButton = {
                TextButton(
                    enabled = !state.busy,
                    onClick = {
                        vm.rpc(action.rpc, action.args, action.success) {
                            confirmation = null
                            if (action.pop) stack.clear()
                        }
                    },
                ) {
                    Text(if (state.busy) "Salvando…" else "Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }, enabled = !state.busy) {
                    Text("Cancelar")
                }
            },
        )
    }
}

fun tableFor(kind: String) =
    when (kind) {
        "animal" -> "animals"
        "adopter" -> "adopters"
        "adoption" -> "adoptions"
        "volunteer" -> "volunteers"
        else -> "visits"
    }

@Composable
fun StatusScreen(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    loading: Boolean = false,
    action: (() -> Unit)? = null,
    secondary: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
        if (loading) {
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator()
        }
        action?.let {
            Spacer(Modifier.height(20.dp))
            Button(onClick = it) { Text("Tentar novamente") }
        }
        secondary?.let { TextButton(onClick = it) { Text("Sair") } }
    }
}
