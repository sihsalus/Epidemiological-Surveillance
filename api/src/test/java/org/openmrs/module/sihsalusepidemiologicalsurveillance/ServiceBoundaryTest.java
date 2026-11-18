package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import org.junit.Test;
import org.openmrs.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

/** Rejections and secondary paths of the surveillance service, with synthetic clinical data. */
public class ServiceBoundaryTest {
	
	private static void assertCode(String code, Runnable action) {
		try {
			action.run();
			fail("Expected " + code);
		}
		catch (SurveillanceException ex) {
			assertEquals(code, ex.getCode());
		}
	}
	
	private static SurveillanceCase reportCase(SyntheticFixture f) {
		SurveillanceCase value = new SurveillanceCase();
		value.setPatient(f.patient);
		value.setEncounter(f.source);
		value.setDiagnosis(f.encounterDiagnosis);
		value.setOnsetDate(java.sql.Date.valueOf("2026-01-20"));
		value.setPatientAge(30);
		value.setPatientSex("F");
		value.setDiagnosisType("CONFIRMADO");
		value.setInfectionAddress(10);
		value.setOrigin("AUTOCTONO");
		return value;
	}
	
	private static NotifiableEvent otherEvent() {
		NotifiableEvent other = new NotifiableEvent();
		other.setId(2);
		other.setUuid(SyntheticFixture.uuid(601));
		other.setConcept(SyntheticFixture.concept(602));
		other.setPeriodicity("SEMANAL");
		return other;
	}
	
	@Test
	public void alertQueriesRejectUnknownFiltersAndMissingAlerts() {
		SyntheticFixture f = new SyntheticFixture();
		assertCode("INVALID_CLASSIFICATION", () -> f.service.alerts("UNKNOWN", null, null));
		assertCode("INVALID_REFERENCE", () -> f.service.attendAlert("missing"));
	}
	
	@Test
	public void indicatorsRejectInvalidParametersAndSkipCasesOutsideTheSelection() {
		SyntheticFixture f = new SyntheticFixture();
		assertCode("INVALID_INDICATOR", () -> f.service.indicators(f.event.getUuid(), "2026-01-01", "2026-01-31",
		    "CENTRO_POBLADO", null, "CONFIRMADO", "unknown"));
		assertCode("INVALID_DATE_RANGE", () -> f.service.indicators(f.event.getUuid(), "2026-01", "2026-01-31",
		    "CENTRO_POBLADO", null, "CONFIRMADO", "completeness"));
		assertCode("INVALID_DATE_RANGE", () -> f.service.indicators(f.event.getUuid(), "2026-02-01", "2026-01-31",
		    "CENTRO_POBLADO", null, "CONFIRMADO", "completeness"));
		assertCode("INVALID_ZONE_LEVEL", () -> f.service.indicators(f.event.getUuid(), "2026-01-01", "2026-01-31", "REGION",
		    null, "CONFIRMADO", "completeness"));
		when(f.dao.addressId("missing", "COUNTY_DISTRICT")).thenReturn(null);
		assertCode("INVALID_REPORT_ADDRESS", () -> f.service.indicators(f.event.getUuid(), "2026-01-01", "2026-01-31",
		    "DISTRITO", "missing", "CONFIRMADO", "completeness"));
		assertCode("INVALID_DEMOGRAPHIC_EVENTS", () -> f.service.indicators("missing", "2026-01-01", "2026-01-31",
		    "CENTRO_POBLADO", null, "CONFIRMADO", "completeness"));
		NotifiableEvent other = otherEvent();
		Diagnosis otherDiagnosis = new Diagnosis();
		otherDiagnosis.setVoided(false);
		otherDiagnosis.setDiagnosis(new CodedOrFreeText(other.getConcept(), null, null));
		SurveillanceCase otherDisease = reportCase(f);
		otherDisease.setDiagnosis(otherDiagnosis);
		SurveillanceCase withoutPlace = reportCase(f);
		withoutPlace.setInfectionAddress(null);
		when(f.dao.events()).thenReturn(Arrays.asList(f.event, other));
		when(f.dao.surveillanceCases()).thenReturn(Arrays.asList(otherDisease, withoutPlace));
		when(f.dao.addressId("center", "CITY_VILLAGE")).thenReturn(10);
		Map<String, Object> result = f.service.indicators(f.event.getUuid(), "2026-01-01", "2026-01-31", "CENTRO_POBLADO",
		    "center", "CONFIRMADO", "completeness");
		assertEquals(0, result.get("denominator"));
	}
	
