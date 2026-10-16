package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openmrs.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

public class SurveillanceServiceTest {
	
	@Test
	public void invalidCaseDateIdentifiesOnlyTheFieldAndViolatedCondition() {
		SyntheticFixture f = new SyntheticFixture();
		f.surveillanceRequest.notificationDate = "2026-01-19";
		try {
			f.service.createDraft(f.surveillanceRequest);
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals(Collections.singletonMap("notificationDate", "notificationBeforeInvestigation"),
			    ex.getFieldErrors());
			assertEquals(Collections.singletonList("notificationDate"), ex.getFields());
		}
		f.surveillanceRequest.notificationDate = "2026-02-30";
		try {
			f.service.createDraft(f.surveillanceRequest);
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals(Collections.singletonMap("notificationDate", "dateFormat"), ex.getFieldErrors());
		}
		verify(f.dao, never()).save(any(SurveillanceCase.class));
	}
	
	@Test
	public void draftPreservesSelectedLaboratoryObservationAndDerivedOrder() {
		SyntheticFixture f = new SyntheticFixture();
		Obs observation = f.lab(true);
		f.surveillanceRequest.laboratoryObservationUuid = observation.getUuid();
		SurveillanceCaseResponse response = f.service.createDraft(f.surveillanceRequest);
		ArgumentCaptor<SurveillanceCase> capture = ArgumentCaptor.forClass(SurveillanceCase.class);
		verify(f.dao).save(capture.capture());
		assertSame(observation, capture.getValue().getLaboratoryObservation());
		assertSame(observation.getOrder(), capture.getValue().getTestOrder());
		assertEquals(observation.getUuid(), response.laboratoryObservationUuid);
	}
	
	@Test
	public void draftRejectsLaboratoryResultFromAnotherEncounter() {
		SyntheticFixture f = new SyntheticFixture();
		Obs observation = f.lab(true);
		observation.setEncounter(new Encounter(999));
		f.surveillanceRequest.laboratoryObservationUuid = observation.getUuid();
		try {
			f.service.createDraft(f.surveillanceRequest);
			fail("Expected invalid laboratory reference");
		}
		catch (SurveillanceException ex) {
			assertEquals("INVALID_LAB_RESULT", ex.getCode());
		}
	}
	
	@Test
	public void reportReadsOnlyAggregatesWithDefaultFilters() {
		SyntheticFixture f = new SyntheticFixture();
		SurveillanceReport report = f.service.report(f.event.getUuid(), "2026-01-01", "2026-01-03", "SEMANA");
		assertEquals("CONFIRMADO", report.diagnosisType);
		assertEquals("CENTRO_POBLADO", report.zoneLevel);
		verify(f.dao).counts(f.event, "dia", 2026, 2026, "CENTRO_POBLADO", null);
		// Historical window is anchored to the current calendar year even for boundary weeks.
		verify(f.dao).counts(f.event, "semana", 2021, 2025, "CENTRO_POBLADO", null);
		verify(f.dao, never()).encounters(any(), any(), anyString());
		verify(f.dao, never()).surveillanceCases();
		verifyNoInteractions(f.clinical);
	}
	
	@Test
	public void reportValidatesAddressAgainstSelectedLevel() {
		SyntheticFixture f = new SyntheticFixture();
		when(f.dao.addressId("synthetic-district", "COUNTY_DISTRICT")).thenReturn(10);
		when(f.dao.addressId("synthetic-district", "CITY_VILLAGE")).thenReturn(null);
		SurveillanceReport report = f.service.report(f.event.getUuid(), "2026-01-01", "2026-01-03", "dia", "DISTRITO",
		    "synthetic-district", "TODOS");
		assertEquals("synthetic-district", report.address);
		assertEquals("TODOS", report.population);
		verify(f.dao).counts(f.event, "dia", 2026, 2026, "DISTRITO", 10);
		try {
			f.service.report(f.event.getUuid(), "2026-01-01", "2026-01-03", "dia", "CENTRO_POBLADO", "synthetic-district",
			    "TODOS");
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals(422, ex.getStatus());
			assertEquals("INVALID_REPORT_ADDRESS", ex.getCode());
		}
	}
	
