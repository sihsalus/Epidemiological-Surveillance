package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;

import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.SurveillanceCase;

public class IndicatorsTest {
	
	@Test
	public void migrationCreatesMondayNoonAndPreservesSavedSettingsOnRerun() throws Exception {
		try (java.sql.Connection connection = java.sql.DriverManager
		        .getConnection("jdbc:h2:mem:deadline-" + UUID.randomUUID(), "sa", "")) {
			liquibase.Liquibase migration = new liquibase.Liquibase("liquibase-notification-config.xml",
			        new liquibase.resource.ClassLoaderResourceAccessor(), liquibase.database.DatabaseFactory.getInstance()
			                .findCorrectDatabaseImplementation(new liquibase.database.jvm.JdbcConnection(connection)));
			migration.update("");
			java.sql.Statement sql = connection.createStatement();
			java.sql.ResultSet row = sql
			        .executeQuery("select weekday, hour, minute from surveillance_notification_config where id = 1");
			assertTrue(row.next());
			assertEquals(1, row.getInt(1));
			assertEquals(12, row.getInt(2));
			assertEquals(0, row.getInt(3));
			sql.executeUpdate("update surveillance_notification_config set weekday = 2, hour = 9, minute = 30 where id = 1");
			connection.commit();
			migration.update("");
			row = sql.executeQuery("select weekday, hour, minute from surveillance_notification_config where id = 1");
			assertTrue(row.next());
			assertEquals(2, row.getInt(1));
			assertEquals(9, row.getInt(2));
			assertEquals(30, row.getInt(3));
		}
	}
	
	private SurveillanceCase record(SyntheticFixture f, String care, String notified) {
		SurveillanceCase value = new SurveillanceCase();
		Encounter encounter = new Encounter();
		encounter.setEncounterDatetime(Date.from(Instant.parse(care)));
		value.setEncounter(encounter);
		value.setDiagnosis(f.encounterDiagnosis);
		value.setPatient(f.patient);
		value.setDiagnosisType("CONFIRMADO");
		value.setOnsetDate(java.sql.Date.valueOf("2025-12-01"));
		value.setOrigin("AUTOCTONO");
		value.setInfectionAddress(10);
		if (notified != null)
			value.setNotificationDate(Date.from(Instant.parse(notified)));
		return value;
	}
	
	private Map<String, Object> report(SyntheticFixture f, String type, String indicator) {
		when(f.dao.events()).thenReturn(Collections.singletonList(f.event));
		return f.service.indicators(f.event.getUuid(), "2026-01-01", "2026-01-31", "CENTRO_POBLADO", null, type, indicator);
	}
	
	@Test
	public void immediateRequiresNotificationAndStrictlyLessThan24Hours() {
		SyntheticFixture f = new SyntheticFixture();
		IndicatorCalculator calculator = new IndicatorCalculator();
		String care = "2026-01-20T15:00:00Z";
		for (String time : Arrays.asList(null, "2026-01-20T14:59:59Z", "2026-01-21T15:00:00Z", "2026-01-21T15:00:01Z"))
			assertFalse(calculator.timely(record(f, care, time), "INMEDIATA", f.m, new int[] { 1, 12, 0 }));
		assertTrue(calculator.timely(record(f, care, "2026-01-21T14:59:59Z"), "INMEDIATA", f.m, new int[] { 1, 12, 0 }));
	}
	
	@Test
	public void weeklyUsesEpidemiologicalWeekTimezoneAndStrictDeadlineAcrossYears() {
		SyntheticFixture f = new SyntheticFixture();
		f.m.firstDayOfWeek = "SUNDAY";
		IndicatorCalculator calculator = new IndicatorCalculator();
		// Wednesday Dec 31 belongs to the week ending Saturday Jan 3. Due Monday Jan 5 at noon Lima.
		String care = "2025-12-31T15:00:00Z";
		assertTrue(calculator.timely(record(f, care, "2026-01-05T16:59:59Z"), "SEMANAL", f.m, new int[] { 1, 12, 0 }));
		assertFalse(calculator.timely(record(f, care, "2026-01-05T17:00:00Z"), "SEMANAL", f.m, new int[] { 1, 12, 0 }));
		assertTrue(calculator.timely(record(f, care, "2026-01-05T17:00:00Z"), "SEMANAL", f.m, new int[] { 2, 9, 30 }));
		// Sunday in UTC is still Saturday in Lima: use the preceding epidemiological week.
		assertFalse(calculator.timely(record(f, "2026-01-04T04:00:00Z", "2026-01-05T17:00:00Z"), "SEMANAL", f.m,
		    new int[] { 1, 12, 0 }));
	}
	