	@Test
	public void riskThresholdsAreReadValidatedAndStored() {
		SyntheticFixture f = new SyntheticFixture();
		when(f.dao.riskThresholds()).thenReturn("5,10,20");
		assertArrayEquals(new int[] { 5, 10, 20 }, f.service.getRiskThresholds());
		assertArrayEquals(new int[] { 2, 4, 8 }, f.service.saveRiskThresholds(Arrays.asList(2, 4, 8)));
		verify(f.dao).saveRiskThresholds("2,4,8");
		assertCode("INVALID_RISK_LEVELS", () -> f.service.saveRiskThresholds(Arrays.asList(2, 4)));
	}
	
	@Test
	public void coordinateImportIgnoresCentersWithoutCode() {
		SyntheticFixture f = new SyntheticFixture();
		when(f.dao.populatedCenterCodes())
		        .thenReturn(Arrays.asList(new Object[] { 1, null }, new Object[] { 2, "100" }, new Object[] { 3, "100" }));
		Map<String, Integer> result = f.service
		        .importCoordinates("address_hierarchy_entry_id,latitude,longitude\n100,-3.7,-73.2\n200,-3.8,-73.3");
		assertEquals(Integer.valueOf(2), result.get("imported"));
		assertEquals(Integer.valueOf(0), result.get("matched"));
	}
	
	@Test
	public void geographyRejectsInvalidParametersAndReadsEthnicityPerPatient() {
		SyntheticFixture f = new SyntheticFixture();
		assertCode("INVALID_DEMOGRAPHIC_DIMENSIONS", () -> f.service.geography(f.event.getUuid(), "2026-01-01", "2026-01-31",
		    "CENTRO_POBLADO", null, "CONFIRMADO", "disease", "mes"));
		assertCode("INVALID_DATE_RANGE", () -> f.service.geography(f.event.getUuid(), "2026-1-1", "2026-01-31",
		    "CENTRO_POBLADO", null, "CONFIRMADO", "origin", "mes"));
		assertCode("INVALID_ZONE_LEVEL", () -> f.service.geography(f.event.getUuid(), "2026-01-01", "2026-01-31", "REGION",
		    null, "CONFIRMADO", "origin", "mes"));
		when(f.dao.addressId("missing", "COUNTY_DISTRICT")).thenReturn(null);
		assertCode("INVALID_REPORT_ADDRESS", () -> f.service.geography(f.event.getUuid(), "2026-01-01", "2026-01-31",
		    "DISTRITO", "missing", "CONFIRMADO", "origin", "mes"));
		assertCode("INVALID_EVENT", () -> f.service.geography("missing", "2026-01-01", "2026-01-31", "CENTRO_POBLADO", null,
		    "CONFIRMADO", "origin", "mes"));
		PersonAttributeType type = new PersonAttributeType(1);
		type.setUuid("ethnicity");
		type.setFormat("org.openmrs.Concept");
		f.m.ethnicityAttributeTypeUuid = "ethnicity";
		when(f.clinical.attributeType("ethnicity")).thenReturn(type);
		Concept ethnicity = spy(SyntheticFixture.concept(501));
		doReturn("Synthetic ethnicity").when(ethnicity).getDisplayString();
		when(f.clinical.concept(501)).thenReturn(ethnicity);
		f.patient.addAttribute(new PersonAttribute(type, "501"));
		NotifiableEvent other = otherEvent();
		Diagnosis otherDiagnosis = new Diagnosis();
		otherDiagnosis.setVoided(false);
		otherDiagnosis.setDiagnosis(new CodedOrFreeText(other.getConcept(), null, null));
		SurveillanceCase otherDisease = reportCase(f), undated = reportCase(f), first = reportCase(f),
		        second = reportCase(f);
		otherDisease.setDiagnosis(otherDiagnosis);
		undated.setOnsetDate(null);
		when(f.dao.events()).thenReturn(Arrays.asList(f.event, other));
		when(f.dao.surveillanceCases()).thenReturn(Arrays.asList(otherDisease, undated, first, second));
		when(f.dao.centerCoordinates()).thenReturn(Collections.<Object[]> emptyList());
		GeographicReport report = f.service.geography(f.event.getUuid(), "2026-01-01", "2026-01-31", "CENTRO_POBLADO", null,
		    "CONFIRMADO", "ethnicity", "mes");
		assertEquals(2, report.total);
		verify(f.clinical, times(1)).concept(501);
	}
	
