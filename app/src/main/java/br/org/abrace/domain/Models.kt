package br.org.abrace.domain

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.*

/** API records remain immutable. Only session tokens are persisted, encrypted. */
data class Record(val values: JsonObject) {
    val id: String
        get() = text("id")

    fun text(key: String): String =
        (values[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content.orEmpty()

    fun bool(key: String): Boolean = (values[key] as? JsonPrimitive)?.booleanOrNull ?: false

    fun int(key: String): Int = text(key).toIntOrNull() ?: 0

    fun strings(key: String): List<String> =
        (values[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

    val archived: Boolean
        get() = text("archived_at").isNotEmpty()
}

data class Snapshot(val root: JsonObject, val photoUrls: Map<String, String> = emptyMap()) {
    fun records(key: String): List<Record> =
        (root[key] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::Record) }.orEmpty()

    val userId
        get() = root["current_user_id"]?.jsonPrimitive?.content.orEmpty()

    val me
        get() = records("volunteers").firstOrNull { it.id == userId }

    val role
        get() = Role.entries.firstOrNull { it.code == me?.text("role") } ?: Role.VIEWER

    val canEdit
        get() = role != Role.VIEWER

    val isAdmin
        get() = role == Role.ADMIN

    fun find(table: String, id: String) = records(table).firstOrNull { it.id == id }

    fun name(table: String, id: String) =
        find(table, id)
            ?.let {
                it.text(if (table in listOf("adopters", "volunteers")) "full_name" else "name")
            }
            .orEmpty()

    fun completedVisits(adoptionId: String): Int =
        records("visit_animals").count { result ->
            result.text("adoption_id") == adoptionId &&
                result.text("outcome").isNotBlank() &&
                find("visits", result.text("visit_id"))?.let {
                    it.text("visited_at").isNotBlank() && it.text("cancelled_at").isBlank()
                } == true
        }

    fun visitAdoptions(visitId: String): List<Record> =
        records("visit_animals")
            .filter { it.text("visit_id") == visitId }
            .mapNotNull { find("adoptions", it.text("adoption_id")) }

    fun ongoing(adoption: Record) =
        adoption.text("ended_at").isBlank() && adoption.text("followup_status") == "ongoing"
}

enum class Role(val code: String, val label: String) {
    VIEWER("viewer", "Consulta"),
    EDITOR("editor", "Edição"),
    ADMIN("admin", "Administração"),
}

val speciesLabels = linkedMapOf("dog" to "Cão", "cat" to "Gato")
val sexLabels = linkedMapOf("unknown" to "Não informado", "female" to "Fêmea", "male" to "Macho")
val statusLabels =
    linkedMapOf(
        "available" to "Disponível",
        "unavailable" to "Indisponível",
        "assessment" to "Em avaliação",
        "adopted" to "Adotado",
        "deceased" to "Falecido",
    )
val triStateLabels = linkedMapOf("unknown" to "Não sabemos", "yes" to "Sim", "no" to "Não")
val followupLabels =
    linkedMapOf(
        "ongoing" to "Em acompanhamento",
        "adopted" to "Adotado",
        "recollected" to "Recolhido",
    )
val outcomeLabels =
    linkedMapOf("well_cared" to "bem-cuidado", "needs_attention" to "precisa de atenção")
val feedLabels =
    linkedMapOf(
        "vaccine" to "Vacinas",
        "dose" to "Tratamentos",
        "visit" to "Visitas",
        "unscheduled" to "Agendar",
        "closure" to "Encerrar",
    )
val localeBr: Locale = Locale.forLanguageTag("pt-BR")

fun dateLabel(value: String): String =
    runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }
        .getOrDefault(value)

fun instantOf(value: String): Instant? =
    runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()

fun dateTimeLabel(value: String): String =
    instantOf(value)
        ?.atZone(ZoneId.systemDefault())
        ?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")) ?: value

fun ageLabel(birth: String, today: LocalDate = LocalDate.now()): String {
    val date = runCatching { LocalDate.parse(birth) }.getOrNull() ?: return "Idade desconhecida"
    if (date.isAfter(today)) return "Idade inválida"
    val p = Period.between(date, today)
    return when {
        p.years > 0 -> "${p.years} ${if (p.years == 1) "ano" else "anos"}"
        p.months > 0 -> "${p.months} ${if (p.months == 1) "mês" else "meses"}"
        else -> "Menos de 1 mês"
    }
}

fun isValidCpf(value: String): Boolean {
    val digits = value.filter(Char::isDigit)
    if (digits.length != 11 || digits.toSet().size == 1) return false
    return (9..10).all { end ->
        val sum = (0 until end).sumOf { digits[it].digitToInt() * (end + 1 - it) }
        ((sum * 10 % 11) % 10) == digits[end].digitToInt()
    }
}

/** Null dates represent manual scheduling needs, never a fictitious overdue date. */
fun sortedFeed(records: List<Record>): List<Record> =
    records.sortedWith(
        compareBy<Record> { instantOf(it.text("due_at")) ?: Instant.MAX }
            .thenBy { it.text("title") }
    )
