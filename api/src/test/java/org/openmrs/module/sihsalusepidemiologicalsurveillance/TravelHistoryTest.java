package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

public class TravelHistoryTest {

	private SyntheticFixture fixture(String address, String start, String end) {
		SyntheticFixture f = new SyntheticFixture();
		TravelHistoryEntry entry = new TravelHistoryEntry();
		entry.travelAddress = address;
		entry.startDate = start;
		entry.endDate = end;
		f.surveillanceRequest.travelHistory.add(entry);
		when(f.dao.populatedCenterId("district")).thenReturn(null);
		when(f.dao.populatedCenterId("center")).thenReturn(42);
		when(f.dao.addressUuid(42)).thenReturn("center");
		when(f.dao.addressDisplay(42)).thenReturn("Synthetic center");
		return f;
	}

	private void rejects(SyntheticFixture f, String field, String reason) {
		try {
			f.service.createDraft(f.surveillanceRequest);
			fail("Expected invalid trip");
		}
		catch (SurveillanceException ex) {
			assertEquals(Collections.singletonMap("travelHistory[0]." + field, reason), ex.getFieldErrors());
		}
		verify(f.dao, never()).save(any(SurveillanceCase.class));
	}

	@Test
	public void optionalListMayBeMissingNullOrEmpty() {
		SyntheticFixture f = new SyntheticFixture();
		assertTrue(f.service.createDraft(f.surveillanceRequest).travelHistory.isEmpty());
		f.surveillanceRequest.travelHistory = null;
		assertTrue(f.service.createDraft(f.surveillanceRequest).travelHistory.isEmpty());
	}

	@Test
	public void requiresEachTripFieldAndValidCenter() {
		rejects(fixture(null, "2026-01-01", "2026-01-02"), "travelAddress", "required");
		rejects(fixture("center", null, "2026-01-02"), "startDate", "required");
		rejects(fixture("center", "2026-01-01", null), "endDate", "required");
		rejects(fixture("district", "2026-01-01", "2026-01-02"), "travelAddress", "selection");
		SyntheticFixture f = fixture("center", "2026-01-01", "2026-01-02");
		f.surveillanceRequest.travelHistory.set(0, null);
		rejects(f, "travelAddress", "required");
	}

	@Test
	public void rejectsInvalidReversedAndFutureDates() {
		rejects(fixture("center", "2026-02-30", "2026-03-01"), "startDate", "dateFormat");
		rejects(fixture("center", "2026-01-02", "2026-01-01"), "endDate", "endBeforeStart");
		String tomorrow = new EpidemiologicalCalendar(new ClinicalCatalog()).today().plusDays(1).toString();
		rejects(fixture("center", tomorrow, tomorrow), "startDate", "future");
		rejects(fixture("center", "2026-01-01", tomorrow), "endDate", "future");
	}

	@Test
	public void windowUsesOnsetAndIncludesIntersectingBoundaryAndLongTrips() {
		for (String[] dates : new String[][] { { "2025-12-22", "2025-12-22" }, { "2025-11-01", "2025-12-22" },
		        { "2026-01-19", "2026-01-19" }, { "2025-11-01", "2026-01-20" } }) {
			SyntheticFixture f = fixture("center", dates[0], dates[1]);
			assertEquals(dates[0], f.service.createDraft(f.surveillanceRequest).travelHistory.get(0).startDate);
		}
		rejects(fixture("center", "2025-12-01", "2025-12-21"), "startDate", "travelOutsideWindow");
		rejects(fixture("center", "2026-01-20", "2026-01-21"), "startDate", "travelOutsideWindow");
	}

	@Test
	public void withoutOnsetUsesEncounterDateIgnoringSuppliedInvestigationDate() {
		SyntheticFixture f = fixture("center", "2025-12-23", "2025-12-23");
		f.surveillanceRequest.onsetDate = null;
		f.surveillanceRequest.investigationDate = "2025-01-01";
		assertEquals(1, f.service.createDraft(f.surveillanceRequest).travelHistory.size());
		f.surveillanceRequest.travelHistory.get(0).endDate = "2025-12-22";
		f.surveillanceRequest.travelHistory.get(0).startDate = "2025-12-22";
		try {
			f.service.createDraft(f.surveillanceRequest);
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals("travelOutsideWindow", ex.getFieldErrors().get("travelHistory[0].startDate"));
		}
	}

	@Test
	public void replacementPreservesIdentityAuditsUpdatesAddsAndRemoves() {
		SyntheticFixture f = fixture("center", "2026-01-01", "2026-01-02");
		SurveillanceCaseResponse created = f.service.createDraft(f.surveillanceRequest);
		ArgumentCaptor<SurveillanceCase> capture = ArgumentCaptor.forClass(SurveillanceCase.class);
		verify(f.dao).save(capture.capture());
		SurveillanceCase value = capture.getValue();
		when(f.dao.byUuid(SurveillanceCase.class, value.getUuid())).thenReturn(value);
		SurveillanceCaseTravel original = value.getTravelHistory().iterator().next();
		assertSame(f.actor, original.getCreator());
		assertNull(original.getChangedBy());
		f.surveillanceRequest.travelHistory = created.travelHistory;
		f.surveillanceRequest.travelHistory.get(0).endDate = "2026-01-03";
		TravelHistoryEntry added = new TravelHistoryEntry();
		added.travelAddress = "center";
		added.startDate = "2026-01-04";
		added.endDate = "2026-01-05";
		f.surveillanceRequest.travelHistory.add(added);
		SurveillanceCaseResponse edited = f.service.updateDraft(value.getUuid(), f.surveillanceRequest);
		assertEquals(2, edited.travelHistory.size());
		assertEquals(original.getUuid(), edited.travelHistory.get(0).uuid);
		assertSame(f.actor, original.getChangedBy());
		assertNotNull(original.getDateChanged());
		assertEquals(2, f.service.getDraft(value.getUuid()).travelHistory.size());
		f.surveillanceRequest.travelHistory = Collections.emptyList();
		assertTrue(f.service.updateDraft(value.getUuid(), f.surveillanceRequest).travelHistory.isEmpty());
		verify(f.access).require(SurveillanceConstants.REGISTER);
		verify(f.access, times(2)).require(SurveillanceConstants.EDIT);
	}

	@Test
	public void rejectsForeignOrDuplicateTravelIdentity() {
		SyntheticFixture f = fixture("center", "2026-01-01", "2026-01-02");
		f.surveillanceRequest.travelHistory.get(0).uuid = "another-case-trip";
		rejects(f, "uuid", "selection");
	}
}