	@Test
	public void demographicsRejectInvalidParametersAndSkipCasesOutsideTheSelection() {
		SyntheticFixture f = new SyntheticFixture();
		String event = f.event.getUuid();
		assertCode("INVALID_ZONE_LEVEL", () -> f.service.demographics("2026-01-01", "2026-01-31", "sex", "age", "mes", event,
		    null, "CONFIRMADO", "REGION", null));
		assertCode("INVALID_DATE_RANGE",
		    () -> f.service.demographics("2026-1-1", "2026-01-31", "sex", "age", "mes", event, null, "CONFIRMADO"));
		assertCode("INVALID_DATE_RANGE",
		    () -> f.service.demographics("2026-02-01", "2026-01-31", "sex", "age", "mes", event, null, "CONFIRMADO"));
		assertCode("INVALID_DIAGNOSIS_TYPE",
		    () -> f.service.demographics("2026-01-01", "2026-01-31", "sex", "age", "mes", event, null, "DESCARTADO"));
		assertCode("INVALID_DEMOGRAPHIC_EVENTS",
		    () -> f.service.demographics("2026-01-01", "2026-01-31", "sex", "age", "mes", null, null, "CONFIRMADO"));
		assertCode("INVALID_DEMOGRAPHIC_EVENTS",
		    () -> f.service.demographics("2026-01-01", "2026-01-31", "sex", "age", "mes", "missing", null, "CONFIRMADO"));
		NotifiableEvent other = otherEvent();
		Diagnosis otherDiagnosis = new Diagnosis();
		otherDiagnosis.setVoided(false);
		otherDiagnosis.setDiagnosis(new CodedOrFreeText(other.getConcept(), null, null));
		SurveillanceCase probable = reportCase(f), otherDisease = reportCase(f), later = reportCase(f),
		        included = reportCase(f);
		probable.setDiagnosisType("PROBABLE");
		otherDisease.setDiagnosis(otherDiagnosis);
		later.setOnsetDate(java.sql.Date.valueOf("2026-02-20"));
		when(f.dao.events()).thenReturn(Arrays.asList(f.event, other));
		when(f.dao.surveillanceCases()).thenReturn(Arrays.asList(probable, otherDisease, later, included));
		DemographicReport report = f.service.demographics("2026-01-01", "2026-01-31", "sex", "age", "mes", event, null,
		    "CONFIRMADO");
		assertEquals(1, report.total);
	}
	
	@Test
	public void ethnicityIsResolvedFromConceptUuidsTextValuesAndMissingAttributes() {
		SyntheticFixture f = new SyntheticFixture();
		PersonAttributeType type = new PersonAttributeType(1);
		type.setUuid("ethnicity");
		type.setFormat("org.openmrs.Concept");
		f.m.ethnicityAttributeTypeUuid = "ethnicity";
		when(f.clinical.attributeType("ethnicity")).thenReturn(type);
		Concept ethnicity = spy(SyntheticFixture.concept(501));
		doReturn("Synthetic ethnicity").when(ethnicity).getDisplayString();
		doReturn(new ConceptName("Synthetic ethnicity", Locale.ENGLISH)).when(ethnicity).getName();
		when(f.clinical.concept(ethnicity.getUuid())).thenReturn(ethnicity);
		f.patient.addAttribute(new PersonAttribute(type, ethnicity.getUuid()));
		SurveillanceCase value = reportCase(f);
		value.setNotificationDate(Date.from(Instant.parse("2026-01-05T12:00:00Z")));
		value.setLocation(f.location);
		when(f.dao.weeklyCases(any(Date.class), any(Date.class))).thenReturn(Collections.singletonList(value));
		assertEquals(1, ((List<?>) f.service.weeklyRecords(2026, 1).get("rows")).size());
		when(f.dao.surveillanceCases()).thenReturn(Collections.singletonList(value));
		DemographicReport byUuid = f.service.demographics("2026-01-01", "2026-01-31", "ethnicity", "sex", "mes",
		    f.event.getUuid(), null, "CONFIRMADO");
		assertEquals("Synthetic ethnicity", byUuid.rows.get(0).key);
		Patient anonymous = new Patient(77);
		anonymous.setUuid("anonymous");
		SurveillanceCase withoutAttribute = reportCase(f);
		withoutAttribute.setPatient(anonymous);
		SurveillanceCase withoutPatient = reportCase(f);
		withoutPatient.setPatient(null);
		when(f.dao.surveillanceCases()).thenReturn(Arrays.asList(withoutAttribute, withoutPatient));
		assertEquals(2, f.service.demographics("2026-01-01", "2026-01-31", "ethnicity", "sex", "mes", f.event.getUuid(),
		    null, "CONFIRMADO").total);
		f.patient.getAttribute(type).setValue("501");
		when(f.clinical.concept(501)).thenReturn(null);
		when(f.dao.surveillanceCases()).thenReturn(Collections.singletonList(value));
		assertEquals("UNKNOWN", f.service.demographics("2026-01-01", "2026-01-31", "ethnicity", "sex", "mes",
		    f.event.getUuid(), null, "CONFIRMADO").rows.get(0).key);
	}
	
