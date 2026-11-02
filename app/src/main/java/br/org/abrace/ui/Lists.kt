package br.org.abrace.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.org.abrace.domain.*
import br.org.abrace.domain.Record
import coil3.compose.AsyncImage
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(s: Snapshot, onOpen: (Detail) -> Unit) {
    var filter by remember { mutableStateOf("") }
    val feed =
        sortedFeed(s.records("feed")).filter { filter.isEmpty() || it.text("kind") == filter }
    val resident =
        s.records("animals").count {
            !it.archived && it.text("status") !in listOf("adopted", "deceased")
        }
    val adopted = s.records("adoptions").count { it.text("ended_at").isEmpty() }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                LocalDate.now()
                    .format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", localeBr))
                    .replaceFirstChar { it.titlecase(localeBr) },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item { Text("O cuidado de hoje", style = MaterialTheme.typography.headlineLarge) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(resident.toString(), "Nas bases", Modifier.weight(1f))
                StatCard(adopted.toString(), "Em seus lares", Modifier.weight(1f))
            }
        }
        item {
            Text(
                "Compromissos e pendências",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        item { FilterRow(filter, listOf("" to "Tudo") + feedLabels.toList()) { filter = it } }
        if (feed.isEmpty())
            item {
                EmptyCard(
                    "Tudo em dia por aqui",
                    if (s.records("feed").isEmpty()) "Os próximos cuidados aparecerão neste espaço."
                    else "Não há pendências deste tipo.",
                )
            }
        items(feed, key = { it.id }) { need ->
            val date = instantOf(need.text("due_at"))
            val overdue = date?.isBefore(Instant.now()) == true
            Card(
                onClick = {
                    onOpen(
                        when (need.text("kind")) {
                            "visit" -> Detail("visit", need.text("reference_id"))
                            "unscheduled",
                            "closure" -> Detail("adoption", need.text("reference_id"))
                            else -> Detail("animal", need.text("animal_id"), 1)
                        }
                    )
                },
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (overdue) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceContainer
                    ),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        when (need.text("kind")) {
                            "vaccine" -> Icons.Outlined.Vaccines
                            "dose" -> Icons.Outlined.Medication
                            "visit" -> Icons.Outlined.Home
                            "closure" -> Icons.Outlined.TaskAlt
                            else -> Icons.Outlined.Event
                        },
                        null,
                        tint =
                            if (overdue) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(need.text("title"), style = MaterialTheme.typography.titleMedium)
                        Text(need.text("subtitle"), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (date == null) "Ação pendente"
                            else
                                (if (overdue) "Atrasado · " else "") +
                                    dateTimeLabel(need.text("due_at")),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Icon(Icons.Outlined.ChevronRight, "Abrir")
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(value, style = MaterialTheme.typography.headlineLarge)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun AnimalsScreen(s: Snapshot, onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var base by remember { mutableStateOf("") }
    val animals =
        s.records("animals").filter { a ->
            a.archived == (filter == "archived") &&
                (filter.isEmpty() ||
                    filter == "archived" ||
                    a.text("species") == filter ||
                    a.text("status") == filter) &&
                (base.isEmpty() || a.text("home_base_id") == base) &&
                listOf(a.text("name"), a.text("breed"), a.text("microchip")).any {
                    it.contains(query, true)
                }
        }
    Column {
        Search(query, { query = it }, "Buscar nome, raça ou microchip")
        FilterRow(
            filter,
            listOf(
                "" to "Todos",
                "available" to "Disponíveis",
                "dog" to "Cães",
                "cat" to "Gatos",
                "adopted" to "Adotados",
                "assessment" to "Em avaliação",
                "deceased" to "Falecidos",
                "archived" to "Arquivados",
            ),
        ) {
            filter = it
        }
        FilterRow(
            base,
            listOf("" to "Todas as bases") + s.records("bases").map { it.id to it.text("name") },
        ) {
            base = it
        }
        if (animals.isEmpty())
            EmptyCard(
                "Nenhum animal encontrado",
                "Cadastre os animais ou ajuste os filtros.",
                Modifier.padding(20.dp),
            )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(144.dp),
            contentPadding =
                PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(animals, key = { it.id }) { a ->
                Card(onClick = { onOpen(a.id) }, shape = MaterialTheme.shapes.extraLarge) {
                    AnimalPhoto(a, s, Modifier.fillMaxWidth().aspectRatio(1.15f))
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            a.text("name"),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${speciesLabels[a.text("species")]} · ${ageLabel(a.text("estimated_birth_date"))}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            s.name("bases", a.text("home_base_id")),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                        BadgeText(
                            if (a.archived) "Arquivado"
                            else statusLabels[a.text("status")].orEmpty()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdoptionsScreen(s: Snapshot, section: Int, onSection: (Int) -> Unit, onOpen: (Detail) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember(section) { mutableStateOf("") }
    Column {
        PrimaryTabRow(selectedTabIndex = section) {
            listOf("Adoções", "Adotantes").forEachIndexed { i, label ->
                Tab(selected = section == i, onClick = { onSection(i) }, text = { Text(label) })
            }
        }
        Search(
            query,
            { query = it },
            if (section == 0) "Buscar animal ou adotante" else "Buscar nome ou CPF",
        )
        if (section == 0) {
            FilterRow(
                filter,
                listOf(
                    "" to "Todas",
                    "ongoing" to "Em acompanhamento",
                    "adopted" to "Adotados",
                    "recollected" to "Recolhidos",
                ),
            ) {
                filter = it
            }
            val adoptions =
                s.records("adoptions").filter { ad ->
                    (filter.isEmpty() || ad.text("followup_status") == filter) &&
                        (s.name("animals", ad.text("animal_id")) +
                                s.name("adopters", ad.text("adopter_id")))
                            .contains(query, true)
                }
            LazyColumn(
                contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (adoptions.isEmpty())
                    item {
                        EmptyCard(
                            "Nenhuma adoção por aqui",
                            "As adoções registradas aparecerão com o progresso de cada acompanhamento.",
                        )
                    }
                items(adoptions, key = { it.id }) { ad ->
                    AdoptionCard(ad, s) { onOpen(Detail("adoption", ad.id)) }
                }
            }
        } else {
            FilterRow(filter, listOf("" to "Ativos", "archived" to "Arquivados")) { filter = it }
            val people =
                s.records("adopters").filter {
                    it.archived == (filter == "archived") &&
                        (it.text("full_name").contains(query, true) ||
                            it.text("cpf").contains(query))
                }
            LazyColumn(
                contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (people.isEmpty())
                    item {
                        EmptyCard(
                            "Nenhum adotante encontrado",
                            "Cadastre o adotante antes de registrar a adoção.",
                        )
                    }
                items(people, key = { it.id }) { p ->
                    Card(onClick = { onOpen(Detail("adopter", p.id)) }) {
                        Row(
                            Modifier.padding(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Person, null)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    p.text("full_name"),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "${p.text("city")} · ${p.text("state")}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Icon(Icons.Outlined.ChevronRight, null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VolunteersScreen(s: Snapshot, onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    Column {
        Search(query, { query = it }, "Buscar voluntário")
        FilterRow(filter, listOf("" to "Ativos", "archived" to "Desativados")) { filter = it }
        val people =
            s.records("volunteers").filter {
                (if (filter == "archived") !it.bool("active") || it.archived
                else it.bool("active") && !it.archived) &&
                    it.text("full_name").contains(query, true)
            }
        LazyColumn(
            contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (people.isEmpty())
                item {
                    EmptyCard(
                        "Nenhum voluntário encontrado",
                        "Os convites são enviados pelo administrador.",
                    )
                }
            items(people, key = { it.id }) { person ->
                Card(onClick = { onOpen(person.id) }) {
                    Row(
                        Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                person.text("full_name").take(1).uppercase(),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                person.text("full_name"),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(person.text("email"), style = MaterialTheme.typography.bodySmall)
                            BadgeText(
                                Role.entries
                                    .firstOrNull { it.code == person.text("role") }
                                    ?.label
                                    .orEmpty()
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, null)
                    }
                }
            }
        }
    }
}

@Composable
fun AdoptionCard(ad: Record, s: Snapshot, onClick: () -> Unit) {
    val a = s.find("animals", ad.text("animal_id"))
    Card(onClick = onClick, shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (a != null) AnimalPhoto(a, s, Modifier.size(68.dp).clip(MaterialTheme.shapes.medium))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(a?.text("name").orEmpty(), style = MaterialTheme.typography.titleLarge)
                Text(
                    s.name("adopters", ad.text("adopter_id")),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(dateLabel(ad.text("adopted_on")), style = MaterialTheme.typography.labelMedium)
                BadgeText(
                    if (ad.text("end_reason") == "deceased") "Falecido"
                    else followupLabels[ad.text("followup_status")].orEmpty()
                )
                VisitProgress(s.completedVisits(ad.id))
            }
        }
    }
}

@Composable
fun VisitProgress(completed: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) { i ->
            Box(
                Modifier.size(9.dp)
                    .background(
                        if (i < completed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    )
            )
        }
        Spacer(Modifier.width(4.dp))
        Text("$completed/3 visitas", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun AnimalPhoto(animal: Record, s: Snapshot, modifier: Modifier) {
    Box(
        modifier.background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val photo = animal.strings("photo_paths").firstOrNull()?.let { s.photoUrls[it] }
        if (photo != null)
            AsyncImage(
                model = photoRequest(photo),
                contentDescription = "Foto de ${animal.text("name")}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        else
            Icon(
                Icons.Outlined.Pets,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
    }
}

@Composable
fun BadgeText(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun Search(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (value.isNotBlank())
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Outlined.Close, "Limpar busca")
                }
        },
        placeholder = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
fun FilterRow(selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
fun EmptyCard(title: String, text: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
