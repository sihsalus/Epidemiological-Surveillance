package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.EpidemiologicalCalendar;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.SurveillanceException;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.SurveillanceCaseResponse;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.SurveillanceCase;

public class CaseSnapshotTest {

	@Test
	public void legacyResidenceDisplaysDepartmentFirstWithoutChangingSnapshot() {
		SyntheticFixture f = new SyntheticFixture();
		f.service.createDraft(f.surveillanceRequest);
		ArgumentCaptor<SurveillanceCase> captured = ArgumentCaptor.forClass(SurveillanceCase.class);
		verify(f.dao).save(captured.capture());
		SurveillanceCase saved = captured.getValue();
		when(f.dao.byUuid(SurveillanceCase.class, saved.getUuid())).thenReturn(saved);
		when(f.dao.addressChildren("ADDRESS_1", null)).thenReturn(
		    java.util.Collections.singletonList(new Object[] { "department-id", "Department" }));
		String legacy = "Province → District → Village → Department";
		saved.setPatientResidence(legacy);
		assertEquals("Department → Province → District → Village", f.service.getDraft(saved.getUuid()).patientResidence);
		assertEquals(legacy, saved.getPatientResidence());
		saved.setPatientResidence("Department → Province → District → Village");
		assertEquals(saved.getPatientResidence(), f.service.getDraft(saved.getUuid()).patientResidence);
		saved.setPatientResidence("Province → District → Village");
		assertEquals(saved.getPatientResidence(), f.service.getDraft(saved.getUuid()).patientResidence);
	}

	@Test
	public void pregnancyUsesCompletedYearsAtInvestigationIncludingBothLimits() {
		String[] births = { "2016-01-21", "2016-01-20", "1970-01-21", "1970-01-20" };
		int[] ages = { 9, 10, 55, 56 };
		for (int i = 0; i < births.length; i++) {
			SyntheticFixture f = new SyntheticFixture();
			f.patient.setBirthdate(java.sql.Date.valueOf(births[i]));
			f.surveillanceRequest.pregnant = true;
			SurveillanceCaseResponse result = f.service.createDraft(f.surveillanceRequest);
			assertEquals(Integer.valueOf(ages[i]), result.patientAge);
			assertEquals(ages[i] >= 10 && ages[i] <= 55, result.pregnant);
		}
	}

	@Test
	public void pregnancyIsNoForMaleOrMissingBirthDate() {
		SyntheticFixture f = new SyntheticFixture();
		f.surveillanceRequest.pregnant = true;
		f.patient.setGender("M");
		assertFalse(f.service.createDraft(f.surveillanceRequest).pregnant);
		f.patient.setGender("F");
		f.patient.setBirthdate(null);
		SurveillanceCaseResponse result = f.service.createDraft(f.surveillanceRequest);
		assertFalse(result.pregnant);
		assertNull(result.patientAge);
	}

	@Test
	public void patientCopiesSurvivePatientChangesAndCaseUpdates() {
		SyntheticFixture f = new SyntheticFixture();
		PersonName name = new PersonName("Synthetic", "Second", "Paternal");
		name.setFamilyName2("Maternal");
		name.setPreferred(true);
		f.patient.addName(name);
		PersonAddress address = new PersonAddress();
		address.setPreferred(true);
		address.setStateProvince("Province");
		address.setCountyDistrict("District");
		address.setCityVillage("Village");
		f.patient.addAddress(address);
		f.surveillanceRequest.deathDate = null;
		f.surveillanceRequest.pregnant = true;
		f.service.createDraft(f.surveillanceRequest);
		ArgumentCaptor<SurveillanceCase> captured = ArgumentCaptor.forClass(SurveillanceCase.class);
		verify(f.dao).save(captured.capture());
		SurveillanceCase saved = captured.getValue();
		when(f.dao.byUuid(SurveillanceCase.class, saved.getUuid())).thenReturn(saved);
		when(f.dao.caseByDiagnosis(f.encounterDiagnosis)).thenReturn(saved);
		name.setGivenName("Changed");
		name.setFamilyName("Changed");
		name.setFamilyName2("Changed");
		address.setCityVillage("Changed");
		f.patient.setGender("M");
		f.patient.setBirthdate(java.sql.Date.valueOf("2020-01-01"));
		SurveillanceCaseResponse result = f.service.updateDraft(saved.getUuid(), f.surveillanceRequest);
		assertEquals("Synthetic Second", result.patientGivenNames);
		assertEquals("Paternal", result.patientPaternalSurname);
		assertEquals("Maternal", result.patientMaternalSurname);
		assertEquals("F", result.patientSex);
		assertEquals(Integer.valueOf(36), result.patientAge);
		assertEquals("Province → District → Village", result.patientResidence);
		assertTrue(result.pregnant);
	}

	@Test
	public void investigationComesFromEncounterRegardlessOfRequest() {
		SyntheticFixture f = new SyntheticFixture();
		f.surveillanceRequest.investigationDate = "2099-01-01";
		assertEquals("2026-01-20", f.service.createDraft(f.surveillanceRequest).investigationDate);
	}

	@Test
	public void datesRejectFutureAndNotificationBeforeInvestigation() {
		for (String notification : new String[] { "2026-01-19", "2999-01-01" }) {
			SyntheticFixture f = new SyntheticFixture();
			f.surveillanceRequest.notificationDate = notification;
			try {
				f.service.createDraft(f.surveillanceRequest);
				fail("Invalid notification accepted");
			}
			catch (SurveillanceException ex) {
				assertEquals("INVALID_DATE_RANGE", ex.getCode());
			}
			verify(f.dao, never()).save(any(SurveillanceCase.class));
		}
		SyntheticFixture f = new SyntheticFixture();
		f.source.setEncounterDatetime(new EpidemiologicalCalendar(f.m).date(LocalDate.of(2999, 1, 1)));
		f.surveillanceRequest.notificationDate = null;
		try {
			f.service.createDraft(f.surveillanceRequest);
			fail("Future investigation accepted");
		}
		catch (SurveillanceException ex) {
			assertEquals("INVALID_DATE_RANGE", ex.getCode());
		}
	}

	@Test
	public void investigationAndNotificationCanEqualToday() {
		SyntheticFixture f = new SyntheticFixture();
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(f.m);
		f.source.setEncounterDatetime(calendar.date(calendar.today()));
		f.surveillanceRequest.notificationDate = calendar.today().toString();
		assertEquals(calendar.today().toString(), f.service.createDraft(f.surveillanceRequest).investigationDate);
	}
}