	@Test
	public void healthcheckEventsAndAddressCatalogAreServedOrRejected() {
		SyntheticFixture f = new SyntheticFixture();
		when(f.dao.verifiedTableCount()).thenReturn(12);
		assertEquals("UP", f.service.healthcheck().get("status"));
		Concept named = spy(f.event.getConcept());
		doReturn(new ConceptName("Synthetic event", Locale.ENGLISH)).when(named).getName();
		f.event.setConcept(named);
		assertEquals("Synthetic event", f.service.getEvent(f.event.getUuid()).get("conceptDisplay"));
		assertCode("EVENT_NOT_FOUND", () -> f.service.getEvent(SyntheticFixture.uuid(999)));
		when(f.dao.addressChildren("CITY_VILLAGE", "parent"))
		        .thenReturn(Collections.singletonList(new Object[] { "child", "Synthetic center" }));
		List<Map<String, Object>> children = f.service.addressChildren("populated-centers", "parent");
		assertEquals("Synthetic center", children.get(0).get("display"));
		for (String level : Arrays.asList("departments", "provinces", "districts"))
			assertTrue(f.service.addressChildren(level, null).isEmpty());
		assertCode("INVALID_ADDRESS_LEVEL", () -> f.service.addressChildren("regions", null));
	}
	
	@Test
	public void caseQueriesRejectInvalidRangesAndMissingCases() {
		SyntheticFixture f = new SyntheticFixture();
		assertCode("INVALID_DATE_RANGE",
		    () -> f.service.listCases(null, null, "2026-02-01", "2026-01-01", null, null, null));
		assertCode("INVALID_DATE", () -> f.service.listCases(null, null, "2026-2-1", null, null, null, null));
		SurveillanceCase alive = reportCase(f);
		when(f.dao.surveillanceCases()).thenReturn(Collections.singletonList(alive));
		assertTrue(f.service.listCases(null, null, null, null, null, null, true).isEmpty());
		assertCode("CASE_NOT_FOUND", () -> f.service.updateDraft("missing", f.surveillanceRequest));
		assertCode("CASE_NOT_FOUND", () -> f.service.getDraft("missing"));
	}
	
	@Test
	public void draftRejectsEachInvalidReference() {
		assertCode("REQUIRED_FIELDS", () -> new SyntheticFixture().service.createDraft(null));
		SyntheticFixture patient = new SyntheticFixture();
		patient.surveillanceRequest.patientUuid = "missing";
		assertCode("INVALID_REFERENCE", () -> patient.service.createDraft(patient.surveillanceRequest));
		SyntheticFixture encounter = new SyntheticFixture();
		encounter.source.setPatient(new Patient(99));
		assertCode("INVALID_REFERENCE", () -> encounter.service.createDraft(encounter.surveillanceRequest));
		SyntheticFixture provider = new SyntheticFixture();
		provider.provider.setRetired(true);
		assertCode("INVALID_REFERENCE", () -> provider.service.createDraft(provider.surveillanceRequest));
		SyntheticFixture location = new SyntheticFixture();
		location.location.setRetired(true);
		assertCode("INVALID_REFERENCE", () -> location.service.createDraft(location.surveillanceRequest));
		SyntheticFixture diagnosis = new SyntheticFixture();
		diagnosis.encounterDiagnosis.setVoided(true);
		assertCode("INVALID_REFERENCE", () -> diagnosis.service.createDraft(diagnosis.surveillanceRequest));
		SyntheticFixture freeText = new SyntheticFixture();
		freeText.encounterDiagnosis.setDiagnosis(new CodedOrFreeText(null, null, "synthetic free text"));
		assertCode("INVALID_REFERENCE", () -> freeText.service.createDraft(freeText.surveillanceRequest));
	}
	
