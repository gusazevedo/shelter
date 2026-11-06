@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package br.org.abrace.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import br.org.abrace.AbraceViewModel
import br.org.abrace.domain.*
import coil3.compose.AsyncImage
import java.io.File
import java.time.*
import java.util.UUID
import kotlinx.serialization.json.*

enum class FormKind(val title: String) {
    ANIMAL("Animal"),
    ADOPTER("Adotante"),
    VACCINE("Registrar vacina"),
    TREATMENT("Novo tratamento"),
    DOSE("Registrar aplicação"),
    INVITE("Convidar voluntário"),
    VOLUNTEER("Editar voluntário"),
    ADOPTION("Registrar adoção"),
    VISIT("Agendar visita"),
    VISIT_RESULT("Registrar visita"),
    CLOSE("Encerrar acompanhamento"),
}

class FormRequest(
    val kind: FormKind,
    val target: String = "",
    record: Record? = null,
    snapshot: Snapshot,
) {
    val key = UUID.randomUUID().toString()
    val values = mutableStateMapOf<String, String>()
    val photoPaths = mutableStateListOf<String>()
    val newPhotos = mutableStateListOf<Uri>()

    init {
        record?.values?.forEach { (key, value) ->
            values[key] =
                when (value) {
                    is JsonArray -> value.joinToString("|") { it.jsonPrimitive.content }
                    is JsonNull -> ""
                    else -> value.jsonPrimitive.content
                }
        }
        record?.strings("photo_paths")?.let(photoPaths::addAll)
        fun default(key: String, value: String) {
            if (key !in values) values[key] = value
        }
        default("id", record?.id ?: UUID.randomUUID().toString())
        val today = LocalDate.now().toString()
        when (kind) {
            FormKind.ANIMAL -> {
                default("species", "dog")
                default("sex", "unknown")
                default("status", "available")
                default("admitted_on", today)
                default("home_base_id", snapshot.records("bases").firstOrNull()?.id.orEmpty())
                listOf("neutered", "rabies_vaccinated", "leishmaniasis").forEach {
                    default(it, "unknown")
                }
            }
            FormKind.ADOPTER -> default("has_other_animals", "false")
            FormKind.VACCINE -> {
                default("animal_id", target)
                default("entry_mode", "applied")
                if (values["administered_on"].isNullOrBlank()) values["administered_on"] = today
            }
            FormKind.TREATMENT -> {
                default("animal_id", target)
                default("start_date", today)
                default("time_of_day", "09:00")
                default("schedule_type", "daily")
                default("finish_mode", "date")
                default("timezone", ZoneId.systemDefault().id)
            }
            FormKind.ADOPTION -> {
                default("adopted_on", today)
                if (target.isNotBlank()) default("animal_ids", target)
            }
            FormKind.VISIT -> {
                default("date", LocalDate.now().plusDays(1).toString())
                default("time", "09:00")
                default("visitor_id", snapshot.userId)
                snapshot.find("adoptions", target)?.let {
                    default("adopter_id", it.text("adopter_id"))
                    default("adoption_ids", it.id)
                }
            }
            FormKind.VISIT_RESULT,
            FormKind.DOSE -> {
                default("date", today)
                default("time", LocalTime.now().withSecond(0).withNano(0).toString())
            }
            FormKind.CLOSE -> {
                val ad = snapshot.find("adoptions", target)
                default(
                    "outcome",
                    if (ad != null && snapshot.ongoing(ad) && snapshot.completedVisits(target) == 3)
                        "adopted"
                    else "recollected",
                )
                default("returned_confirmed", "false")
            }
            else -> Unit
        }
    }
}

enum class FieldType {
    TEXT,
    NOTES,
    DATE,
    TIME,
    NUMBER,
    DECIMAL,
    EMAIL,
    PHONE,
    SELECT,
    MULTI,
    BOOLEAN,
}

data class Field(
    val key: String,
    val label: String,
    val type: FieldType = FieldType.TEXT,
    val required: Boolean = false,
    val options: Map<String, String> = emptyMap(),
)

private fun options(
    snapshot: Snapshot,
    table: String,
    include: (Record) -> Boolean = { !it.archived },
): Map<String, String> =
    snapshot.records(table).filter(include).associate {
        it.id to it.text(if (table in listOf("adopters", "volunteers")) "full_name" else "name")
    }