	@Test
	public void reportRejectsUnsupportedGeographyAndDiscardedFilter() {
		SyntheticFixture f = new SyntheticFixture();
		for (String[] selection : Arrays.asList(new String[] { "INVALID", "TODOS", "INVALID_ZONE_LEVEL" },
		    new String[] { "DISTRITO", "DESCARTADO", "INVALID_DIAGNOSIS_TYPE" })) {
			try {
				f.service.report(f.event.getUuid(), "2026-01-01", "2026-01-03", "dia", selection[0], null, selection[1]);
				fail();
			}
			catch (SurveillanceException ex) {
				assertEquals(422, ex.getStatus());
				assertEquals(selection[2], ex.getCode());
			}
		}
	}
	
	@Test
	public void draftPersistsEveryCaseAttributeAndLeavesIndividualRecordUnset() {
		SyntheticFixture f = new SyntheticFixture();
		f.surveillanceRequest.infectionAddressUuid = SyntheticFixture.uuid(92);
		when(f.dao.populatedCenterId(f.surveillanceRequest.infectionAddressUuid)).thenReturn(92);
		
		f.service.createDraft(f.surveillanceRequest);
		
		ArgumentCaptor<SurveillanceCase> capture = ArgumentCaptor.forClass(SurveillanceCase.class);
		verify(f.dao).save(capture.capture());
		SurveillanceCase saved = capture.getValue();
		assertSame(f.patient, saved.getPatient());
		assertSame(f.source, saved.getEncounter());
		assertSame(f.provider, saved.getProvider());
		assertSame(f.location, saved.getLocation());
		assertSame(f.encounterDiagnosis, saved.getDiagnosis());
		assertEquals(Integer.valueOf(92), saved.getInfectionAddress());
		assertEquals("AUTOCTONO", saved.getOrigin());
		assertEquals("PROBABLE", saved.getDiagnosisType());
		assertEquals("IGN", saved.getVaccinationStatus());
		assertEquals("PASIVA", saved.getSurveillanceType());
		assertNull(saved.getIndividualRecord());
		verify(f.dao).replaceCounts(eq(f.event), anyList());
	}
	
	@Test
	public void discardedDraftDoesNotTriggerCountRecalculation() {
		SyntheticFixture f = new SyntheticFixture();
		f.surveillanceRequest.diagnosisType = "DESCARTADO";
		f.service.createDraft(f.surveillanceRequest);
		verify(f.dao, never()).replaceCounts(any(NotifiableEvent.class), anyList());
	}
	
	@Test
	public void editingACaseToDiscardedRecalculatesItsPreviousContribution() {
		SyntheticFixture f = new SyntheticFixture();
		SurveillanceCase existing = new SurveillanceCase();
		existing.setUuid(SyntheticFixture.uuid(99));
		existing.setDiagnosisType("CONFIRMADO");
		when(f.dao.byUuid(SurveillanceCase.class, existing.getUuid())).thenReturn(existing);
		when(f.dao.caseByDiagnosis(f.encounterDiagnosis)).thenReturn(existing);
		f.surveillanceRequest.diagnosisType = "DESCARTADO";
		f.surveillanceRequest.deathDate = null;
		f.service.updateDraft(existing.getUuid(), f.surveillanceRequest);
		assertEquals("DESCARTADO", existing.getDiagnosisType());
		verify(f.dao).replaceCounts(eq(f.event), anyList());
	}
	
	@Test
	public void draftRejectsASecondCaseForTheSameDiagnosis() {
		SyntheticFixture f = new SyntheticFixture();
		SurveillanceCase existing = new SurveillanceCase();
		when(f.dao.caseByDiagnosis(f.encounterDiagnosis)).thenReturn(existing);
		
		try {
			f.service.createDraft(f.surveillanceRequest);
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals(409, ex.getStatus());
			assertEquals("CASE_ALREADY_EXISTS_FOR_DIAGNOSIS", ex.getCode());
		}
		verify(f.dao, never()).save(any(SurveillanceCase.class));
	}
	
