package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openmrs.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

/** Alternative outcomes of the conditions used by case queries, case details and weekly records. */
public class ServiceBranchTest {
	
	private static void assertCode(String code, Runnable action) {
		try {
			action.run();
			fail("Expected " + code);
		}
		catch (SurveillanceException ex) {
			assertEquals(code, ex.getCode());
		}
	}
	
	/** Registers a valid synthetic case and returns the persisted entity. */
	private static SurveillanceCase saved(SyntheticFixture f) {
		f.service.createDraft(f.surveillanceRequest);
		ArgumentCaptor<SurveillanceCase> capture = ArgumentCaptor.forClass(SurveillanceCase.class);
		verify(f.dao, atLeastOnce()).save(capture.capture());
		SurveillanceCase value = capture.getValue();
		value.setUuid(SyntheticFixture.uuid(900));
		return value;
	}
	
	private static SurveillanceCase copy(SurveillanceCase base) {
		SurveillanceCase value = new SurveillanceCase();
		value.setUuid(UUID.randomUUID().toString());
		value.setPatient(base.getPatient());
		value.setEncounter(base.getEncounter());
		value.setProvider(base.getProvider());
		value.setLocation(base.getLocation());
		value.setDiagnosis(base.getDiagnosis());
		value.setOnsetDate(base.getOnsetDate());
		value.setNotificationDate(base.getNotificationDate());
		value.setDiagnosisType(base.getDiagnosisType());
		value.setPatientSex(base.getPatientSex());
		value.setPatientAge(base.getPatientAge());
		return value;
	}
	
	private static Date date(String value) {
		return java.sql.Date.valueOf(value);
	}
	
	private static Diagnosis diagnosis(Concept coded) {
		Diagnosis value = new Diagnosis();
		value.setVoided(false);
		value.setDiagnosis(coded == null ? null : new CodedOrFreeText(coded, null, null));
		return value;
	}
	
	@Test
	public void caseListFiltersEveryCriterionAndRejectsAnEmptyNotificationRange() {
		SyntheticFixture f = new SyntheticFixture();
		SurveillanceCase base = saved(f);
		assertCode("INVALID_DATE_RANGE",
		    () -> f.service.listCases(null, null, null, null, "2026-01-22", "2026-01-21", null));
		
		Patient other = new Patient(77);
		other.setUuid(SyntheticFixture.uuid(77));
		SurveillanceCase otherPatient = copy(base);
		otherPatient.setPatient(other);
		SurveillanceCase withoutPatient = copy(base);
		withoutPatient.setPatient(null);
		when(f.dao.surveillanceCases()).thenReturn(Arrays.asList(base, otherPatient, withoutPatient));
		assertEquals(1, f.service.listCases(f.patient.getUuid(), null, null, null, null, null, null).size());
		
		SurveillanceCase withoutDiagnosis = copy(base);
		withoutDiagnosis.setDiagnosis(null);
		SurveillanceCase freeText = copy(base);
		freeText.setDiagnosis(diagnosis(null));
		SurveillanceCase uncoded = copy(base);
		Diagnosis nonCoded = diagnosis(null);
		nonCoded.setDiagnosis(new CodedOrFreeText(null, null, "texto libre"));
		uncoded.setDiagnosis(nonCoded);
		when(f.dao.surveillanceCases()).thenReturn(Arrays.asList(base, withoutDiagnosis, freeText, uncoded));
		assertEquals(1, f.service.listCases(null, f.diagnosis.getUuid(), null, null, null, null, null).size());
		
		SurveillanceCase withoutOnset = copy(base);
		withoutOnset.setOnsetDate(null);
		SurveillanceCase early = copy(base);
		early.setOnsetDate(date("2026-01-05"));
		SurveillanceCase late = copy(base);
		late.setOnsetDate(date("2026-01-30"));
		when(f.dao.surveillanceCases()).thenReturn(Arrays.asList(base, withoutOnset, early, late));
		assertEquals(2, f.service.listCases(null, null, "2026-01-10", null, null, null, null).size());
		assertEquals(2, f.service.listCases(null, null, null, "2026-01-25", null, null, null).size());
		assertEquals(1, f.service.listCases(null, null, "2026-01-10", "2026-01-25", null, null, null).size());
		
		SurveillanceCase withoutNotification = copy(base);
		withoutNotification.setNotificationDate(null);
		SurveillanceCase earlyNotice = copy(base);
		earlyNotice.setNotificationDate(date("2026-01-02"));
		SurveillanceCase lateNotice = copy(base);
		lateNotice.setNotificationDate(date("2026-02-15"));
		when(f.dao.surveillanceCases()).thenReturn(Arrays.asList(base, withoutNotification, earlyNotice, lateNotice));
		assertEquals(2, f.service.listCases(null, null, null, null, "2026-01-10", null, null).size());
		assertEquals(2, f.service.listCases(null, null, null, null, null, "2026-01-31", null).size());
		
		when(f.dao.surveillanceCases()).thenReturn(Collections.singletonList(base));
		f.patient.setDeathDate(null);
		assertEquals(1, f.service.listCases(null, null, null, null, null, null, false).size());
		f.patient.setDeathDate(date("2026-01-22"));
		assertEquals(1, f.service.listCases(null, null, null, null, null, null, true).size());
		assertEquals(0, f.service.listCases(null, null, null, null, null, null, false).size());
	}
	