	@Test
	public void completenessIncludesIncompleteCasesAndUsesCareNotOnsetForPeriod() {
		SyntheticFixture f = new SyntheticFixture();
		SurveillanceCase complete = record(f, "2026-01-20T15:00:00Z", "2026-01-21T15:00:00Z");
		List<SurveillanceCase> cases = new ArrayList<SurveillanceCase>();
		cases.add(complete);
		for (int missing = 0; missing < 4; missing++) {
			SurveillanceCase value = record(f, "2026-01-20T15:00:00Z", "2026-01-21T15:00:00Z");
			value.setDiagnosisType("PROBABLE");
			if (missing == 0)
				value.setOnsetDate(null);
			if (missing == 1)
				value.setNotificationDate(null);
			if (missing == 2)
				value.setInfectionAddress(null);
			if (missing == 3)
				value.setOrigin(null);
			cases.add(value);
		}
		SurveillanceCase discarded = record(f, "2026-01-20T15:00:00Z", null);
		discarded.setDiagnosisType("DESCARTADO");
		cases.add(discarded);
		cases.add(record(f, "2026-02-01T05:00:00Z", null));
		when(f.dao.surveillanceCases()).thenReturn(cases);
		assertEquals(5, report(f, "TODOS", "completeness").get("denominator"));
		assertEquals(20.0, report(f, "TODOS", "completeness").get("percentage"));
		assertEquals(4, report(f, "PROBABLE", "completeness").get("denominator"));
		assertEquals(100.0, report(f, "CONFIRMADO", "completeness").get("percentage"));
		verify(f.access, atLeastOnce()).require(SurveillanceConstants.REPORT);
	}
	
	@Test
	public void fatalityCountsOnlyAttributedDeathsAndRejectsNonConfirmedFilter() {
		SyntheticFixture f = new SyntheticFixture();
		f.patient.setDeathDate(java.sql.Date.valueOf("2026-01-22"));
		List<SurveillanceCase> cases = new ArrayList<SurveillanceCase>();
		for (String choice : Arrays.asList("SI", "NO", "POR_DETERMINAR", null)) {
			SurveillanceCase value = record(f, "2026-01-20T15:00:00Z", null);
			value.setDeathAttribution(choice);
			cases.add(value);
		}
		SurveillanceCase probable = record(f, "2026-01-20T15:00:00Z", null);
		probable.setDiagnosisType("PROBABLE");
		probable.setDeathAttribution("SI");
		cases.add(probable);
		when(f.dao.surveillanceCases()).thenReturn(cases);
		assertEquals(25.0, report(f, "CONFIRMADO", "fatality").get("percentage"));
		try {
			report(f, "TODOS", "fatality");
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals("INVALID_DIAGNOSIS_TYPE", ex.getCode());
		}
	}
	
	@Test
	public void territoryFiltersAndEmptyCohort() {
		SyntheticFixture f = new SyntheticFixture();
		SurveillanceCase value = record(f, "2026-01-20T15:00:00Z", null);
		when(f.dao.events()).thenReturn(Collections.singletonList(f.event));
		when(f.dao.surveillanceCases()).thenReturn(Collections.singletonList(value));
		when(f.dao.addressId("selected", "ADDRESS_1")).thenReturn(5);
		when(f.dao.ancestorIdForPopulatedCenter(10, "ADDRESS_1")).thenReturn(6);
		Map<String, Object> result = f.service.indicators(f.event.getUuid(), "2026-01-01", "2026-01-31", "DEPARTAMENTO",
		    "selected", "TODOS", "completeness");
		assertEquals(0, result.get("denominator"));
		assertNull(result.get("percentage"));
		when(f.dao.ancestorIdForPopulatedCenter(10, "ADDRESS_1")).thenReturn(5);
		assertEquals(1, f.service.indicators(f.event.getUuid(), "2026-01-01", "2026-01-31", "DEPARTAMENTO", "selected",
		    "TODOS", "completeness").get("denominator"));
	}
	
	@Test
	public void settingsDefaultValidateAndPersist() {
		SyntheticFixture f = new SyntheticFixture();
		assertArrayEquals(new int[] { 1, 12, 0 }, f.service.getNotificationSettings());
		assertArrayEquals(new int[] { 7, 23, 59 }, f.service.saveNotificationSettings(Arrays.asList(7, 23, 59)));
		verify(f.dao).saveNotificationSettings(7, 23, 59);
		verify(f.access).require(SurveillanceConstants.MANAGE);
		for (List<Integer> invalid : Arrays.asList(Arrays.asList(0, 12, 0), Arrays.asList(8, 12, 0), Arrays.asList(1, 24, 0),
		    Arrays.asList(1, 12, 60), Arrays.asList(1, null, 0))) {
			try {
				f.service.saveNotificationSettings(invalid);
				fail();
			}
			catch (SurveillanceException ex) {
				assertEquals("INVALID_NOTIFICATION_SETTINGS", ex.getCode());
			}
		}
	}
	
	@Test
	public void caseFieldsAreRequiredAndProbableDeathsAreNotAttributed() {
		SyntheticFixture f = new SyntheticFixture();
		f.surveillanceRequest.vaccinationStatus = null;
		try {
			f.service.createDraft(f.surveillanceRequest);
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals("REQUIRED_FIELDS", ex.getCode());
		}
		f.surveillanceRequest.vaccinationStatus = "IGN";
		f.surveillanceRequest.surveillanceType = null;
		try {
			f.service.createDraft(f.surveillanceRequest);
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals("REQUIRED_FIELDS", ex.getCode());
		}
		f.surveillanceRequest.surveillanceType = "PASIVA";
		f.patient.setDeathDate(java.sql.Date.valueOf("2026-01-22"));
		f.surveillanceRequest.deathAttribution = "SI";
		assertNull(f.service.createDraft(f.surveillanceRequest).deathAttribution);
	}
}