private fun fields(request: FormRequest, s: Snapshot): List<Field> {
    val v = request.values
    return when (request.kind) {
        FormKind.ANIMAL ->
            listOf(
                Field("name", "Nome", required = true),
                Field("species", "Espécie", FieldType.SELECT, true, speciesLabels),
                Field("sex", "Sexo", FieldType.SELECT, true, sexLabels),
                Field("estimated_birth_date", "Nascimento estimado", FieldType.DATE),
                Field("breed", "Raça"),
                Field("weight_kg", "Peso em kg", FieldType.DECIMAL),
                Field("color", "Cor"),
                Field("microchip", "Microchip"),
                Field("admitted_on", "Data de entrada", FieldType.DATE, true),
                Field(
                    "home_base_id",
                    "Base de acolhimento",
                    FieldType.SELECT,
                    true,
                    options(s, "bases"),
                ),
                Field(
                    "status",
                    "Situação",
                    FieldType.SELECT,
                    true,
                    if (
                        s.records("adoptions").any {
                            it.text("animal_id") == v["id"] && it.text("ended_at").isBlank()
                        }
                    )
                        statusLabels.filterKeys { it in listOf("adopted", "deceased") }
                    else statusLabels.filterKeys { it != "adopted" },
                ),
                Field("neutered", "Castrado", FieldType.SELECT, true, triStateLabels),
                Field(
                    "rabies_vaccinated",
                    "Vacinado contra raiva",
                    FieldType.SELECT,
                    true,
                    triStateLabels,
                ),
                Field("leishmaniasis", "Leishmaniose", FieldType.SELECT, true, triStateLabels),
                Field("health_notes", "Observações de saúde e internações", FieldType.NOTES),
                Field("notes", "Observações gerais", FieldType.NOTES),
            )
        FormKind.ADOPTER ->
            listOf(
                Field("full_name", "Nome completo", required = true),
                Field("birth_date", "Data de nascimento", FieldType.DATE, true),
                Field("cpf", "CPF", FieldType.NUMBER, true),
                Field("phone", "Telefone", FieldType.PHONE, true),
                Field("email", "E-mail", FieldType.EMAIL, true),
                Field("postal_code", "CEP", FieldType.NUMBER, true),
                Field("street", "Rua / avenida", required = true),
                Field("number", "Número", required = true),
                Field("complement", "Complemento"),
                Field("neighborhood", "Bairro", required = true),
                Field("city", "Cidade", required = true),
                Field(
                    "state",
                    "UF",
                    FieldType.SELECT,
                    true,
                    listOf(
                            "AC",
                            "AL",
                            "AP",
                            "AM",
                            "BA",
                            "CE",
                            "DF",
                            "ES",
                            "GO",
                            "MA",
                            "MT",
                            "MS",
                            "MG",
                            "PA",
                            "PB",
                            "PR",
                            "PE",
                            "PI",
                            "RJ",
                            "RN",
                            "RS",
                            "RO",
                            "RR",
                            "SC",
                            "SP",
                            "SE",
                            "TO",
                        )
                        .associateWith { it },
                ),
                Field("has_other_animals", "Possui outros animais", FieldType.BOOLEAN),
            ) +
                (if (v["has_other_animals"] == "true")
                    listOf(Field("other_animals", "Quais outros animais?", FieldType.NOTES))
                else emptyList()) +
                Field("notes", "Observações", FieldType.NOTES)
        FormKind.DOSE ->
            listOf(
                Field("date", "Data da aplicação realizada", FieldType.DATE, true),
                Field("time", "Horário realizado", FieldType.TIME, true),
                Field("notes", "Observações", FieldType.NOTES),
            )
        FormKind.VACCINE ->
            listOf(
                Field(
                    "entry_mode",
                    "Registro",
                    FieldType.SELECT,
                    true,
                    linkedMapOf("applied" to "Aplicação realizada", "planned" to "Agendar dose"),
                ),
                Field("name", "Nome da vacina", required = true),
            ) +
                (if (v["entry_mode"] == "planned")
                    listOf(Field("scheduled_on", "Data prevista", FieldType.DATE, true))
                else
                    listOf(
                        Field("administered_on", "Data da aplicação", FieldType.DATE, true),
                        Field("next_due_on", "Próxima dose prevista", FieldType.DATE),
                    )) +
                Field("notes", "Observações", FieldType.NOTES)
        FormKind.TREATMENT ->
            listOf(
                Field("name", "Nome do tratamento", required = true),
                Field("instructions", "Orientações recebidas", FieldType.NOTES, true),
                Field("start_date", "Data de início", FieldType.DATE, true),
                Field("time_of_day", "Horário da primeira aplicação", FieldType.TIME, true),
                Field(
                    "schedule_type",
                    "Frequência",
                    FieldType.SELECT,
                    true,
                    linkedMapOf(
                        "daily" to "Todos os dias",
                        "weekly" to "Dias da semana",
                        "hourly" to "Intervalo em horas",
                    ),
                ),
            ) +
                when (v["schedule_type"]) {
                    "hourly" ->
                        listOf(
                            Field("interval_hours", "A cada quantas horas?", FieldType.NUMBER, true)
                        )
                    "weekly" ->
                        listOf(
                            Field(
                                "weekdays",
                                "Dias da semana",
                                FieldType.MULTI,
                                true,
                                linkedMapOf(
                                    "1" to "Segunda",
                                    "2" to "Terça",
                                    "3" to "Quarta",
                                    "4" to "Quinta",
                                    "5" to "Sexta",
                                    "6" to "Sábado",
                                    "7" to "Domingo",
                                ),
                            )
                        )
                    else -> emptyList()
                } +
                listOf(
                    Field(
                        "finish_mode",
                        "Término",
                        FieldType.SELECT,
                        true,
                        linkedMapOf("date" to "Por data", "count" to "Por quantidade de aplicações"),
                    ),
                    if (v["finish_mode"] == "count")
                        Field("total_doses", "Quantidade de aplicações", FieldType.NUMBER, true)
                    else Field("end_date", "Último dia", FieldType.DATE, true),
                    Field(
                        "timezone",
                        "Fuso horário",
                        FieldType.SELECT,
                        true,
                        (listOf(
                                    v["timezone"] ?: "America/Fortaleza",
                                    "America/Fortaleza",
                                    "America/Sao_Paulo",
                                    "America/Manaus",
                                    "America/Rio_Branco",
                                )
                                .distinct())
                            .associateWith { it.replace("America/", "").replace('_', ' ') },
                    ),
                )
        FormKind.INVITE ->
            listOf(
                Field("full_name", "Nome completo", required = true),
                Field("email", "E-mail", FieldType.EMAIL, true),
                Field("phone", "Telefone", FieldType.PHONE),
            )
        FormKind.VOLUNTEER ->
            listOf(
                Field("full_name", "Nome completo", required = true),
                Field("phone", "Telefone", FieldType.PHONE),
            )
        FormKind.ADOPTION ->
            listOf(
                Field("adopter_id", "Adotante", FieldType.SELECT, true, options(s, "adopters")),
                Field(
                    "animal_ids",
                    "Animais que estão sendo adotados",
                    FieldType.MULTI,
                    true,
                    options(s, "animals") { !it.archived && it.text("status") == "available" },
                ),
                Field("adopted_on", "Data da adoção", FieldType.DATE, true),
                Field("notes", "Observações", FieldType.NOTES),
            )
        FormKind.VISIT -> {
            val eligible =
                s.records("adoptions").filter { ad ->
                    s.ongoing(ad) &&
                        s.records("visit_animals").count { va ->
                            va.text("adoption_id") == ad.id &&
                                s.find("visits", va.text("visit_id"))
                                    ?.text("cancelled_at")
                                    .isNullOrEmpty()
                        } < 3
                }
            listOf(
                Field(
                    "adopter_id",
                    "Adotante",
                    FieldType.SELECT,
                    true,
                    options(s, "adopters") { p -> eligible.any { it.text("adopter_id") == p.id } },
                ),
                Field(
                    "adoption_ids",
                    "Animais a acompanhar",
                    FieldType.MULTI,
                    true,
                    eligible
                        .filter { it.text("adopter_id") == v["adopter_id"] }
                        .associate {
                            it.id to
                                (s.name("animals", it.text("animal_id")) +
                                    " · ${s.completedVisits(it.id)}/3 visitas")
                        },
                ),
                Field(
                    "visitor_id",
                    "Visitante",
                    FieldType.SELECT,
                    true,
                    options(s, "volunteers") {
                        it.bool("active") &&
                            !it.archived &&
                            it.text("role") in listOf("editor", "admin")
                    },
                ),
                Field("date", "Data marcada", FieldType.DATE, true),
                Field("time", "Horário", FieldType.TIME, true),
                Field("notes", "Observações", FieldType.NOTES),
            )
        }
        FormKind.VISIT_RESULT ->
            listOf(
                Field("date", "Data da realização", FieldType.DATE, true),
                Field("time", "Horário", FieldType.TIME, true),
            ) +
                s.visitAdoptions(request.target).filter(s::ongoing).flatMap { ad ->
                    listOf(
                        Field(
                            "outcome:${ad.id}",
                            s.name("animals", ad.text("animal_id")),
                            FieldType.SELECT,
                            true,
                            outcomeLabels,
                        ),
                        Field(
                            "note:${ad.id}",
                            "Observações sobre ${s.name("animals",ad.text("animal_id"))}",
                            FieldType.NOTES,
                        ),
                    )
                } +
                Field("notes", "Observações gerais da visita", FieldType.NOTES)
        FormKind.CLOSE -> {
            val ad = s.find("adoptions", request.target)
            listOf(
                Field(
                    "outcome",
                    "Encerramento para este animal",
                    FieldType.SELECT,
                    true,
                    if (ad != null && s.ongoing(ad) && s.completedVisits(ad.id) == 3)
                        followupLabels.filterKeys { it != "ongoing" }
                    else mapOf("recollected" to "Recolhido"),
                )
            ) +
                if (v["outcome"] == "recollected")
                    listOf(
                        Field("return_reason", "Motivo do retorno", FieldType.NOTES, true),
                        Field(
                            "returned_confirmed",
                            "O animal já retornou à base de origem",
                            FieldType.BOOLEAN,
                            true,
                        ),
                    )
                else emptyList()
        }
    }
}