	@Test
	public void editingRejectsAChangeOfPatientEncounterOrDiagnosis() {
		SyntheticFixture f = new SyntheticFixture();
		SurveillanceCase existing = new SurveillanceCase();
		existing.setUuid("existing");
		existing.setPatient(new Patient(55));
		existing.setProvider(f.provider);
		when(f.dao.byUuid(SurveillanceCase.class, "existing")).thenReturn(existing);
		assertCode("INVALID_REFERENCE", () -> f.service.updateDraft("existing", f.surveillanceRequest));
	}
	
	@Test
	public void draftRejectsInvalidClassificationsAddressesLaboratoryAndDates() {
		String[][] invalid = { { "diagnosisType", "OTRO" }, { "origin", "OTRO" }, { "vaccinationStatus", "OTRO" },
		        { "surveillanceType", "OTRA" } };
		for (String[] field : invalid) {
			SyntheticFixture f = new SyntheticFixture();
			if ("diagnosisType".equals(field[0]))
				f.surveillanceRequest.diagnosisType = field[1];
			if ("origin".equals(field[0]))
				f.surveillanceRequest.origin = field[1];
			if ("vaccinationStatus".equals(field[0]))
				f.surveillanceRequest.vaccinationStatus = field[1];
			if ("surveillanceType".equals(field[0]))
				f.surveillanceRequest.surveillanceType = field[1];
			assertCode("INVALID_CLASSIFICATION", () -> f.service.createDraft(f.surveillanceRequest));
		}
		SyntheticFixture address = new SyntheticFixture();
		address.surveillanceRequest.infectionAddressUuid = "missing";
		when(address.dao.populatedCenterId("missing")).thenReturn(null);
		assertCode("INVALID_INFECTION_ADDRESS", () -> address.service.createDraft(address.surveillanceRequest));
		SyntheticFixture order = new SyntheticFixture();
		order.surveillanceRequest.testOrderUuid = "missing";
		assertCode("INVALID_LAB_RESULT", () -> order.service.createDraft(order.surveillanceRequest));
		SyntheticFixture mismatch = new SyntheticFixture();
		Obs observation = mismatch.lab(true);
		TestOrder another = new TestOrder();
		another.setPatient(mismatch.patient);
		when(mismatch.clinical.order("another")).thenReturn(another);
		mismatch.surveillanceRequest.testOrderUuid = "another";
		mismatch.surveillanceRequest.laboratoryObservationUuid = observation.getUuid();
		assertCode("INVALID_LAB_RESULT", () -> mismatch.service.createDraft(mismatch.surveillanceRequest));
		SyntheticFixture foreign = new SyntheticFixture();
		TestOrder foreignOrder = new TestOrder();
		foreignOrder.setPatient(new Patient(99));
		when(foreign.clinical.order("foreign")).thenReturn(foreignOrder);
		foreign.surveillanceRequest.testOrderUuid = "foreign";
		assertCode("INVALID_LAB_RESULT", () -> foreign.service.createDraft(foreign.surveillanceRequest));
		SyntheticFixture undated = new SyntheticFixture();
		undated.source.setEncounterDatetime(null);
		assertCode("INVALID_DATE", () -> undated.service.createDraft(undated.surveillanceRequest));
		SyntheticFixture unborn = new SyntheticFixture();
		unborn.patient.setBirthdate(new EpidemiologicalCalendar(unborn.m).date(LocalDate.of(2026, 6, 1)));
		assertCode("INVALID_DATE_RANGE", () -> unborn.service.createDraft(unborn.surveillanceRequest));
		SyntheticFixture lateOnset = new SyntheticFixture();
		lateOnset.surveillanceRequest.onsetDate = "2026-01-21";
		assertCode("INVALID_DATE_RANGE", () -> lateOnset.service.createDraft(lateOnset.surveillanceRequest));
	}
	
