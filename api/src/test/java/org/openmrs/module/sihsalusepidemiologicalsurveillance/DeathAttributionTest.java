package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Date;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.DeathAttributionSaveHandler;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.SurveillanceException;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.SurveillanceCase;

public class DeathAttributionTest {
	
	@Test
	public void persistsEveryChoiceAndDefaultsToUndetermined() {
		for (String choice : Arrays.asList("SI", "NO", "POR_DETERMINAR", null)) {
			SyntheticFixture f = new SyntheticFixture();
			f.surveillanceRequest.diagnosisType = "CONFIRMADO";
			f.patient.setDead(true);
			f.patient.setDeathDate(java.sql.Date.valueOf("2026-01-22"));
			f.surveillanceRequest.deathAttribution = choice;
			assertEquals(choice == null ? "POR_DETERMINAR" : choice,
			    f.service.createDraft(f.surveillanceRequest).deathAttribution);
			ArgumentCaptor<SurveillanceCase> capture = ArgumentCaptor.forClass(SurveillanceCase.class);
			verify(f.dao).save(capture.capture());
			assertEquals(choice == null ? "POR_DETERMINAR" : choice, capture.getValue().getDeathAttribution());
			assertNull(capture.getValue().getDeathDate());
		}
	}
	
	@Test
	public void livingPatientIgnoresStaleAttributionAndLegacyDate() {
		SyntheticFixture f = new SyntheticFixture();
		f.surveillanceRequest.deathAttribution = "SI";
		assertNull(f.service.createDraft(f.surveillanceRequest).deathAttribution);
	}
	
	@Test
	public void invalidChoiceIsRejectedForDeceasedPatient() {
		SyntheticFixture f = new SyntheticFixture();
		f.surveillanceRequest.diagnosisType = "CONFIRMADO";
		f.patient.setDead(true);
		f.patient.setDeathDate(new Date());
		f.surveillanceRequest.deathAttribution = "INVALID";
		try {
			f.service.createDraft(f.surveillanceRequest);
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals("INVALID_CLASSIFICATION", ex.getCode());
		}
	}
	
	@Test
	public void deceasedCaseRemainsEditableAndLivingUpdateClearsAttribution() {
		SyntheticFixture f = new SyntheticFixture();
		f.surveillanceRequest.diagnosisType = "CONFIRMADO";
		f.patient.setDead(true);
		f.patient.setDeathDate(java.sql.Date.valueOf("2026-01-22"));
		f.surveillanceRequest.deathAttribution = "SI";
		f.service.createDraft(f.surveillanceRequest);
		ArgumentCaptor<SurveillanceCase> capture = ArgumentCaptor.forClass(SurveillanceCase.class);
		verify(f.dao).save(capture.capture());
		SurveillanceCase value = capture.getValue();
		when(f.dao.byUuid(SurveillanceCase.class, value.getUuid())).thenReturn(value);
		f.surveillanceRequest.deathAttribution = "NO";
		assertEquals("NO", f.service.updateDraft(value.getUuid(), f.surveillanceRequest).deathAttribution);
		f.patient.setDeathDate(null);
		f.patient.setDead(false);
		assertNull(f.service.updateDraft(value.getUuid(), f.surveillanceRequest).deathAttribution);
		assertNull(value.getDeathAttribution());
	}
	
	@Test
	public void cleanupIncludesAllPatientCasesAndAuditsChanges() {
		SyntheticFixture f = new SyntheticFixture();
		SurveillanceCase first = new SurveillanceCase();
		SurveillanceCase second = new SurveillanceCase();
		first.setDeathAttribution("SI");
		second.setDeathAttribution("NO");
		when(f.dao.casesForPerson(f.patient.getId())).thenReturn(Arrays.asList(first, second));
		Date now = new Date();
		new DeathAttributionSaveHandler().clear(f.patient, null, now, f.dao);
		assertNull(first.getDeathAttribution());
		assertNull(second.getDeathAttribution());
		assertEquals(now, first.getDateChanged());
		verify(f.dao).save(first);
		verify(f.dao).save(second);
	}
}