@Composable
fun FormScreen(
    request: FormRequest,
    snapshot: Snapshot,
    busy: Boolean,
    vm: AbraceViewModel,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    newAdopter: () -> Unit,
) {
    val context = LocalContext.current
    var error by remember(request.key) { mutableStateOf<String?>(null) }
    val visibleFields = fields(request, snapshot)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(request.kind.title) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                actions = {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            try {
                                visibleFields.forEach { f ->
                                    val value = request.values[f.key].orEmpty()
                                    require(
                                        !f.required ||
                                            (value.isNotBlank() &&
                                                (f.type != FieldType.BOOLEAN || value == "true"))
                                    ) {
                                        "Preencha: ${f.label}."
                                    }
                                    if (value.isNotBlank() && f.type == FieldType.DATE)
                                        LocalDate.parse(value)
                                    if (value.isNotBlank() && f.type == FieldType.DECIMAL)
                                        require(
                                            (value.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0
                                        ) {
                                            "Informe um peso válido."
                                        }
                                    if (f.type == FieldType.SELECT && value.isNotBlank())
                                        require(value in f.options) { "Confira: ${f.label}." }
                                }
                                if (request.kind == FormKind.ADOPTER) {
                                    require(isValidCpf(request.values["cpf"].orEmpty())) {
                                        "Informe um CPF válido."
                                    }
                                    require(
                                        request.values["postal_code"]
                                            .orEmpty()
                                            .filter(Char::isDigit)
                                            .length == 8
                                    ) {
                                        "Informe um CEP com oito dígitos."
                                    }
                                }
                                val payload = buildJsonObject {
                                    put("id", request.values["id"].orEmpty())
                                    visibleFields
                                        .filterNot {
                                            ':' in it.key ||
                                                it.key in
                                                    listOf(
                                                        "date",
                                                        "time",
                                                        "finish_mode",
                                                        "entry_mode",
                                                        "outcome",
                                                        "returned_confirmed",
                                                        "return_reason",
                                                    )
                                        }
                                        .forEach { f ->
                                            val value = request.values[f.key].orEmpty().trim()
                                            put(
                                                f.key,
                                                when (f.type) {
                                                    FieldType.BOOLEAN ->
                                                        JsonPrimitive(value == "true")
                                                    FieldType.NUMBER ->
                                                        if (f.key in listOf("cpf", "postal_code"))
                                                            JsonPrimitive(
                                                                value.filter(Char::isDigit)
                                                            )
                                                        else
                                                            value
                                                                .toIntOrNull()
                                                                ?.let(::JsonPrimitive) ?: JsonNull
                                                    FieldType.DECIMAL ->
                                                        value
                                                            .replace(',', '.')
                                                            .toDoubleOrNull()
                                                            ?.let(::JsonPrimitive) ?: JsonNull
                                                    FieldType.MULTI ->
                                                        JsonArray(
                                                            value
                                                                .split('|')
                                                                .filter(String::isNotBlank)
                                                                .map {
                                                                    if (f.key == "weekdays")
                                                                        JsonPrimitive(it.toInt())
                                                                    else JsonPrimitive(it)
                                                                }
                                                        )
                                                    FieldType.DATE ->
                                                        if (value.isBlank()) JsonNull
                                                        else JsonPrimitive(value)
                                                    else -> JsonPrimitive(value)
                                                },
                                            )
                                        }
                                    when (request.kind) {
                                        FormKind.ANIMAL ->
                                            put(
                                                "photo_paths",
                                                JsonArray(request.photoPaths.map(::JsonPrimitive)),
                                            )
                                        FormKind.VACCINE,
                                        FormKind.TREATMENT -> put("animal_id", request.target)
                                        else -> Unit
                                    }
                                }
                                when (request.kind) {
                                    FormKind.DOSE ->
                                        vm.rpc(
                                            "record_dose",
                                            buildJsonObject {
                                                put("p_id", request.target)
                                                put("p_given_at", localInstant(request.values))
                                                put("p_notes", request.values["notes"].orEmpty())
                                            },
                                            "Aplicação registrada.",
                                        ) {
                                            onSaved("")
                                        }
                                    FormKind.INVITE -> vm.invite(payload) { onSaved("") }
                                    FormKind.ADOPTION ->
                                        vm.rpc(
                                            "create_adoption",
                                            buildJsonObject { put("p_data", payload) },
                                            "Adoção registrada.",
                                        ) {
                                            onSaved("")
                                        }
                                    FormKind.VISIT ->
                                        vm.rpc(
                                            "schedule_visit",
                                            buildJsonObject {
                                                put(
                                                    "p_data",
                                                    JsonObject(
                                                        payload +
                                                            ("scheduled_at" to
                                                                JsonPrimitive(
                                                                    localInstant(request.values)
                                                                ))
                                                    ),
                                                )
                                            },
                                            "Visita agendada.",
                                        ) {
                                            onSaved("")
                                        }
                                    FormKind.VISIT_RESULT ->
                                        vm.rpc(
                                            "record_visit",
                                            buildJsonObject {
                                                put(
                                                    "p_data",
                                                    buildJsonObject {
                                                        put("id", request.target)
                                                        put(
                                                            "visited_at",
                                                            localInstant(request.values),
                                                        )
                                                        put(
                                                            "notes",
                                                            request.values["notes"].orEmpty(),
                                                        )
                                                        put(
                                                            "results",
                                                            JsonArray(
                                                                snapshot
                                                                    .visitAdoptions(request.target)
                                                                    .filter(snapshot::ongoing)
                                                                    .map { ad ->
                                                                        buildJsonObject {
                                                                            put(
                                                                                "adoption_id",
                                                                                ad.id,
                                                                            )
                                                                            put(
                                                                                "outcome",
                                                                                request.values[
                                                                                        "outcome:${ad.id}"]
                                                                                    .orEmpty(),
                                                                            )
                                                                            put(
                                                                                "notes",
                                                                                request.values[
                                                                                        "note:${ad.id}"]
                                                                                    .orEmpty(),
                                                                            )
                                                                        }
                                                                    }
                                                            ),
                                                        )
                                                    },
                                                )
                                            },
                                            "Visita registrada.",
                                        ) {
                                            onSaved("")
                                        }
                                    FormKind.CLOSE ->
                                        vm.rpc(
                                            "close_followup",
                                            buildJsonObject {
                                                put("p_id", request.target)
                                                put(
                                                    "p_outcome",
                                                    request.values["outcome"].orEmpty(),
                                                )
                                                put(
                                                    "p_reason",
                                                    request.values["return_reason"].orEmpty(),
                                                )
                                            },
                                            "Acompanhamento atualizado.",
                                        ) {
                                            onSaved("")
                                        }
                                    else ->
                                        vm.save(
                                            when (request.kind) {
                                                FormKind.ANIMAL -> "animal"
                                                FormKind.ADOPTER -> "adopter"
                                                FormKind.VACCINE -> "vaccination"
                                                FormKind.TREATMENT -> "treatment"
                                                else -> "volunteer"
                                            },
                                            payload,
                                            request.newPhotos.toList(),
                                        ) { id ->
                                            File(context.cacheDir, "photos")
                                                .listFiles()
                                                ?.forEach(File::delete)
                                            onSaved(id)
                                        }
                                }
                                error = null
                            } catch (e: Exception) {
                                error = e.message ?: "Confira os dados informados."
                            }
                        },
                    ) {
                        Text(if (busy) "Salvando…" else "Salvar")
                    }
                },
            )
        },
        bottomBar = { if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) },
    ) { padding ->
        Column(
            Modifier.padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (request.kind) {
                FormKind.ANIMAL -> PhotoEditor(request, snapshot, busy) { error = it }
                FormKind.ADOPTER ->
                    Note(
                        "O animal deve residir neste endereço. Documentos e termos ficam nos arquivos físicos da ONG."
                    )
                FormKind.ADOPTION -> {
                    Note("Registre a adoção após a entrevista e a entrega dos animais.")
                    OutlinedButton(onClick = newAdopter, enabled = !busy) {
                        Icon(Icons.Outlined.PersonAdd, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cadastrar novo adotante")
                    }
                }
                FormKind.TREATMENT ->
                    Note(
                        "Registre a orientação recebida do profissional responsável. Aplicações atrasadas não alteram as próximas datas. Limite de 366 dias por data ou 1.000 aplicações."
                    )
                FormKind.INVITE ->
                    Note(
                        "A pessoa receberá um link por e-mail para criar sua senha e começará com acesso de consulta."
                    )
                FormKind.DOSE ->
                    Note(
                        "A data prevista permanece no calendário. Informe quando a aplicação realmente foi realizada."
                    )
                FormKind.CLOSE ->
                    Note(
                        "A decisão é do visitante. Marque Recolhido somente quando o animal já estiver de volta à base de origem."
                    )
                else -> Unit
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            visibleFields.forEach { field ->
                FormField(field, request.values[field.key].orEmpty(), !busy) { value ->
                    request.values[field.key] = value
                    if (field.key == "adopter_id" && request.kind == FormKind.VISIT)
                        request.values["adoption_ids"] = ""
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun localInstant(values: Map<String, String>): String =
    LocalDate.parse(values["date"])
        .atTime(LocalTime.parse(values["time"]))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toString()

@Composable
private fun FormField(field: Field, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = field.label + if (field.required) " *" else ""
    when (field.type) {
        FieldType.SELECT ->
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (enabled) expanded = !expanded },
            ) {
                OutlinedTextField(
                    value = field.options[value].orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    enabled = enabled,
                    label = { Text(label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier =
                        Modifier.fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    field.options.forEach { (key, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onChange(key)
                                expanded = false
                            },
                        )
                    }
                }
            }
        FieldType.MULTI ->
            Column {
                Text(label, style = MaterialTheme.typography.titleSmall)
                if (field.options.isEmpty())
                    Text(
                        "Nenhum cadastro disponível para selecionar.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                field.options.forEach { (key, name) ->
                    val selected = key in value.split('|')
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selected,
                            enabled = enabled,
                            onCheckedChange = { check ->
                                onChange(
                                    (value
                                            .split('|')
                                            .filter(String::isNotBlank)
                                            .toMutableSet()
                                            .apply { if (check) add(key) else remove(key) })
                                        .joinToString("|")
                                )
                            },
                        )
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        FieldType.BOOLEAN ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f))
                Switch(
                    checked = value == "true",
                    onCheckedChange = { onChange(it.toString()) },
                    enabled = enabled,
                )
            }
        FieldType.DATE -> {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                Icon(Icons.Outlined.CalendarMonth, null)
                Spacer(Modifier.width(12.dp))
                Text(
                    "$label: ${if(value.isBlank()) "Selecionar" else dateLabel(value)}",
                    modifier = Modifier.weight(1f),
                )
            }
            if (expanded) {
                val picker =
                    rememberDatePickerState(
                        initialSelectedDateMillis =
                            runCatching {
                                    LocalDate.parse(value)
                                        .atStartOfDay(ZoneOffset.UTC)
                                        .toInstant()
                                        .toEpochMilli()
                                }
                                .getOrNull()
                    )
                DatePickerDialog(
                    onDismissRequest = { expanded = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                picker.selectedDateMillis?.let {
                                    onChange(
                                        Instant.ofEpochMilli(it)
                                            .atZone(ZoneOffset.UTC)
                                            .toLocalDate()
                                            .toString()
                                    )
                                }
                                expanded = false
                            }
                        ) {
                            Text("Confirmar")
                        }
                    },
                    dismissButton = {
                        Row {
                            if (!field.required)
                                TextButton(
                                    onClick = {
                                        onChange("")
                                        expanded = false
                                    }
                                ) {
                                    Text("Limpar")
                                }
                            TextButton(onClick = { expanded = false }) { Text("Cancelar") }
                        }
                    },
                ) {
                    DatePicker(state = picker)
                }
            }
        }
        FieldType.TIME -> {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                Icon(Icons.Outlined.Schedule, null)
                Spacer(Modifier.width(12.dp))
                Text("$label: $value", modifier = Modifier.weight(1f))
            }
            if (expanded) {
                val initial =
                    runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(9, 0))
                val picker =
                    rememberTimePickerState(
                        initialHour = initial.hour,
                        initialMinute = initial.minute,
                        is24Hour = true,
                    )
                AlertDialog(
                    onDismissRequest = { expanded = false },
                    title = { Text("Horário") },
                    text = { TimeInput(state = picker) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onChange("%02d:%02d".format(picker.hour, picker.minute))
                                expanded = false
                            }
                        ) {
                            Text("Confirmar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { expanded = false }) { Text("Cancelar") }
                    },
                )
            }
        }
        else ->
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onChange(if (field.type == FieldType.NUMBER) it.filter(Char::isDigit) else it)
                },
                enabled = enabled,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = field.type != FieldType.NOTES,
                minLines = if (field.type == FieldType.NOTES) 3 else 1,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            when (field.type) {
                                FieldType.NUMBER -> KeyboardType.Number
                                FieldType.DECIMAL -> KeyboardType.Decimal
                                FieldType.EMAIL -> KeyboardType.Email
                                FieldType.PHONE -> KeyboardType.Phone
                                else -> KeyboardType.Text
                            }
                    ),
            )
    }
}

