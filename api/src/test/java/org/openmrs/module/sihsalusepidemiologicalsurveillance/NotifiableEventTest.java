package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.Test;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.SurveillanceException;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.NotifiableEvent;

public class NotifiableEventTest {
	
	@Test
	public void editsAllAttributesWithoutReplacingIdentityOrCreator() {
		SyntheticFixture f = new SyntheticFixture();
		String uuid = f.event.getUuid();
		f.event.setCreator(f.actor);
		Date created = new Date(1000);
		f.event.setDateCreated(created);
		org.openmrs.Concept concept = SyntheticFixture.concept(97);
		when(f.clinical.concept(concept.getUuid())).thenReturn(concept);
		Map<String, Object> request = body(f, "2026-02-01");
		request.put("conceptUuid", concept.getUuid());
		request.put("periodicity", "INMEDIATA");
		request.put("referenceRegulation", "NTS actualizada");
		request.put("active", false);
		Map<String, Object> result = f.service.updateEvent(uuid, request);
		assertEquals(uuid, result.get("uuid"));
		assertEquals(concept.getUuid(), result.get("conceptUuid"));
		assertEquals("INMEDIATA", result.get("periodicity"));
		assertEquals("NTS actualizada", result.get("referenceRegulation"));
		assertEquals(false, result.get("active"));
		assertFalse(result.containsKey("validFrom"));
		assertFalse(result.containsKey("validTo"));
		assertSame(f.actor, f.event.getCreator());
		assertSame(created, f.event.getDateCreated());
		assertSame(f.actor, f.event.getChangedBy());
		assertNotNull(f.event.getDateChanged());
		verify(f.dao).lockEvent(f.event);
		verify(f.dao).save(f.event);
		request.put("active", true);
		assertEquals(true, f.service.updateEvent(uuid, request).get("active"));
	}
	
	@Test
	public void invalidEditsDoNotMutateTheEvent() {
		for (String field : Arrays.asList("active", "periodicity", "referenceRegulation")) {
			SyntheticFixture f = new SyntheticFixture();
			Date originalStart = f.event.getValidFrom();
			Map<String, Object> request = body(f, "2026-02-01");
			request.put(field, "validTo".equals(field) ? "2025-01-01" : "");
			try {
				f.service.updateEvent(f.event.getUuid(), request);
				fail("Invalid edit must fail: " + field);
			}
			catch (SurveillanceException error) {
				assertEquals(422, error.getStatus());
			}
			assertSame(originalStart, f.event.getValidFrom());
			verify(f.dao, never()).save(any(NotifiableEvent.class));
		}
	}
	
	@Test
	public void editingRequiresManagePrivilege() {
		SyntheticFixture f = new SyntheticFixture();
		when(f.access.require(SurveillanceConstants.MANAGE)).thenThrow(new SurveillanceException(403, "FORBIDDEN"));
		try {
			f.service.updateEvent(f.event.getUuid(), body(f, "2026-02-01"));
			fail("Unauthorized edit must fail");
		}
		catch (SurveillanceException error) {
			assertEquals(403, error.getStatus());
		}
		verify(f.dao, never()).save(any(NotifiableEvent.class));
	}
	
	@Test
	public void newEventsAcceptImmediateAndRejectDailyPeriodicity() {
		SyntheticFixture f = new SyntheticFixture();
		when(f.dao.events()).thenReturn(Collections.<NotifiableEvent> emptyList());
		Map<String, Object> request = body(f, "2026-01-01");
		request.put("periodicity", "INMEDIATA");
		assertEquals("INMEDIATA", f.service.saveEvent(request).get("periodicity"));
		request.put("periodicity", "DIARIA");
		try {
			f.service.saveEvent(request);
			fail("Daily events must be rejected");
		}
		catch (SurveillanceException error) {
			assertEquals(422, error.getStatus());
			assertEquals("INVALID_EVENT", error.getCode());
		}
		verify(f.dao, times(1)).save(any(NotifiableEvent.class));
	}
	
	@Test
	public void rejectsDuplicateDiseaseEvenWhenInactive() {
		for (boolean active : new boolean[] { true, false }) {
			SyntheticFixture f = new SyntheticFixture();
			f.event.setActive(active);
			try {
				f.service.saveEvent(body(f, "2026-01-01"));
				fail("Duplicate disease must fail");
			}
			catch (SurveillanceException error) {
				assertEquals("EVENT_ALREADY_EXISTS", error.getCode());
				assertEquals(409, error.getStatus());
			}
			verify(f.dao, never()).save(any(NotifiableEvent.class));
		}
	}
	
	@Test
	public void postGeneratesUuidWhenItIsNotProvided() {
		SyntheticFixture f = new SyntheticFixture();
		when(f.dao.events()).thenReturn(Collections.<NotifiableEvent> emptyList());
		Map<String, Object> request = body(f, "2026-01-01");
		request.remove("uuid");
		
		String uuid = (String) f.service.saveEvent(request).get("uuid");
		
		assertNotNull(uuid);
		UUID.fromString(uuid);
		verify(f.dao).save(any(NotifiableEvent.class));
	}
	
	@Test
	public void stateCanBeChangedWithoutReplacingTheEvent() {
		SyntheticFixture f = new SyntheticFixture();
		Map<String, Object> result = f.service.updateEvent(f.event.getUuid(),
		    Collections.<String, Object> singletonMap("active", false));
		assertEquals(false, result.get("active"));
		verify(f.dao).lockEvent(f.event);
		verify(f.dao).save(f.event);
	}
	
	@Test
	public void stateRequiresABoolean() {
		SyntheticFixture f = new SyntheticFixture();
		try {
			f.service.updateEvent(f.event.getUuid(), Collections.<String, Object> singletonMap("active", "false"));
			fail();
		}
		catch (SurveillanceException error) {
			assertEquals(422, error.getStatus());
		}
	}
	
	@Test
	public void inactiveEventIsHiddenFromCatalogButRemainsEditable() {
		SyntheticFixture f = new SyntheticFixture();
		f.event.setActive(false);
		assertTrue(((List<?>) f.service.getCatalog().get("events")).isEmpty());
		assertTrue(f.service.getEvents(false).isEmpty());
		assertEquals(1, f.service.getEvents(true).size());
		f.service.updateEvent(f.event.getUuid(), Collections.<String, Object> singletonMap("active", true));
		assertEquals(1, ((List<?>) f.service.getCatalog().get("events")).size());
	}
	
	@Test
	public void inactiveDiagnosisCannotCreateANewCase() {
		SyntheticFixture f = new SyntheticFixture();
		f.event.setActive(false);
		try {
			f.service.createDraft(f.surveillanceRequest);
			fail("Inactive event must reject new cases");
		}
		catch (SurveillanceException error) {
			assertEquals("EVENT_RETIRED", error.getCode());
		}
		verify(f.dao, never())
		        .save(any(org.openmrs.module.sihsalusepidemiologicalsurveillance.model.SurveillanceCase.class));
	}
	
	private Map<String, Object> body(SyntheticFixture f, String validFrom) {
		Map<String, Object> body = new HashMap<String, Object>();
		body.put("uuid", SyntheticFixture.uuid(98));
		body.put("conceptUuid", f.event.getConcept().getUuid());
		body.put("periodicity", "SEMANAL");
		body.put("referenceRegulation", "NTS 228-MINSA/CDC-2025");
		body.put("active", true);
		when(f.clinical.concept(f.event.getConcept().getUuid())).thenReturn(f.event.getConcept());
		return body;
	}
}