	@Test
	public void caseDetailsToleratesMissingOptionalClinicalData() {
		SyntheticFixture f = new SyntheticFixture();
		SurveillanceCase value = saved(f);
		when(f.dao.byUuid(SurveillanceCase.class, value.getUuid())).thenReturn(value);
		
		PatientIdentifier voided = new PatientIdentifier("11111111", new PatientIdentifierType(), f.location);
		voided.setVoided(true);
		PatientIdentifier blank = new PatientIdentifier(" ", new PatientIdentifierType(), f.location);
		blank.setVoided(false);
		PatientIdentifier valid = new PatientIdentifier("22222222", new PatientIdentifierType(), f.location);
		valid.setVoided(false);
		f.patient.setIdentifiers(new LinkedHashSet<PatientIdentifier>(Arrays.asList(voided, blank, valid)));
		f.patient.setBirthdate(null);
		f.patient.setDeathDate(date("2026-01-22"));
		Encounter bare = new Encounter(55);
		bare.setUuid(SyntheticFixture.uuid(55));
		bare.setPatient(f.patient);
		value.setEncounter(bare);
		value.setDiagnosis(diagnosis(null));
		TestOrder order = new TestOrder();
		order.setUuid(SyntheticFixture.uuid(56));
		value.setTestOrder(order);
		Obs text = new Obs();
		text.setUuid(SyntheticFixture.uuid(57));
		text.setVoided(false);
		text.setValueText("Positivo");
		value.setLaboratoryObservation(text);
		value.setInvestigationDate(null);
		value.setDiagnosisType("CONFIRMADO");
		value.setDeathAttribution(null);
		
		SurveillanceCaseResponse response = f.service.getDraft(value.getUuid());
		assertEquals("22222222", response.patientIdentifier);
		assertNull(response.patientBirthDate);
		assertNull(response.encounterDisplay);
		assertNull(response.encounterDate);
		assertNull(response.diagnosisDisplay);
		assertNull(response.diagnosisIcd10);
		assertNull(response.testOrderDisplay);
		assertEquals("Positivo", response.laboratoryObservationDisplay);
		assertNull(response.investigationDate);
		assertEquals("POR_DETERMINAR", response.deathAttribution);
		
		value.setDiagnosis(diagnosis(null));
		value.getDiagnosis().setDiagnosis(new CodedOrFreeText(null, null, "texto libre"));
		text.setVoided(true);
		value.setDeathAttribution("SI");
		response = f.service.getDraft(value.getUuid());
		assertNull(response.diagnosisDisplay);
		assertNull(response.laboratoryObservationUuid);
		assertNull(response.laboratoryObservationDisplay);
		assertEquals("SI", response.deathAttribution);
		
		value.setLaboratoryObservation(null);
		value.setDiagnosisType("PROBABLE");
		response = f.service.getDraft(value.getUuid());
		assertNull(response.laboratoryObservationUuid);
		assertNull(response.deathAttribution);
		
		value.setVoided(true);
		assertCode("CASE_NOT_FOUND", () -> f.service.getDraft(value.getUuid()));
	}
	
