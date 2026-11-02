@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package br.org.abrace.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import br.org.abrace.domain.*
import br.org.abrace.domain.Record
import coil3.compose.AsyncImage
import kotlinx.serialization.json.*

@Composable
fun DetailScreen(
    detail: Detail,
    s: Snapshot,
    busy: Boolean,
    onOpen: (Detail) -> Unit,
    onForm: (FormKind, String, Record?) -> Unit,
    onAction: (PendingAction) -> Unit,
) {
    val record = s.find(tableFor(detail.kind), detail.id)
    if (record == null) {
        EmptyCard(
            "Cadastro não encontrado",
            "Atualize a lista para continuar.",
            Modifier.padding(20.dp),
        )
        return
    }
    when (detail.kind) {
        "animal" -> AnimalDetail(record, s, detail.section, busy, onOpen, onForm, onAction)
        "adopter" -> AdopterDetail(record, s, onOpen)
        "adoption" -> AdoptionDetail(record, s, busy, onOpen, onForm)
        "volunteer" -> VolunteerDetail(record, s, busy, onAction)
        "visit" -> VisitDetail(record, s, busy, onOpen, onForm, onAction)
    }
}

@Composable
private fun AnimalDetail(
    a: Record,
    s: Snapshot,
    initialSection: Int,
    busy: Boolean,
    onOpen: (Detail) -> Unit,
    onForm: (FormKind, String, Record?) -> Unit,
    onAction: (PendingAction) -> Unit,
) {
    var section by remember(a.id) { mutableIntStateOf(initialSection) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val paths = a.strings("photo_paths")
        if (paths.isNotEmpty()) {
            val pager = rememberPagerState(pageCount = { paths.size })
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(250.dp)) { page
                ->
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = photoRequest(s.photoUrls[paths[page]]),
                        contentDescription = "Foto ${page+1} de ${a.text("name")}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            if (paths.size > 1)
                Text(
                    "${pager.currentPage+1} de ${paths.size} fotos · deslize para ver",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
        } else AnimalPhoto(a, s, Modifier.fillMaxWidth().height(190.dp))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgeText(if (a.archived) "Arquivado" else statusLabels[a.text("status")].orEmpty())
                BadgeText(speciesLabels[a.text("species")].orEmpty())
            }
            Text(a.text("name"), style = MaterialTheme.typography.headlineLarge)
            Text(
                "${sexLabels[a.text("sex")]} · ${ageLabel(a.text("estimated_birth_date"))}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                s.name("bases", a.text("home_base_id")),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (s.canEdit && !a.archived && a.text("status") == "available")
                Button(onClick = { onForm(FormKind.ADOPTION, a.id, null) }, enabled = !busy) {
                    Icon(Icons.Outlined.FavoriteBorder, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Registrar adoção")
                }
        }
        PrimaryTabRow(selectedTabIndex = section) {
            listOf("Dados", "Saúde", "Histórico").forEachIndexed { i, label ->
                Tab(selected = section == i, onClick = { section = i }, text = { Text(label) })
            }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (section) {
                0 -> {
                    SectionTitle("Sobre ${a.text("name")}")
                    Info("Nascimento estimado", dateLabel(a.text("estimated_birth_date")))
                    Info("Raça", a.text("breed"))
                    Info("Peso", a.text("weight_kg").let { if (it.isBlank()) "" else "$it kg" })
                    Info("Cor", a.text("color"))
                    Info("Microchip", a.text("microchip"))
                    Info("Entrada na ONG", dateLabel(a.text("admitted_on")))
                    Info("Observações", a.text("notes"))
                }
                1 -> {
                    SectionTitle("Resumo de saúde")
                    Info("Castrado", triStateLabels[a.text("neutered")].orEmpty())
                    Info(
                        "Vacinado contra raiva",
                        triStateLabels[a.text("rabies_vaccinated")].orEmpty(),
                    )
                    Info("Leishmaniose", triStateLabels[a.text("leishmaniasis")].orEmpty())
                    Info("Observações e internações", a.text("health_notes"))
                    HorizontalDivider()
                    SectionTitle("Vacinação")
                    if (s.canEdit && !a.archived && a.text("status") != "deceased")
                        OutlinedButton(
                            onClick = { onForm(FormKind.VACCINE, a.id, null) },
                            enabled = !busy,
                        ) {
                            Icon(Icons.Outlined.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Registrar vacina")
                        }
                    val vaccines =
                        s.records("vaccinations").filter {
                            it.text("animal_id") == a.id && !it.archived
                        }
                    if (vaccines.isEmpty())
                        Text(
                            "Nenhuma vacina registrada.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    vaccines.forEach { v ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(v.text("name"), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (v.text("administered_on").isBlank())
                                        "Agendada para ${dateLabel(v.text("scheduled_on"))}"
                                    else "Aplicada em ${dateLabel(v.text("administered_on"))}"
                                )
                                if (
                                    v.text("administered_on").isBlank() &&
                                        s.canEdit &&
                                        !a.archived &&
                                        a.text("status") != "deceased"
                                )
                                    TextButton(
                                        onClick = { onForm(FormKind.VACCINE, a.id, v) },
                                        enabled = !busy,
                                    ) {
                                        Text("Registrar aplicação")
                                    }
                                if (v.text("next_due_on").isNotEmpty())
                                    Text(
                                        "Próxima prevista: ${dateLabel(v.text("next_due_on"))}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                if (v.text("notes").isNotBlank()) Text(v.text("notes"))
                            }
                        }
                    }
                    HorizontalDivider()
                    SectionTitle("Tratamentos")
                    if (s.canEdit && !a.archived && a.text("status") != "deceased")
                        OutlinedButton(
                            onClick = { onForm(FormKind.TREATMENT, a.id, null) },
                            enabled = !busy,
                        ) {
                            Icon(Icons.Outlined.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Novo tratamento")
                        }
                    val treatments =
                        s.records("treatments").filter {
                            it.text("animal_id") == a.id && !it.archived
                        }
                    if (treatments.isEmpty())
                        Text(
                            "Nenhum tratamento registrado.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    treatments.forEach {
                        TreatmentCard(it, s, busy, a.text("status") != "deceased", onForm, onAction)
                    }
                }
                2 -> {
                    SectionTitle("Adoções")
                    val adoptions = s.records("adoptions").filter { it.text("animal_id") == a.id }
                    if (adoptions.isEmpty()) Text("Nenhuma adoção registrada.")
                    adoptions.forEach { ad ->
                        AdoptionCard(ad, s) { onOpen(Detail("adoption", ad.id)) }
                    }
                    HorizontalDivider()
                    SectionTitle("Bases de acolhimento")
                    s.records("transfers")
                        .filter { it.text("animal_id") == a.id }
                        .forEach { t ->
                            Info(
                                dateTimeLabel(t.text("happened_at")),
                                if (t.text("from_base_id").isBlank())
                                    "Entrada em ${s.name("bases",t.text("to_base_id"))}"
                                else
                                    "${s.name("bases",t.text("from_base_id"))} → ${s.name("bases",t.text("to_base_id"))}",
                            )
                        }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TreatmentCard(
    t: Record,
    s: Snapshot,
    busy: Boolean,
    animalAlive: Boolean,
    onForm: (FormKind, String, Record?) -> Unit,
    onAction: (PendingAction) -> Unit,
) {
    var expanded by remember(t.id) { mutableStateOf(false) }
    var shown by remember(t.id) { mutableIntStateOf(10) }
    val doses = s.records("doses").filter { it.text("treatment_id") == t.id }
    val done = doses.count { it.text("given_at").isNotBlank() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t.text("name"), style = MaterialTheme.typography.titleMedium)
            BadgeText(
                when (t.text("status")) {
                    "active" -> "Ativo"
                    "suspended" -> "Suspenso"
                    else -> "Concluído"
                }
            )
            Text(t.text("instructions"))
            Text(
                "${dateLabel(t.text("start_date"))} → ${dateLabel(t.text("end_date").ifBlank{doses.lastOrNull()?.text("scheduled_at")?.let{instantOf(it)?.atZone(java.time.ZoneId.of(t.text("timezone")))?.toLocalDate()?.toString()}.orEmpty()})}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "$done/${doses.size} aplicações realizadas · ${doses.size-done} restantes",
                style = MaterialTheme.typography.labelLarge,
            )
            val next = doses.firstOrNull { it.text("given_at").isBlank() }
            if (next != null && t.text("status") == "active") {
                Text(
                    "Pendente: ${dateTimeLabel(next.text("scheduled_at"))}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (s.canEdit && animalAlive)
                    Button(onClick = { onForm(FormKind.DOSE, next.id, null) }, enabled = !busy) {
                        Text("Registrar aplicação")
                    }
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Ocultar aplicações" else "Ver aplicações")
            }
            if (expanded) {
                doses.take(shown).forEach { d ->
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Prevista: ${dateTimeLabel(d.text("scheduled_at"))}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            if (d.text("given_at").isEmpty()) "Pendente"
                            else "Realizada: ${dateTimeLabel(d.text("given_at"))}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (d.text("given_at").isNotBlank())
                            Text(
                                s.name("volunteers", d.text("given_by")),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        if (d.text("notes").isNotBlank())
                            Text(d.text("notes"), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (shown < doses.size)
                    TextButton(onClick = { shown += 10 }) { Text("Ver mais aplicações") }
            }
            if (s.canEdit && t.text("status") == "active")
                TextButton(
                    enabled = !busy,
                    onClick = {
                        onAction(
                            PendingAction(
                                "Suspender tratamento?",
                                "As aplicações já realizadas serão preservadas, e as pendentes deixarão de aparecer no feed.",
                                "suspend_treatment",
                                buildJsonObject { put("p_id", t.id) },
                                "Tratamento suspenso.",
                            )
                        )
                    },
                ) {
                    Text("Suspender tratamento")
                }
        }
    }
}

@Composable
private fun AdopterDetail(p: Record, s: Snapshot, onOpen: (Detail) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (p.archived) BadgeText("Arquivado")
        Text(p.text("full_name"), style = MaterialTheme.typography.headlineMedium)
        Info("Nascimento", dateLabel(p.text("birth_date")) + " · " + ageLabel(p.text("birth_date")))
        Info("CPF", p.text("cpf"))
        Info("Telefone", p.text("phone"))
        Info("E-mail", p.text("email"))
        SectionTitle("Endereço onde os animais residem")
        Text(address(p))
        Info(
            "Outros animais",
            if (p.bool("has_other_animals")) p.text("other_animals").ifBlank { "Sim" } else "Não",
        )
        Info("Observações", p.text("notes"))
        HorizontalDivider()
        SectionTitle("Adoções")
        val ads = s.records("adoptions").filter { it.text("adopter_id") == p.id }
        if (ads.isEmpty()) Text("Nenhuma adoção registrada.")
        ads.forEach { ad -> AdoptionCard(ad, s) { onOpen(Detail("adoption", ad.id)) } }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AdoptionDetail(
    ad: Record,
    s: Snapshot,
    busy: Boolean,
    onOpen: (Detail) -> Unit,
    onForm: (FormKind, String, Record?) -> Unit,
) {
    val a = s.find("animals", ad.text("animal_id"))
    val p = s.find("adopters", ad.text("adopter_id"))
    val visitRows = s.records("visit_animals").filter { it.text("adoption_id") == ad.id }
    val completed = s.completedVisits(ad.id)
    val latestVisitor =
        visitRows
            .filter { it.text("outcome").isNotBlank() }
            .mapNotNull { s.find("visits", it.text("visit_id")) }
            .maxByOrNull { it.text("visited_at") }
            ?.text("visitor_id")
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (a != null)
            Card(onClick = { onOpen(Detail("animal", a.id)) }) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AnimalPhoto(a, s, Modifier.size(72.dp))
                    Column {
                        Text(a.text("name"), style = MaterialTheme.typography.headlineSmall)
                        Text("Ver ficha do animal")
                    }
                }
            }
        BadgeText(
            if (ad.text("end_reason") == "deceased") "Falecido"
            else followupLabels[ad.text("followup_status")].orEmpty()
        )
        VisitProgress(completed)
        Info("Data da adoção", dateLabel(ad.text("adopted_on")))
        Info("Base de origem", s.name("bases", ad.text("origin_base_id")))
        Info("Registrada por", s.name("volunteers", ad.text("created_by")))
        if (p != null)
            OutlinedCard(
                onClick = { onOpen(Detail("adopter", p.id)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.text("full_name"), style = MaterialTheme.typography.titleMedium)
                    Text(p.text("phone"))
                    Text(address(p))
                }
            }
        Info("Observações da adoção", ad.text("notes"))
        if (ad.text("followup_closed_at").isNotEmpty())
            Info("Acompanhamento encerrado em", dateTimeLabel(ad.text("followup_closed_at")))
        if (ad.text("ended_at").isNotEmpty()) {
            Info(
                if (ad.text("end_reason") == "returned") "Retorno à base"
                else "Fim da permanência no lar",
                dateTimeLabel(ad.text("ended_at")),
            )
            if (ad.text("return_reason").isNotBlank()) Info("Motivo", ad.text("return_reason"))
        }
        if (s.canEdit && ad.text("ended_at").isEmpty()) {
            val scheduled =
                visitRows.count {
                    s.find("visits", it.text("visit_id"))?.let { v ->
                        v.text("cancelled_at").isEmpty()
                    } == true
                }
            if (s.ongoing(ad) && scheduled < 3)
                Button(enabled = !busy, onClick = { onForm(FormKind.VISIT, ad.id, null) }) {
                    Icon(Icons.Outlined.Event, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Agendar visita")
                }
            if (s.isAdmin || latestVisitor == s.userId)
                OutlinedButton(enabled = !busy, onClick = { onForm(FormKind.CLOSE, ad.id, null) }) {
                    Text(
                        if (s.ongoing(ad)) "Encerrar acompanhamento / recolher"
                        else "Registrar retorno à base"
                    )
                }
            if (completed == 3 && s.ongoing(ad))
                Note(
                    "As três visitas foram realizadas. O visitante deve encerrar o acompanhamento manualmente."
                )
        }
        HorizontalDivider()
        SectionTitle("Visitas deste animal")
        if (visitRows.isEmpty())
            Text("Nenhuma visita agendada. O acompanhamento pode ser organizado após a adoção.")
        visitRows.forEach { row ->
            s.find("visits", row.text("visit_id"))?.let { v ->
                Card(onClick = { onOpen(Detail("visit", v.id)) }) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            dateTimeLabel(v.text("scheduled_at")),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(s.name("volunteers", v.text("visitor_id")))
                        BadgeText(
                            if (v.text("cancelled_at").isNotBlank()) "Cancelada"
                            else if (v.text("visited_at").isNotBlank())
                                outcomeLabels[row.text("outcome")] ?: "Animal já recolhido"
                            else "Agendada"
                        )
                        if (row.text("notes").isNotBlank()) Text(row.text("notes"))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VisitDetail(
    v: Record,
    s: Snapshot,
    busy: Boolean,
    onOpen: (Detail) -> Unit,
    onForm: (FormKind, String, Record?) -> Unit,
    onAction: (PendingAction) -> Unit,
) {
    val p = s.find("adopters", v.text("adopter_id"))
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BadgeText(
            if (v.text("cancelled_at").isNotBlank()) "Cancelada"
            else if (v.text("visited_at").isNotBlank()) "Realizada" else "Agendada"
        )
        Info("Data marcada", dateTimeLabel(v.text("scheduled_at")))
        if (v.text("visited_at").isNotEmpty())
            Info("Realizada em", dateTimeLabel(v.text("visited_at")))
        Info("Visitante", s.name("volunteers", v.text("visitor_id")))
        if (p != null)
            OutlinedCard(onClick = { onOpen(Detail("adopter", p.id)) }) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(p.text("full_name"), style = MaterialTheme.typography.titleLarge)
                    Text(p.text("phone"))
                    Text(address(p))
                }
            }
        Info("Observações gerais", v.text("notes"))
        SectionTitle("Animais acompanhados")
        s.records("visit_animals")
            .filter { it.text("visit_id") == v.id }
            .forEach { row ->
                s.find("adoptions", row.text("adoption_id"))?.let { ad ->
                    OutlinedCard(onClick = { onOpen(Detail("adoption", ad.id)) }) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                s.name("animals", ad.text("animal_id")),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (row.text("outcome").isNotBlank())
                                BadgeText(outcomeLabels[row.text("outcome")].orEmpty())
                            if (row.text("notes").isNotBlank()) Text(row.text("notes"))
                            VisitProgress(s.completedVisits(ad.id))
                        }
                    }
                }
            }
        if (s.canEdit && v.text("visited_at").isEmpty() && v.text("cancelled_at").isEmpty()) {
            if (s.isAdmin || v.text("visitor_id") == s.userId)
                Button(onClick = { onForm(FormKind.VISIT_RESULT, v.id, null) }, enabled = !busy) {
                    Text("Registrar realização e avaliações")
                }
            OutlinedButton(
                onClick = {
                    onAction(
                        PendingAction(
                            "Cancelar esta visita?",
                            "O agendamento será cancelado sem apagar o histórico. Depois, você poderá agendar novamente.",
                            "cancel_visit",
                            buildJsonObject { put("p_id", v.id) },
                            "Visita cancelada.",
                        )
                    )
                },
                enabled = !busy,
            ) {
                Text("Cancelar agendamento")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VolunteerDetail(
    person: Record,
    s: Snapshot,
    busy: Boolean,
    onAction: (PendingAction) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(person.text("full_name"), style = MaterialTheme.typography.headlineMedium)
        BadgeText(if (person.bool("active") && !person.archived) "Ativo" else "Desativado")
        Info("E-mail", person.text("email"))
        Info("Telefone", person.text("phone"))
        Info("Acesso", Role.entries.firstOrNull { it.code == person.text("role") }?.label.orEmpty())
        if (s.isAdmin && person.bool("active") && !person.archived) {
            SectionTitle("Permissão de acesso")
            Role.entries.forEach { role ->
                OutlinedButton(
                    onClick = {
                        onAction(
                            PendingAction(
                                "Alterar permissão?",
                                "${person.text("full_name")} passará a ter acesso de ${role.label.lowercase()}.",
                                "set_volunteer_role",
                                buildJsonObject {
                                    put("p_id", person.id)
                                    put("p_role", role.code)
                                },
                                "Permissão atualizada.",
                            )
                        )
                    },
                    enabled = !busy && role.code != person.text("role"),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(role.label)
                }
            }
            Note(
                "Consulta permite ler. Edição permite cadastrar e atualizar, sem excluir. Administração também gerencia acessos e exclusões."
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun Info(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value.ifBlank { "Não informado" }, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun address(p: Record) =
    "${p.text("street")}, ${p.text("number")}${p.text("complement").let{if(it.isBlank())""else" · $it"}}\n${p.text("neighborhood")} · ${p.text("city")}/${p.text("state")}\nCEP ${p.text("postal_code")}"
