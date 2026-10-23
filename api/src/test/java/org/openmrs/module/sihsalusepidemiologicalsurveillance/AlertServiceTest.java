package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.Test;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

public class AlertServiceTest {
	
	@Test
	public void displaysAttendantsNameWhileRetainingStableAuditIdentity() {
		SyntheticFixture f = new SyntheticFixture();
		f.actor.setUsername("synthetic.login");
		org.openmrs.PersonName name = new org.openmrs.PersonName("Ana", null, "Prueba");
		f.actor.getPerson().addName(name);
		SurveillanceAlert alert = new SurveillanceAlert();
		alert.setUuid("synthetic-alert");
		alert.setStatus("PENDING");
		alert.setGeneratedAt(new Date());
		when(f.dao.byUuid(SurveillanceAlert.class, alert.getUuid())).thenReturn(alert);
		when(f.dao.alertAttendant(f.actor.getUuid())).thenReturn(f.actor);
		assertEquals("Ana Prueba", f.service.attendAlert(alert.getUuid()).get("attendedBy"));
		assertEquals(f.actor.getUuid(), alert.getAttendedBy());
		when(f.dao.alerts()).thenReturn(Arrays.asList(alert));
		assertEquals("Ana Prueba", f.service.alerts(null, null, null).get(0).get("attendedBy"));
		alert.setAttendedBy("synthetic.login");
		when(f.dao.alertAttendant("synthetic.login")).thenReturn(f.actor);
		assertEquals("Ana Prueba", f.service.alerts(null, null, null).get(0).get("attendedBy"));
		alert.setAttendedBy("missing-user");
		assertNull(f.service.alerts(null, null, null).get(0).get("attendedBy"));
	}
	
	@Test
	public void immediatelyNotifiableCaseWithNotificationDateDoesNotAlert() {
		SyntheticFixture f = new SyntheticFixture();
		f.event.setPeriodicity("INMEDIATA");
		assertTrue(f.service.createDraft(f.surveillanceRequest).generatedAlerts.isEmpty());
		verify(f.dao, never()).saveAlert(any());
	}
	
	@Test
	public void registrationAndEditsPersistOncePerOccurrenceAndNotifyResponsibles() {
		SyntheticFixture f = new SyntheticFixture();
		f.event.setPeriodicity("INMEDIATA");
		f.surveillanceRequest.notificationDate = null;
		List<SurveillanceCase> cases = new ArrayList<>();
		List<SurveillanceAlert> alerts = new ArrayList<>();
		when(f.dao.surveillanceCases()).thenReturn(cases);
		when(f.dao.alerts()).thenAnswer(i -> new ArrayList<>(alerts));
		when(f.dao.save(any())).thenAnswer(i -> {
			Object value = i.getArgument(0);
			if (value instanceof SurveillanceCase && !cases.contains(value))
				cases.add((SurveillanceCase) value);
			if (value instanceof SurveillanceAlert && !alerts.contains(value))
				alerts.add((SurveillanceAlert) value);
			return value;
		});
		when(f.dao.saveAlert(any())).thenAnswer(i -> {
			SurveillanceAlert a = i.getArgument(0);
			alerts.add(a);
			return a;
		});
		String uuid = f.service.createDraft(f.surveillanceRequest).uuid;
		assertEquals(1, alerts.size());
		assertEquals("IMMEDIATE_EVENT", alerts.get(0).getReason());
		when(f.dao.byUuid(SurveillanceCase.class, uuid)).thenReturn(cases.get(0));
		f.surveillanceRequest.diagnosisType = "DESCARTADO";
		assertEquals(1, f.service.updateDraft(uuid, f.surveillanceRequest).generatedAlerts.size());
		assertTrue(f.service.updateDraft(uuid, f.surveillanceRequest).generatedAlerts.isEmpty());
		f.surveillanceRequest.diagnosisType = "PROBABLE";
		f.service.updateDraft(uuid, f.surveillanceRequest);
		f.surveillanceRequest.diagnosisType = "DESCARTADO";
		f.service.updateDraft(uuid, f.surveillanceRequest);
		assertEquals(4, alerts.size());
		assertEquals(4, alerts.stream().map(SurveillanceAlert::getOccurrenceKey).distinct().count());
		verify(f.clinical, times(4)).notifySurveillanceResponsibles(anyString());
	}
	
	@Test
	public void filtersAndAttendanceAreAuthorizedAndIdempotent() {
		SyntheticFixture f = new SyntheticFixture();
		SurveillanceAlert a = new SurveillanceAlert();
		a.setUuid("alert");
		a.setType("IMMEDIATE");
		a.setReason("DEATH");
		a.setStatus("PENDING");
		a.setGeneratedAt(new Date());
		when(f.dao.alerts()).thenReturn(Arrays.asList(a));
		when(f.dao.byUuid(SurveillanceAlert.class, "alert")).thenReturn(a);
		assertEquals(1, f.service.alerts("IMMEDIATE", "DEATH", "PENDING").size());
		assertEquals(0, f.service.alerts("OUTBREAK", null, null).size());
		f.service.attendAlert("alert");
		Date attended = a.getAttendedAt();
		f.service.attendAlert("alert");
		assertSame(attended, a.getAttendedAt());
		verify(f.dao, times(1)).saveAlert(a);
		when(f.access.require(SurveillanceConstants.MANAGE)).thenThrow(new SurveillanceException(403, "ACCESS_DENIED"));
		try {
			f.service.alerts(null, null, null);
			fail();
		}
		catch (SurveillanceException e) {
			assertEquals(403, e.getStatus());
		}
		try {
			f.service.attendAlert("alert");
			fail();
		}
		catch (SurveillanceException e) {
			assertEquals(403, e.getStatus());
		}
	}
}