	@Test
	public void weeklyRecordsSkipIncompleteCasesAndReadOnlyTheDniIdentifier() {
		SyntheticFixture f = new SyntheticFixture();
		SurveillanceCase base = saved(f);
		base.setVoided(false);
		PatientIdentifierType dniType = new PatientIdentifierType();
		dniType.setName("DNI");
		PatientIdentifierType otherType = new PatientIdentifierType();
		otherType.setName("Historia clínica");
		PatientIdentifier voided = new PatientIdentifier("11111111", dniType, f.location);
		voided.setVoided(true);
		PatientIdentifier untyped = new PatientIdentifier("33333333", null, f.location);
		untyped.setVoided(false);
		PatientIdentifier history = new PatientIdentifier("HC-1", otherType, f.location);
		history.setVoided(false);
		PatientIdentifier dni = new PatientIdentifier("44444444", dniType, f.location);
		dni.setVoided(false);
		f.patient.setIdentifiers(new LinkedHashSet<PatientIdentifier>(Arrays.asList(voided, untyped, history, dni)));
		
		List<SurveillanceCase> cases = new ArrayList<SurveillanceCase>();
		cases.add(base);
		SurveillanceCase uncoded = copy(base);
		uncoded.setDiagnosis(diagnosis(null));
		cases.add(uncoded);
		SurveillanceCase c;
		cases.add(c = copy(base));
		c.setVoided(true);
		cases.add(c = copy(base));
		c.setPatient(null);
		Patient voidedPatient = new Patient(78);
		voidedPatient.setVoided(true);
		cases.add(c = copy(base));
		c.setPatient(voidedPatient);
		cases.add(c = copy(base));
		c.setEncounter(null);
		Encounter voidedEncounter = new Encounter(79);
		voidedEncounter.setVoided(true);
		cases.add(c = copy(base));
		c.setEncounter(voidedEncounter);
		cases.add(c = copy(base));
		c.setDiagnosis(null);
		Diagnosis voidedDiagnosis = diagnosis(f.diagnosis);
		voidedDiagnosis.setVoided(true);
		cases.add(c = copy(base));
		c.setDiagnosis(voidedDiagnosis);
		cases.add(c = copy(base));
		c.setNotificationDate(null);
		cases.add(c = copy(base));
		c.setNotificationDate(date("2026-01-10"));
		cases.add(c = copy(base));
		c.setNotificationDate(date("2026-01-30"));
		when(f.dao.weeklyCases(any(Date.class), any(Date.class))).thenReturn(cases);
		
		LocalDate weekStart = new EpidemiologicalCalendar(f.m).weekStart(2026, 3);
		assertEquals(LocalDate.of(2026, 1, 18), weekStart);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> rows = (List<Map<String, Object>>) f.service.weeklyRecords(2026, 3).get("rows");
		assertEquals(2, rows.size());
		assertEquals("44444444", rows.get(0).get("dni"));
		assertNull(rows.get(1).get("icd10"));
	}
	
	private static SurveillanceAlert alert(String uuid, String type, String reason, String status, String caseUuid) {
		SurveillanceAlert value = new SurveillanceAlert();
		value.setUuid(uuid);
		value.setType(type);
		value.setReason(reason);
		value.setStatus(status);
		value.setCaseUuid(caseUuid);
		value.setGeneratedAt(new Date());
		return value;
	}
	
