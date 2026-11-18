package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.*;
import org.openmrs.api.*;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ServiceContext;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.AlertRules;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.SurveillanceAccess;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.SurveillanceException;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db.ClinicalData;
import org.openmrs.notification.Alert;
import org.openmrs.notification.AlertService;

/** OpenMRS service boundary and authorization, with synthetic services in place of the platform. */
public class ClinicalBoundaryTest {
	
	private final UserContext user = mock(UserContext.class);
	
	private ServiceContext services;
	
	@Before
	public void installSyntheticPlatform() {
		ServiceContext.destroyInstance();
		services = ServiceContext.getInstance();
		Context.setContext(services);
		Context.setUserContext(user);
	}
	
	@After
	public void removeSyntheticPlatform() {
		Context.clearUserContext();
		ServiceContext.destroyInstance();
	}
	
	@Test
	public void accessRequiresAnAuthenticatedUserHoldingThePrivilege() {
		User authenticated = new User(5);
		SurveillanceAccess access = new SurveillanceAccess();
		when(user.isAuthenticated()).thenReturn(false);
		try {
			access.require("View Surveillance Cases");
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals(401, ex.getStatus());
		}
		when(user.isAuthenticated()).thenReturn(true);
		when(user.getAuthenticatedUser()).thenReturn(authenticated);
		when(user.hasPrivilege("View Surveillance Cases")).thenReturn(false);
		try {
			access.require("View Surveillance Cases");
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals(403, ex.getStatus());
		}
		when(user.hasPrivilege("View Surveillance Cases")).thenReturn(true);
		assertSame(authenticated, access.require("View Surveillance Cases"));
	}
	
	@Test
	public void clinicalReferencesAreReadThroughTheNativeOpenmrsServices() {
		ConceptService concepts = mock(ConceptService.class);
		PatientService patients = mock(PatientService.class);
		EncounterService encounters = mock(EncounterService.class);
		DiagnosisService diagnoses = mock(DiagnosisService.class);
		OrderService orders = mock(OrderService.class);
		LocationService locations = mock(LocationService.class);
		ProviderService providers = mock(ProviderService.class);
		ObsService observations = mock(ObsService.class);
		PersonService persons = mock(PersonService.class);
		services.setConceptService(concepts);
		services.setPatientService(patients);
		services.setEncounterService(encounters);
		services.setDiagnosisService(diagnoses);
		services.setOrderService(orders);
		services.setLocationService(locations);
		services.setProviderService(providers);
		services.setObsService(observations);
		services.setPersonService(persons);
		Concept concept = new Concept(1), yes = new Concept(2), no = new Concept(3);
		Patient patient = new Patient(4);
		Encounter encounter = new Encounter(5);
		Diagnosis diagnosis = new Diagnosis();
		TestOrder order = new TestOrder();
		Location location = new Location(6);
		Provider provider = new Provider(7);
		Obs obs = new Obs(8);
		PersonAttributeType type = new PersonAttributeType(9);
		when(concepts.getConceptByUuid("c")).thenReturn(concept);
		when(concepts.getConcept(1)).thenReturn(concept);
		when(concepts.getTrueConcept()).thenReturn(yes);
		when(concepts.getFalseConcept()).thenReturn(no);
		when(patients.getPatientByUuid("p")).thenReturn(patient);
		when(encounters.getEncounterByUuid("e")).thenReturn(encounter);
		when(diagnoses.getDiagnosisByUuid("d")).thenReturn(diagnosis);
		when(orders.getOrderByUuid("o")).thenReturn(order);
		when(locations.getLocationByUuid("l")).thenReturn(location);
		when(providers.getProviderByUuid("v")).thenReturn(provider);
		when(observations.getObsByUuid("b")).thenReturn(obs);
		when(persons.getPersonAttributeTypeByUuid("t")).thenReturn(type);
		ClinicalData clinical = new ClinicalData();
		assertSame(concept, clinical.concept("c"));
		assertSame(concept, clinical.concept(1));
		assertSame(yes, clinical.trueConcept());
		assertSame(no, clinical.falseConcept());
		assertSame(patient, clinical.patient("p"));
		assertSame(encounter, clinical.encounter("e"));
		assertSame(diagnosis, clinical.diagnosis("d"));
		assertSame(order, clinical.order("o"));
		assertSame(location, clinical.location("l"));
		assertSame(provider, clinical.provider("v"));
		assertSame(obs, clinical.observation("b"));
		assertSame(type, clinical.attributeType("t"));
	}
	
	@Test
	public void responsiblesAreAlertedWithTemporaryPrivilegesThatAreAlwaysRemoved() {
		UserService users = mock(UserService.class);
		AlertService alerts = mock(AlertService.class);
		services.setUserService(users);
		services.setAlertService(alerts);
		User responsible = mock(User.class), retired = mock(User.class), other = mock(User.class);
		for (String privilege : AlertRules.PRIVILEGES)
			when(responsible.hasPrivilege(privilege)).thenReturn(true);
		when(retired.isRetired()).thenReturn(true);
		when(users.getAllUsers()).thenReturn(Arrays.asList(responsible, retired, other));
		when(user.hasPrivilege("Get Users")).thenReturn(false);
		when(user.hasPrivilege("Manage Alerts")).thenReturn(true);
		new ClinicalData().notifySurveillanceResponsibles("synthetic alert");
		verify(alerts, times(1)).saveAlert(any(Alert.class));
		verify(user).addProxyPrivilege("Get Users");
		verify(user).removeProxyPrivilege("Get Users");
		verify(user, never()).addProxyPrivilege("Manage Alerts");
	}
}
