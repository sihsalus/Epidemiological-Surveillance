package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.util.*;

import org.openmrs.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;

public class CaseObservations {
	
	public static Obs find(Encounter encounter, String conceptUuid) {
		Obs found = null;
		for (Obs obs : encounter.getAllObs(false)) {
			if (!obs.getVoided() && obs.getConcept() != null && Objects.equals(conceptUuid, obs.getConcept().getUuid())) {
				if (found != null)
					throw new SurveillanceException(422, "AMBIGUOUS_SOURCE_DATA");
				found = obs;
			}
		}
		return found;
	}
	
	public static String text(Encounter encounter, ClinicalCatalog m, String key) {
		Obs obs = find(encounter, m.questions.get(key));
		return obs == null ? null : obs.getValueText();
	}
	
	public static String coded(Encounter encounter, ClinicalCatalog m, String key) {
		Obs obs = find(encounter, m.questions.get(key));
		return obs == null || obs.getValueCoded() == null ? null : obs.getValueCoded().getUuid();
	}
	
	public static String choiceKey(List<ClinicalCatalog.Choice> choices, String uuid) {
		for (ClinicalCatalog.Choice choice : choices)
			if (Objects.equals(choice.conceptUuid, uuid))
				return choice.key;
		return null;
	}
	
	public CaseRecord read(Encounter e, ClinicalCatalog m, Map<String, String> eventConcepts) {
		CaseRecord result = new CaseRecord();
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(m);
		result.encounterUuid = e.getUuid();
		result.eventUuid = eventConcepts.get(coded(e, m, "event"));
		result.locationUuid = e.getLocation() == null ? null : e.getLocation().getUuid();
		result.status = choiceKey(m.statuses, coded(e, m, "status"));
		result.origin = choiceKey(m.origins, coded(e, m, "origin"));
		if (result.eventUuid != null) {
			ClinicalCatalog.Disease disease = ClinicalCatalogService.disease(m, result.eventUuid);
			if (disease != null && disease.severities != null && !disease.severities.isEmpty()) {
				result.severity = choiceKey(disease.severities, coded(e, m, "severity"));
			}
		}
		Obs onset = find(e, m.questions.get("onset"));
		if (onset != null && onset.getValueDatetime() != null)
			result.onset = calendar.local(onset.getValueDatetime());
		if (e.getPatient().getBirthdate() != null)
			result.birthDate = calendar.local(e.getPatient().getBirthdate());
		result.gender = e.getPatient().getGender();
		result.ethnicity = coded(e, m, "ethnicity");
		Obs pregnancy = find(e, m.questions.get("pregnancy"));
		result.pregnant = pregnancy == null ? null : booleanValue(pregnancy, m);
		result.deceased = Boolean.TRUE.equals(e.getPatient().getDead());
		return result;
	}
	
	public static Boolean booleanValue(Obs observation, ClinicalCatalog m) {
		if (observation == null || observation.getValueCoded() == null)
			return null;
		String uuid = observation.getValueCoded().getUuid();
		return java.util.Objects.equals(uuid, m.trueConceptUuid) ? Boolean.TRUE
		        : java.util.Objects.equals(uuid, m.falseConceptUuid) ? Boolean.FALSE : null;
	}
}