	@Test
	public void alertFiltersAndAttendantsCoverEveryAlternative() {
		SyntheticFixture f = new SyntheticFixture();
		assertCode("INVALID_CLASSIFICATION", () -> f.service.alerts(null, "UNKNOWN", null));
		assertCode("INVALID_CLASSIFICATION", () -> f.service.alerts(null, null, "UNKNOWN"));
		SurveillanceCase deleted = new SurveillanceCase();
		deleted.setVoided(true);
		SurveillanceCase orphan = new SurveillanceCase();
		SurveillanceCase hidden = new SurveillanceCase();
		Patient voidedPatient = new Patient(81);
		voidedPatient.setVoided(true);
		hidden.setPatient(voidedPatient);
		when(f.dao.byUuid(SurveillanceCase.class, "deleted")).thenReturn(deleted);
		when(f.dao.byUuid(SurveillanceCase.class, "orphan")).thenReturn(orphan);
		when(f.dao.byUuid(SurveillanceCase.class, "hidden")).thenReturn(hidden);
		SurveillanceAlert unnamed = alert("a1", "IMMEDIATE", "IMMEDIATE_EVENT", "ATTENDED", "deleted");
		unnamed.setAttendedBy("nameless");
		unnamed.setAttendedAt(new Date());
		SurveillanceAlert removedUser = alert("a2", "OUTBREAK", "EPIDEMIC_THRESHOLD", "ATTENDED", "orphan");
		removedUser.setAttendedBy("removed");
		SurveillanceAlert pending = alert("a3", "IMMEDIATE", "DEATH", "PENDING", "hidden");
		SurveillanceAlert missing = alert("a4", "OUTBREAK", "FOCUS_CASES", "PENDING", "absent");
		User nameless = new User(90);
		nameless.setUsername("synthetic.user");
		when(f.dao.alertAttendant("nameless")).thenReturn(nameless);
		when(f.dao.alerts()).thenReturn(Arrays.asList(unnamed, removedUser, pending, missing));
		
		List<Map<String, Object>> all = f.service.alerts(null, null, null);
		assertEquals(4, all.size());
		assertEquals("synthetic.user", all.get(0).get("attendedBy"));
		assertNull(all.get(1).get("attendedBy"));
		assertEquals(2, f.service.alerts("IMMEDIATE", null, null).size());
		assertEquals(1, f.service.alerts(null, "DEATH", null).size());
		assertEquals(2, f.service.alerts(null, null, "PENDING").size());
		assertEquals(1, f.service.alerts("OUTBREAK", "FOCUS_CASES", "PENDING").size());
		assertEquals(0, f.service.alerts("OUTBREAK", "DEATH", null).size());
		assertEquals(0, f.service.alerts("IMMEDIATE", "DEATH", "ATTENDED").size());
	}
	
	@Test
	public void configurationValidationRejectsEachInvalidValue() {
		SyntheticFixture f = new SyntheticFixture();
		when(f.dao.notificationSettings()).thenReturn(null).thenReturn(new int[] { 2, 9, 30 });
		assertArrayEquals(new int[] { 1, 12, 0 }, f.service.getNotificationSettings());
		assertArrayEquals(new int[] { 2, 9, 30 }, f.service.getNotificationSettings());
		for (List<Integer> invalid : Arrays.<List<Integer>> asList(null, Arrays.asList(1, 2), Arrays.asList(1, null, 0),
		    Arrays.asList(0, 12, 0), Arrays.asList(8, 12, 0), Arrays.asList(1, -1, 0), Arrays.asList(1, 24, 0),
		    Arrays.asList(1, 12, -1), Arrays.asList(1, 12, 60)))
			assertCode("INVALID_NOTIFICATION_SETTINGS", () -> f.service.saveNotificationSettings(invalid));
		assertArrayEquals(new int[] { 7, 23, 59 }, f.service.saveNotificationSettings(Arrays.asList(7, 23, 59)));
		
		when(f.dao.endemicSettings()).thenReturn(null).thenReturn(new int[] { 4, 2 });
		assertArrayEquals(new int[] { 5, 3 }, f.service.getEndemicSettings());
		assertArrayEquals(new int[] { 4, 2 }, f.service.getEndemicSettings());
		for (List<Integer> invalid : Arrays.<List<Integer>> asList(null, Arrays.asList(5), Arrays.asList(5, null),
		    Arrays.asList(0, 1), Arrays.asList(9999, 1), Arrays.asList(5, 0), Arrays.asList(3, 4)))
			assertCode("INVALID_ENDEMIC_SETTINGS", () -> f.service.saveEndemicSettings(invalid));
		assertArrayEquals(new int[] { 5, 5 }, f.service.saveEndemicSettings(Arrays.asList(5, 5)));
		
		assertCode("INVALID_RISK_LEVELS", () -> f.service.saveRiskThresholds(null));
	}
	
	private interface Mutation {
		
		void apply(SyntheticFixture f);
	}
	
	private static void rejects(String code, Mutation mutation) {
		SyntheticFixture f = new SyntheticFixture();
		mutation.apply(f);
		assertCode(code, () -> f.service.createDraft(f.surveillanceRequest));
	}
	