	@Test
	public void closingDraftRequiresOnlyTheRegisterPrivilegeBeforeValidation() {
		SyntheticFixture f = new SyntheticFixture();
		SurveillanceCase draft = new SurveillanceCase();
		draft.setUuid(SyntheticFixture.uuid(99));
		draft.setPatient(f.patient);
		draft.setEncounter(f.source);
		draft.setProvider(f.provider);
		draft.setLocation(f.location);
		draft.setDiagnosis(f.encounterDiagnosis);
		when(f.dao.byUuid(SurveillanceCase.class, draft.getUuid())).thenReturn(draft);
		
		try {
			f.service.closeDraft(draft.getUuid());
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals("REQUIRED_FIELDS", ex.getCode());
		}
		
		verify(f.access).require(SurveillanceConstants.REGISTER);
		verify(f.access, never()).require(SurveillanceConstants.VIEW);
	}
	
	@Test
	public void registrationCompletesExistingEncounterDiagnosisAndAudit() {
		SyntheticFixture f = new SyntheticFixture();
		CaseResult result = f.service.registerCase(f.request);
		assertEquals(f.source.getUuid(), result.uuid);
		assertEquals("A90", result.icd10);
		assertEquals("SEMANAL", result.periodicity);
		ArgumentCaptor<Encounter> capture = ArgumentCaptor.forClass(Encounter.class);
		verify(f.clinical).saveEncounter(capture.capture());
		Encounter e = capture.getValue();
		assertSame(f.source, e);
		assertEquals(5, e.getAllObs(false).size());
		assertEquals(f.source.getUuid(), e.getUuid());
		verify(f.clinical).saveDiagnosis(any(Diagnosis.class));
		org.mockito.InOrder order = inOrder(f.dao, f.clinical);
		order.verify(f.dao).lockPatient(f.patient);
		order.verify(f.dao).lockEvent(f.event);
		order.verify(f.clinical).saveEncounter(any(Encounter.class));
	}
	
	@Test
	public void registrationAcceptsAnEncounterOfAnyType() {
		SyntheticFixture f = new SyntheticFixture();
		EncounterType otherType = new EncounterType();
		otherType.setUuid(SyntheticFixture.uuid(90));
		f.source.setEncounterType(otherType);
		assertEquals(f.source.getUuid(), f.service.registerCase(f.request).uuid);
		verify(f.clinical).saveEncounter(f.source);
	}
	
	@Test
	public void preservesExistingPregnancyObservationAndMatchingDiagnosis() {
		SyntheticFixture f = new SyntheticFixture();
		Obs pregnancy = new Obs(f.patient, f.clinical.concept(f.m.questions.get("pregnancy")),
		        f.source.getEncounterDatetime(), f.location);
		pregnancy.setValueCoded(f.clinical.trueConcept());
		f.source.addObs(pregnancy);
		Diagnosis existing = new Diagnosis();
		existing.setDiagnosis(new CodedOrFreeText(f.diagnosis, null, null));
		f.source.setDiagnoses(new HashSet<Diagnosis>(Arrays.asList(existing)));
		f.service.registerCase(f.request);
		assertSame(pregnancy, CaseObservations.find(f.source, f.m.questions.get("pregnancy")));
		assertSame(existing, f.source.getDiagnoses().iterator().next());
		verify(f.clinical, never()).saveDiagnosis(any(Diagnosis.class));
	}
	
	@Test
	public void duplicatePreventsAnyClinicalWrite() {
		SyntheticFixture f = new SyntheticFixture();
		when(f.dao.possibleDuplicates(any(), anyList(), any(), any(), anyString())).thenReturn(Arrays.asList(f.source));
		try {
			f.service.registerCase(f.request);
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals("POSSIBLE_DUPLICATE", ex.getCode());
		}
		verify(f.clinical, never()).saveEncounter(any());
	}
	
	@Test
	public void retryReturnsSameEncounterWithoutSecondWrite() {
		SyntheticFixture f = new SyntheticFixture();
		f.service.registerCase(f.request);
		ArgumentCaptor<Encounter> capture = ArgumentCaptor.forClass(Encounter.class);
		verify(f.clinical).saveEncounter(capture.capture());
		assertTrue(f.service.registerCase(f.request).replayed);
		verify(f.clinical, times(1)).saveEncounter(any());
	}
	
