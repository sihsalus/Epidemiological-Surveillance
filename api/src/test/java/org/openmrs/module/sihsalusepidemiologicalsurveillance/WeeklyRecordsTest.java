package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import org.junit.Test;
import org.openmrs.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.SurveillanceCase;

public class WeeklyRecordsTest {
	
	@Test
	public void usesNotificationWeekAcrossYearBoundaryAndExcludesVoidedSources() {
		SyntheticFixture f = new SyntheticFixture();
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(f.m);
		assertEquals(LocalDate.of(2025, 12, 28), calendar.weekStart(2025, 53));
		assertEquals(LocalDate.of(2026, 1, 4), calendar.weekStart(2026, 1));
		SurveillanceCase included = row(f, "2026-01-04T04:59:59Z");
		included.setPatientGivenNames("Synthetic");
		included.setPatientAge(0);
		included.setInvestigationForm(true);
		included.setDiagnosisType("DESCARTADO");
		included.setInfectionAddress(103);
		included.setPatientResidence("Department → Province → District → Locality → Street");
		when(f.dao.ancestorIdForPopulatedCenter(103, "STATE_PROVINCE")).thenReturn(101);
		when(f.dao.ancestorIdForPopulatedCenter(103, "COUNTY_DISTRICT")).thenReturn(102);
		when(f.dao.addressCode(101)).thenReturn("0101");
		when(f.dao.addressCode(102)).thenReturn("010101");
		when(f.dao.addressCode(103)).thenReturn("0101010001");
		when(f.dao.residenceCode("Department", "Province", "District", "Locality")).thenReturn("0101010002");
		PatientIdentifierType type = new PatientIdentifierType();
		type.setName("DNI");
		PatientIdentifier dni = new PatientIdentifier("00123456", type, f.location);
		f.patient.addIdentifier(dni);
		SurveillanceCase nextWeek = row(f, "2026-01-04T05:00:00Z");
		SurveillanceCase before = row(f, "2025-12-28T04:59:59Z");
		SurveillanceCase voided = row(f, "2026-01-01T05:00:00Z");
		voided.setVoided(true);
		when(f.dao.weeklyCases(any(Date.class), any(Date.class)))
		        .thenReturn(Arrays.asList(included, nextWeek, before, voided));
		Map<String, Object> report = f.service.weeklyRecords(2025, 53);
		List<Map<String, Object>> rows = (List<Map<String, Object>>) report.get("rows");
		assertEquals(1, rows.size());
		assertEquals("00123456", rows.get(0).get("dni"));
		assertEquals(0, rows.get(0).get("age"));
		assertEquals("A90", rows.get(0).get("icd10"));
		assertEquals("0101", rows.get(0).get("province"));
		assertEquals("010101", rows.get(0).get("district"));
		assertEquals("0101010001", rows.get(0).get("locality"));
		assertEquals("0101010002", rows.get(0).get("residence"));
		assertFalse(rows.get(0).containsKey("establishment"));
		assertFalse(rows.get(0).containsKey("diagnosis"));
		assertEquals("DESCARTADO", rows.get(0).get("diagnosisType"));
		assertEquals(true, rows.get(0).get("investigationForm"));
		assertEquals("2026-01-03", rows.get(0).get("notificationDate"));
		verify(f.dao).weeklyCases(calendar.date(LocalDate.of(2025, 12, 28)), calendar.date(LocalDate.of(2026, 1, 4)));
		verify(f.access).require(SurveillanceConstants.VIEW);
		verify(f.access).require(SurveillanceConstants.REPORT);
		f.encounterDiagnosis.setVoided(true);
		assertTrue(((List<?>) f.service.weeklyRecords(2025, 53).get("rows")).isEmpty());
		f.encounterDiagnosis.setVoided(false);
		f.source.setVoided(true);
		assertTrue(((List<?>) f.service.weeklyRecords(2025, 53).get("rows")).isEmpty());
	}
	
	@Test
	public void rejectsInvalidWeeksAndDeniedAccessBeforeReadingCases() {
		SyntheticFixture f = new SyntheticFixture();
		for (int week : new int[] { 0, 53, 54 }) {
			try {
				f.service.weeklyRecords(2026, week);
				fail();
			}
			catch (SurveillanceException expected) {
				assertEquals("INVALID_PERIOD", expected.getCode());
			}
		}
		doThrow(new SurveillanceException(403, "ACCESS_DENIED")).when(f.access).require(SurveillanceConstants.REPORT);
		try {
			f.service.weeklyRecords(2026, 1);
			fail();
		}
		catch (SurveillanceException expected) {
			assertEquals(403, expected.getStatus());
		}
		verify(f.dao, never()).weeklyCases(any(Date.class), any(Date.class));
	}
	
	@Test
	public void returnsEthnicityNameAndNeverSubstitutesOtherIdentifiersForDni() {
		SyntheticFixture f = new SyntheticFixture();
		f.m.ethnicityAttributeTypeUuid = "ethnicity";
		PersonAttributeType type = new PersonAttributeType(1);
		type.setFormat("org.openmrs.Concept");
		when(f.clinical.attributeType("ethnicity")).thenReturn(type);
		Concept ethnicity = SyntheticFixture.concept(501);
		ethnicity.addName(new ConceptName("Synthetic ethnicity", Locale.ENGLISH));
		when(f.clinical.concept(501)).thenReturn(ethnicity);
		f.patient.addAttribute(new PersonAttribute(type, "501"));
		SurveillanceCase value = row(f, "2026-01-05T12:00:00Z");
		when(f.dao.weeklyCases(any(Date.class), any(Date.class))).thenReturn(Collections.singletonList(value));
		List<Map<String, Object>> rows = (List<Map<String, Object>>) f.service.weeklyRecords(2026, 1).get("rows");
		assertEquals("Synthetic ethnicity", rows.get(0).get("ethnicity"));
		assertNull(rows.get(0).get("dni"));
	}
	
	private SurveillanceCase row(SyntheticFixture f, String notification) {
		SurveillanceCase value = new SurveillanceCase();
		value.setPatient(f.patient);
		value.setEncounter(f.source);
		value.setDiagnosis(f.encounterDiagnosis);
		value.setLocation(f.location);
		value.setNotificationDate(Date.from(Instant.parse(notification)));
		return value;
	}
}
