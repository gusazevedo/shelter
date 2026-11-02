package br.org.abrace.domain

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    @Test
    fun `CPF with valid length but wrong check digits is rejected`() {
        assertTrue(isValidCpf("529.982.247-25"))
        assertFalse(isValidCpf("52998224724"))
        assertFalse(isValidCpf("11111111111"))
        assertFalse(isValidCpf("123"))
    }

    @Test
    fun `estimated age changes with the reference date and handles young animals`() {
        assertEquals("1 ano", ageLabel("2025-11-01", LocalDate.of(2026, 11, 1)))
        assertEquals("11 meses", ageLabel("2025-11-01", LocalDate.of(2026, 10, 31)))
        assertEquals("Menos de 1 mês", ageLabel("2026-10-20", LocalDate.of(2026, 11, 1)))
        assertEquals("Idade desconhecida", ageLabel("", LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun `shared visit advances each adoption without counting cancelled or unevaluated animals`() {
        val s =
            Snapshot(
                Json.parseToJsonElement(
                        """{
            "adoptions":[{"id":"dog","ended_at":null,"followup_status":"ongoing"},{"id":"cat","ended_at":null,"followup_status":"ongoing"}],
            "visits":[{"id":"one","visited_at":"2026-10-01T12:00:00Z"},{"id":"two","visited_at":"2026-10-10T12:00:00Z"},{"id":"shared","visited_at":"2026-10-20T12:00:00Z"},{"id":"cancelled","cancelled_at":"2026-10-21T12:00:00Z"},{"id":"pending","visited_at":null}],
            "visit_animals":[{"visit_id":"one","adoption_id":"dog","outcome":"well_cared"},{"visit_id":"two","adoption_id":"dog","outcome":"needs_attention"},{"visit_id":"shared","adoption_id":"dog","outcome":"well_cared"},{"visit_id":"shared","adoption_id":"cat","outcome":"well_cared"},{"visit_id":"cancelled","adoption_id":"cat","outcome":"well_cared"},{"visit_id":"pending","adoption_id":"cat","outcome":null}]
        }"""
                    )
                    .jsonObject
            )
        assertEquals(3, s.completedVisits("dog"))
        assertEquals(1, s.completedVisits("cat"))
    }

    @Test
    fun `followup closure does not imply a returned placement`() {
        val s =
            Snapshot(
                Json.parseToJsonElement(
                        """{"adoptions":[{"id":"one","ended_at":null,"followup_status":"adopted"}]}"""
                    )
                    .jsonObject
            )
        val ad = s.records("adoptions").single()
        assertFalse(s.ongoing(ad))
        assertEquals("", ad.text("ended_at"))
    }

    @Test
    fun `feed orders exact instants and leaves unscheduled needs without invented dates`() {
        val s =
            Snapshot(
                Json.parseToJsonElement(
                        """{"feed":[{"id":"manual","due_at":null,"title":"Agendar"},{"id":"future","due_at":"2026-11-02T09:00:00-03:00"},{"id":"past","due_at":"2026-10-01T09:00:00-03:00"}]}"""
                    )
                    .jsonObject
            )
        assertEquals(
            listOf("past", "future", "manual"),
            sortedFeed(s.records("feed")).map { it.id },
        )
    }
}