	@Test
	public void registrationRejectsEachInconsistentReference() {
		Patient stranger = new Patient(82);
		stranger.setUuid(SyntheticFixture.uuid(82));
		stranger.setVoided(false);
		rejects("INVALID_REFERENCE", f -> f.patient.setVoided(true));
		rejects("INVALID_REFERENCE", f -> when(f.clinical.encounter(f.source.getUuid())).thenReturn(null));
		rejects("INVALID_REFERENCE", f -> f.source.setVoided(true));
		rejects("INVALID_REFERENCE", f -> f.source.setPatient(stranger));
		rejects("INVALID_REFERENCE", f -> f.provider.setRetired(true));
		rejects("INVALID_REFERENCE", f -> f.provider.setPerson(null));
		rejects("INVALID_REFERENCE", f -> f.location.setRetired(true));
		rejects("INVALID_REFERENCE", f -> f.source.setLocation(new Location(83)));
		rejects("INVALID_REFERENCE", f -> f.encounterDiagnosis.setVoided(true));
		rejects("INVALID_REFERENCE", f -> f.encounterDiagnosis.setEncounter(new Encounter(84)));
		rejects("INVALID_CLASSIFICATION", f -> f.surveillanceRequest.origin = "DESCONOCIDO");
		rejects("INVALID_CLASSIFICATION", f -> f.surveillanceRequest.vaccinationStatus = "TAL_VEZ");
		rejects("INVALID_CLASSIFICATION", f -> f.surveillanceRequest.surveillanceType = "PASIVA_ACTIVA");
		rejects("INVALID_LAB_RESULT", f -> f.surveillanceRequest.testOrderUuid = SyntheticFixture.uuid(85));
		rejects("INVALID_LAB_RESULT", f -> {
			TestOrder order = new TestOrder();
			order.setVoided(true);
			when(f.clinical.order(SyntheticFixture.uuid(85))).thenReturn(order);
			f.surveillanceRequest.testOrderUuid = SyntheticFixture.uuid(85);
		});
		rejects("INVALID_LAB_RESULT", f -> f.surveillanceRequest.laboratoryObservationUuid = SyntheticFixture.uuid(86));
		rejects("INVALID_LAB_RESULT", f -> {
			Obs obs = f.lab(true);
			obs.setVoided(true);
			f.surveillanceRequest.laboratoryObservationUuid = obs.getUuid();
		});
		rejects("INVALID_LAB_RESULT", f -> {
			Obs obs = f.lab(true);
			obs.setPerson(stranger);
			f.surveillanceRequest.laboratoryObservationUuid = obs.getUuid();
		});
		rejects("INVALID_LAB_RESULT", f -> {
			Obs obs = f.lab(true);
			obs.setEncounter(new Encounter(87));
			f.surveillanceRequest.laboratoryObservationUuid = obs.getUuid();
		});
		rejects("INVALID_LAB_RESULT", f -> {
			Obs obs = f.lab(true);
			TestOrder other = new TestOrder();
			other.setPatient(f.patient);
			when(f.clinical.order(SyntheticFixture.uuid(88))).thenReturn(other);
			f.surveillanceRequest.testOrderUuid = SyntheticFixture.uuid(88);
			f.surveillanceRequest.laboratoryObservationUuid = obs.getUuid();
		});
		rejects("INVALID_LAB_RESULT", f -> {
			Obs obs = f.lab(true);
			((TestOrder) obs.getOrder()).setPatient(stranger);
			f.surveillanceRequest.laboratoryObservationUuid = obs.getUuid();
		});
		rejects("INVALID_LAB_RESULT", f -> {
			Obs obs = f.lab(true);
			obs.getOrder().setVoided(true);
			f.surveillanceRequest.laboratoryObservationUuid = obs.getUuid();
		});
	}
	
	@Test
	public void editingRejectsAnotherEncounterOrDiagnosisForTheSameCase() {
		for (int variant = 0; variant < 2; variant++) {
			SyntheticFixture f = new SyntheticFixture();
			SurveillanceCase existing = saved(f);
			Encounter otherEncounter = new Encounter(89);
			Diagnosis otherDiagnosis = diagnosis(f.diagnosis);
			if (variant == 0)
				existing.setEncounter(otherEncounter);
			else
				existing.setDiagnosis(otherDiagnosis);
			when(f.dao.byUuid(SurveillanceCase.class, existing.getUuid())).thenReturn(existing);
			assertCode("INVALID_REFERENCE", () -> f.service.updateDraft(existing.getUuid(), f.surveillanceRequest));
		}
	}
	