	@Test
	public void changedPayloadCannotOverwriteSuccessfulRequest() {
		SyntheticFixture f = new SyntheticFixture();
		f.service.registerCase(f.request);
		ArgumentCaptor<Encounter> capture = ArgumentCaptor.forClass(Encounter.class);
		verify(f.clinical).saveEncounter(capture.capture());
		f.request.severity = "SEVERE";
		try {
			f.service.registerCase(f.request);
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals("IDEMPOTENCY_CONFLICT", ex.getCode());
		}
		verify(f.clinical, times(1)).saveEncounter(any());
	}
	
	@Test
	public void deniedWriteDoesNotReadClinicalData() {
		SyntheticFixture f = new SyntheticFixture();
		doThrow(new SurveillanceException(403, "ACCESS_DENIED")).when(f.access).require(SurveillanceConstants.REGISTER);
		try {
			f.service.registerCase(f.request);
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals(403, ex.getStatus());
		}
		verify(f.clinical, never()).patient(anyString());
	}
	
	@Test
	public void deniedReportsDoNotReadAnyCases() {
		SyntheticFixture f = new SyntheticFixture();
		doThrow(new SurveillanceException(403, "ACCESS_DENIED")).when(f.access).require(SurveillanceConstants.REPORT);
		try {
			f.service.report(f.event.getUuid(), "2026-01-01", "2026-01-10", "semana");
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals(403, ex.getStatus());
		}
		verify(f.dao, never()).encounters(any(), any(), anyString());
	}
	
	@Test
	public void severeCaseOverridesConfiguredWeeklyPeriodicity() {
		SyntheticFixture f = new SyntheticFixture();
		f.request.severity = "SEVERE";
		CaseResult result = f.service.registerCase(f.request);
		assertEquals("inmediata", result.periodicity);
		assertEquals(0, result.deadlineDays);
		assertTrue(result.immediateAlerts.contains("SEVERE_CASE"));
		verify(f.clinical).alert(eq(f.actor), anyString());
	}
	
	@Test
	public void refreshUsesCoverageAndReplacesAggregates() {
		SyntheticFixture f = new SyntheticFixture();
		f.event.getConcept().addSetMember(f.diagnosis);
		f.encounterDiagnosis.setDiagnosis(new CodedOrFreeText(f.diagnosis, null, null));
		SurveillanceCase surveillanceCase = new SurveillanceCase();
		surveillanceCase.setDiagnosis(f.encounterDiagnosis);
		surveillanceCase.setDiagnosisType("CONFIRMADO");
		surveillanceCase.setInfectionAddress(92);
		surveillanceCase.setOnsetDate(new EpidemiologicalCalendar(f.m).date(java.time.LocalDate.of(2026, 1, 19)));
		when(f.dao.surveillanceCases()).thenReturn(Collections.singletonList(surveillanceCase));
		when(f.dao.districtIdForPopulatedCenter(92)).thenReturn(91);
		f.service.refreshCounts();
		f.service.refreshCounts();
		ArgumentCaptor<List> captured = ArgumentCaptor.forClass(List.class);
		verify(f.dao, times(2)).replaceCounts(eq(f.event), captured.capture());
		assertEquals(captured.getAllValues().get(0).size(), captured.getAllValues().get(1).size());
		assertFalse(captured.getAllValues().get(0).isEmpty());
	}
	
	@Test
	public void confirmedRegistrationTriggersCountRefreshForThatEvent() {
		SyntheticFixture f = new SyntheticFixture();
		f.m.surveillanceStartDate = "2026-01-01";
		f.lab(true);
		f.request.status = "CONFIRMED";
		f.service.registerCase(f.request);
		verify(f.dao).replaceCounts(eq(f.event), argThat(counts -> !counts.isEmpty()));
	}
	
	@Test
	public void suspectedRegistrationDoesNotTriggerCountRefresh() {
		SyntheticFixture f = new SyntheticFixture();
		f.m.surveillanceStartDate = "2026-01-01";
		f.service.registerCase(f.request); // status = SUSPECTED by default
		verify(f.dao, never()).replaceCounts(any(), anyList());
	}
	
	@Test
	public void dynamicEventWithoutStaticDiseaseUsesEventConceptAsDiagnosis() {
		SyntheticFixture f = new SyntheticFixture();
		// Remove disease from catalog so it's purely dynamic
		f.m.diseases.clear();
		CaseResult result = f.service.registerCase(f.request);
		assertEquals(f.event.getConcept().getUuid(), result.diagnosisConceptUuid);
	}
}