	@Test
	public void onsetAfterNotificationIsRejectedAcrossTimeZones() {
		TimeZone original = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
			SyntheticFixture f = new SyntheticFixture();
			f.source.setEncounterDatetime(Date.from(Instant.parse("2026-01-20T05:30:00Z")));
			f.surveillanceRequest.onsetDate = "2026-01-20";
			f.surveillanceRequest.notificationDate = "2026-01-20T00:45:00-05:00";
			try {
				f.service.createDraft(f.surveillanceRequest);
				fail();
			}
			catch (SurveillanceException ex) {
				assertEquals(Collections.singletonMap("notificationDate", "beforeOnset"), ex.getFieldErrors());
			}
		}
		finally {
			TimeZone.setDefault(original);
		}
	}
	
	@Test
	public void englishOriginsAreNormalizedAndIdentifiersAndUnrelatedCasesAreHandled() {
		for (String[] origin : new String[][] { { "AUTOCHTHONOUS", "AUTOCTONO" },
		        { "IMPORTED_NATIONAL", "IMPORTADO_NACIONAL" }, { "IMPORTED_INTERNATIONAL", "IMPORTADO_INTERNACIONAL" } }) {
			SyntheticFixture f = new SyntheticFixture();
			f.surveillanceRequest.origin = origin[0];
			PatientIdentifier dni = new PatientIdentifier("00123456", new PatientIdentifierType(), f.location);
			f.patient.addIdentifier(dni);
			SurveillanceCase undiagnosed = new SurveillanceCase();
			NotifiableEvent other = otherEvent();
			when(f.dao.events()).thenReturn(Arrays.asList(f.event, other));
			when(f.dao.surveillanceCases()).thenReturn(Collections.singletonList(undiagnosed));
			SurveillanceCaseResponse response = f.service.createDraft(f.surveillanceRequest);
			assertEquals(origin[1], response.origin);
			assertEquals("00123456", response.patientIdentifier);
		}
	}
	
	@Test
	public void temporalReportRejectsMissingEventsInvalidRangesAndUncoveredPeriods() {
		SyntheticFixture f = new SyntheticFixture();
		String event = f.event.getUuid();
		assertCode("EVENT_NOT_FOUND", () -> f.service.report("missing", "2026-01-01", "2026-01-31", "mes"));
		assertCode("INVALID_DATE_RANGE", () -> f.service.report(event, "2026-1-1", "2026-01-31", "mes"));
		assertCode("INVALID_DATE_RANGE", () -> f.service.report(event, "2026-02-01", "2026-01-31", "mes"));
		f.m.surveillanceStartDate = "2026-01-15";
		assertCode("OUTSIDE_COVERAGE", () -> f.service.report(event, "2026-01-01", "2026-01-31", "mes"));
		f.m.surveillanceStartDate = "2020-01-01";
		when(f.dao.addressId("department", "ADDRESS_1")).thenReturn(5);
		PeriodCaseCount withoutCenter = new PeriodCaseCount();
		withoutCenter.setEvent(f.event);
		when(f.dao.counts(f.event, "dia", 2026, 2026, "CENTRO_POBLADO", null))
		        .thenReturn(Collections.singletonList(withoutCenter));
		assertEquals(0,
		    f.service.report(event, "2026-01-01", "2026-01-31", "mes", "DEPARTAMENTO", "department", "CONFIRMADO").total);
	}
	
	@Test
	public void eventWritesRejectDuplicatesInvalidConceptsLongRegulationsAndNonCanonicalUuids() {
		SyntheticFixture f = new SyntheticFixture();
		Map<String, Object> body = new HashMap<>();
		body.put("uuid", f.event.getUuid());
		assertCode("EVENT_ALREADY_EXISTS", () -> f.service.saveEvent(body));
		Concept concept = SyntheticFixture.concept(700);
		when(f.clinical.concept(concept.getUuid())).thenReturn(concept);
		Map<String, Object> valid = new HashMap<>();
		valid.put("conceptUuid", SyntheticFixture.uuid(701));
		valid.put("periodicity", "semanal");
		valid.put("referenceRegulation", "NTS");
		valid.put("active", true);
		assertCode("INVALID_EVENT", () -> f.service.saveEvent(valid));
		valid.put("conceptUuid", concept.getUuid());
		char[] longText = new char[101];
		Arrays.fill(longText, 'x');
		valid.put("referenceRegulation", new String(longText));
		assertCode("INVALID_EVENT", () -> f.service.saveEvent(valid));
		valid.put("referenceRegulation", "NTS");
		valid.put("conceptUuid", "1-1-1-1-1");
		assertCode("INVALID_REFERENCE", () -> f.service.saveEvent(valid));
	}
}