	/** Cases that every report must leave out, each with a single missing or excluded attribute. */
	private static List<SurveillanceCase> cohort(SyntheticFixture f) {
		SurveillanceCase base = new SurveillanceCase();
		base.setUuid(SyntheticFixture.uuid(950));
		base.setPatient(f.patient);
		base.setEncounter(f.source);
		base.setProvider(f.provider);
		base.setLocation(f.location);
		base.setDiagnosis(f.encounterDiagnosis);
		base.setDiagnosisType("CONFIRMADO");
		base.setOnsetDate(date("2026-01-20"));
		base.setNotificationDate(date("2026-01-21"));
		base.setInfectionAddress(10);
		base.setPatientAge(30);
		base.setPatientSex("F");
		base.setOrigin("AUTOCTONO");
		List<SurveillanceCase> cases = new ArrayList<SurveillanceCase>();
		cases.add(base);
		SurveillanceCase c;
		cases.add(c = copy(base));
		c.setVoided(true);
		cases.add(c = copy(base));
		c.setDiagnosis(null);
		Diagnosis voided = diagnosis(f.diagnosis);
		voided.setVoided(true);
		cases.add(c = copy(base));
		c.setDiagnosis(voided);
		cases.add(c = copy(base));
		c.setDiagnosis(diagnosis(null));
		Diagnosis freeText = diagnosis(null);
		freeText.setDiagnosis(new CodedOrFreeText(null, null, "texto"));
		cases.add(c = copy(base));
		c.setDiagnosis(freeText);
		cases.add(c = copy(base));
		c.setEncounter(null);
		Encounter voidedEncounter = new Encounter(91);
		voidedEncounter.setVoided(true);
		cases.add(c = copy(base));
		c.setEncounter(voidedEncounter);
		Encounter undated = new Encounter(92);
		undated.setVoided(false);
		cases.add(c = copy(base));
		c.setEncounter(undated);
		cases.add(c = copy(base));
		c.setDiagnosisType("DESCARTADO");
		cases.add(c = copy(base));
		c.setDiagnosisType("PROBABLE");
		cases.add(c = copy(base));
		c.setInfectionAddress(null);
		cases.add(c = copy(base));
		c.setOnsetDate(null);
		cases.add(c = copy(base));
		c.setInfectionAddress(11);
		cases.add(c = copy(base));
		c.setPatient(null);
		return cases;
	}
	