@Composable
private fun PhotoEditor(
    request: FormRequest,
    snapshot: Snapshot,
    busy: Boolean,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris
            ->
            request.newPhotos.addAll(
                uris.take((3 - request.photoPaths.size - request.newPhotos.size).coerceAtLeast(0))
            )
        }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok)
                cameraUri?.let {
                    if (request.photoPaths.size + request.newPhotos.size < 3)
                        request.newPhotos.add(it)
                }
        }
    Text(
        "Fotos · ${request.photoPaths.size+request.newPhotos.size}/3",
        style = MaterialTheme.typography.titleMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        request.photoPaths.toList().forEach { path ->
            Column {
                AsyncImage(
                    model = photoRequest(snapshot.photoUrls[path]),
                    contentDescription = "Foto do animal",
                    modifier = Modifier.size(88.dp),
                )
                TextButton(enabled = !busy, onClick = { request.photoPaths.remove(path) }) {
                    Text("Remover")
                }
            }
        }
        request.newPhotos.toList().forEach { uri ->
            Column {
                AsyncImage(
                    model = photoRequest(uri),
                    contentDescription = "Nova foto do animal",
                    modifier = Modifier.size(88.dp),
                )
                TextButton(enabled = !busy, onClick = { request.newPhotos.remove(uri) }) {
                    Text("Remover")
                }
            }
        }
    }
    if (request.photoPaths.size + request.newPhotos.size < 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    pick.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, null)
                Spacer(Modifier.width(8.dp))
                Text("Galeria")
            }
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
                    val uri =
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.files",
                            File.createTempFile("animal-", ".jpg", dir),
                        )
                    cameraUri = uri
                    runCatching { camera.launch(uri) }
                        .onFailure {
                            onError(
                                "Não foi possível abrir a câmera. Você também pode escolher uma foto da galeria."
                            )
                        }
                },
            ) {
                Icon(Icons.Outlined.PhotoCamera, null)
                Spacer(Modifier.width(8.dp))
                Text("Câmera")
            }
        }
}

@Composable
fun Note(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
