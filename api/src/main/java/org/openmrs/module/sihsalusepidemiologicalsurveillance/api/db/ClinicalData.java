package org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db;

import org.openmrs.*;
import org.openmrs.api.context.Context;

/** Native OpenMRS service boundary; does not introduce a second clinical persistence model. */
public class ClinicalData {
	
	public Concept concept(String uuid) {
		return Context.getConceptService().getConceptByUuid(uuid);
	}
	
	public Concept concept(Integer id) {
		return Context.getConceptService().getConcept(id);
	}
	
	public Patient patient(String uuid) {
		return Context.getPatientService().getPatientByUuid(uuid);
	}
	
	public Encounter encounter(String uuid) {
		return Context.getEncounterService().getEncounterByUuid(uuid);
	}
	
	public Diagnosis diagnosis(String uuid) {
		return Context.getDiagnosisService().getDiagnosisByUuid(uuid);
	}
	
	public Order order(String uuid) {
		return Context.getOrderService().getOrderByUuid(uuid);
	}
	
	public EncounterType encounterType(String uuid) {
		return Context.getEncounterService().getEncounterTypeByUuid(uuid);
	}
	
	public EncounterRole encounterRole(String uuid) {
		return Context.getEncounterService().getEncounterRoleByUuid(uuid);
	}
	
	public Location location(String uuid) {
		return Context.getLocationService().getLocationByUuid(uuid);
	}
	
	public Provider provider(String uuid) {
		return Context.getProviderService().getProviderByUuid(uuid);
	}
	
	public Obs observation(String uuid) {
		return Context.getObsService().getObsByUuid(uuid);
	}
	
	public PersonAttributeType attributeType(String uuid) {
		return Context.getPersonService().getPersonAttributeTypeByUuid(uuid);
	}
	
	public ConceptSource conceptSource(String uuid) {
		return Context.getConceptService().getConceptSourceByUuid(uuid);
	}
	
	public Encounter saveEncounter(Encounter encounter) {
		return Context.getEncounterService().saveEncounter(encounter);
	}
	
	public Diagnosis saveDiagnosis(Diagnosis diagnosis) {
		return Context.getDiagnosisService().save(diagnosis);
	}
	
	public void alert(org.openmrs.User user, String text) {
		Context.getAlertService().saveAlert(new org.openmrs.notification.Alert(text, user));
	}
	
	public void notifySurveillanceResponsibles(String text) {
		// Internal notification only: case registrars need not hold user-directory/alert administration privileges.
		java.util.List<String> temporary = new java.util.ArrayList<>();
		try {
			for (String privilege : java.util.Arrays.asList("Get Users", "Manage Alerts")) {
				if (!Context.hasPrivilege(privilege)) {
					Context.addProxyPrivilege(privilege);
					temporary.add(privilege);
				}
			}
			for (User user : Context.getUserService().getAllUsers()) {
				if (!user.isRetired()
				        && org.openmrs.module.sihsalusepidemiologicalsurveillance.api.AlertRules.isResponsible(user))
					alert(user, text);
			}
		}
		finally {
			for (String privilege : temporary)
				Context.removeProxyPrivilege(privilege);
		}
	}
	
	public Concept trueConcept() {
		return Context.getConceptService().getTrueConcept();
	}
	
	public Concept falseConcept() {
		return Context.getConceptService().getFalseConcept();
	}
}