	@Test
	public void reportsLeaveOutIncompleteCasesAndValidateTheirParameters() {
		SyntheticFixture f = new SyntheticFixture();
		String event = f.event.getUuid();
		when(f.dao.surveillanceCases()).thenReturn(cohort(f));
		when(f.dao.addressId("district", "COUNTY_DISTRICT")).thenReturn(20);
		when(f.dao.addressId("center", "CITY_VILLAGE")).thenReturn(10);
		when(f.dao.districtIdForPopulatedCenter(10)).thenReturn(20);
		when(f.dao.districtIdForPopulatedCenter(11)).thenReturn(21);
		when(f.dao.ancestorIdForPopulatedCenter(10, "COUNTY_DISTRICT")).thenReturn(20);
		when(f.dao.ancestorIdForPopulatedCenter(11, "COUNTY_DISTRICT")).thenReturn(21);
		
		assertNotNull(
		    f.service.indicators(event, "2026-01-01", "2026-01-31", "DISTRITO", "district", "CONFIRMADO", "completeness"));
		assertNotNull(
		    f.service.indicators(event, "2026-01-01", "2026-01-31", "CENTRO_POBLADO", "center", "TODOS", "timeliness"));
		assertNotNull(
		    f.service.indicators(event, "2026-01-21", "2026-01-31", "CENTRO_POBLADO", null, "TODOS", "completeness"));
		assertCode("INVALID_DATE_RANGE",
		    () -> f.service.indicators(event, "2026-01-01", "2999-01-31", "CENTRO_POBLADO", null, "TODOS", "completeness"));
		
		assertNotNull(f.service.geography(event, "2026-01-01", "2026-01-31", "DISTRITO", "district", "CONFIRMADO",
		    "ethnicity", "mes"));
		assertNotNull(
		    f.service.geography(event, "2026-01-01", "2026-01-31", "CENTRO_POBLADO", "center", "TODOS", "origin", "mes"));
		assertNotNull(f.service.geography(event, "2026-01-21", "2026-01-31", "CENTRO_POBLADO", null, "TODOS", "sex", "mes"));
		assertCode("INVALID_DEMOGRAPHIC_DIMENSIONS",
		    () -> f.service.geography(event, "2026-01-01", "2026-01-31", "CENTRO_POBLADO", null, "TODOS", "sex", "decada"));
		assertCode("INVALID_DEMOGRAPHIC_DIMENSIONS",
		    () -> f.service.geography(event, "2026-01-01", "2026-01-31", "CENTRO_POBLADO", null, "TODOS", "planeta", "mes"));
		assertCode("INVALID_GEOGRAPHIC_RANGE",
		    () -> f.service.geography(event, "2025-12-01", "2026-01-31", "CENTRO_POBLADO", null, "TODOS", "origin", "mes"));
		assertCode("INVALID_GEOGRAPHIC_RANGE",
		    () -> f.service.geography(event, "2026-01-01", "2026-12-31", "CENTRO_POBLADO", null, "TODOS", "origin", "mes"));
		
		assertNotNull(
		    f.service.demographics("2026-01-01", "2026-01-31", "ethnicity", "sex", "mes", event, null, "CONFIRMADO"));
		assertNotNull(f.service.demographics("2026-01-01", "2026-01-31", "sex", "ethnicity", "mes", event, null, "TODOS"));
		assertCode("INVALID_DATE_RANGE",
		    () -> f.service.demographics("2026-01-01", "2999-01-31", "sex", "ageGroup", "mes", event, null, "TODOS"));
		assertCode("INVALID_DEMOGRAPHIC_DIMENSIONS",
		    () -> f.service.demographics("2026-01-01", "2026-01-31", "planeta", "sex", "mes", event, null, "TODOS"));
		assertCode("INVALID_DEMOGRAPHIC_DIMENSIONS",
		    () -> f.service.demographics("2026-01-01", "2026-01-31", "sex", "sex", "mes", event, null, "TODOS"));
		assertCode("INVALID_DEMOGRAPHIC_DIMENSIONS",
		    () -> f.service.demographics("2026-01-01", "2026-01-31", "sex", "ageGroup", "decada", event, null, "TODOS"));
		assertCode("INVALID_DEMOGRAPHIC_EVENTS",
		    () -> f.service.demographics("2026-01-01", "2026-01-31", "disease", "sex", "mes", event, event, "TODOS"));
		assertCode("INVALID_DEMOGRAPHIC_EVENTS",
		    () -> f.service.demographics("2026-01-01", "2026-01-31", "sex", "disease", "mes", null, null, "TODOS"));
		assertCode("INVALID_DEMOGRAPHIC_EVENTS",
		    () -> f.service.demographics("2026-01-01", "2026-01-31", "sex", "ageGroup", "mes", event, event, "TODOS"));
		NotifiableEvent withoutConcept = new NotifiableEvent();
		withoutConcept.setUuid("bare");
		when(f.dao.events()).thenReturn(Arrays.asList(f.event, withoutConcept));
		assertCode("INVALID_DEMOGRAPHIC_EVENTS",
		    () -> f.service.demographics("2026-01-01", "2026-01-31", "sex", "disease", "mes", null, "bare", "TODOS"));
	}
	
	@Test
	public void registrationAcceptsAnOmittedOriginAndAMatchingOrder() {
		SyntheticFixture f = new SyntheticFixture();
		Obs obs = f.lab(true);
		when(f.clinical.order(SyntheticFixture.uuid(99))).thenReturn(obs.getOrder());
		f.surveillanceRequest.testOrderUuid = SyntheticFixture.uuid(99);
		f.surveillanceRequest.laboratoryObservationUuid = obs.getUuid();
		f.surveillanceRequest.origin = null;
		f.surveillanceRequest.diagnosisType = "CONFIRMADO";
		assertNotNull(f.service.createDraft(f.surveillanceRequest));
		rejects("INVALID_REFERENCE", g -> when(g.clinical.provider(g.provider.getUuid())).thenReturn(null));
		rejects("INVALID_REFERENCE", g -> when(g.clinical.location(g.location.getUuid())).thenReturn(null));
		rejects("INVALID_REFERENCE", g -> when(g.clinical.diagnosis(g.encounterDiagnosis.getUuid())).thenReturn(null));
		rejects("INVALID_REFERENCE", g -> g.provider.setPerson(new Person(93)));
	}
}
