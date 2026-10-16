package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.time.*;
import java.util.*;

import org.openmrs.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db.ClinicalData;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;

public class CaseValidator {
	
	private ClinicalData clinical;
	
	private org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db.SurveillanceDao dao;
	
	public void setClinical(ClinicalData value) {
		clinical = value;
	}
	
	public void setDao(org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db.SurveillanceDao value) {
		dao = value;
	}
	
	public static class Validated {
		
		public Patient patient;
		
		public Encounter source;
		
		public Location location;
		
		public Provider provider;
		
		public Concept diagnosis;
		
		public String icd10;
		
		public Obs laboratory;
		
		public LocalDate onset;
		
		public Boolean pregnant;
		
		public String ethnicity;
		
		public ClinicalCatalog.Disease disease;
	}
	
	public Validated validate(CaseRequest r, ClinicalCatalog m, User actor) {
		List<String> missing = new ArrayList<String>();
		if (r == null)
			throw new SurveillanceException(422, "REQUIRED_FIELDS");
		String[] values = { r.uuid, r.patientUuid, r.sourceEncounterUuid, r.providerUuid, r.locationUuid, r.eventUuid,
		        r.status, r.origin, r.onsetDate };
		String[] names = { "uuid", "patientUuid", "sourceEncounterUuid", "providerUuid", "locationUuid", "eventUuid",
		        "status", "origin", "onsetDate" };
		for (int i = 0; i < values.length; i++)
			if (!ClinicalCatalogService.present(values[i]))
				missing.add(names[i]);
		if (!missing.isEmpty())
			throw new SurveillanceException(422, "REQUIRED_FIELDS", missing);
		try {
			for (String uuid : Arrays.asList(r.uuid, r.patientUuid, r.sourceEncounterUuid, r.providerUuid, r.locationUuid,
			    r.eventUuid))
				if (!UUID.fromString(uuid).toString().equalsIgnoreCase(uuid))
					throw new IllegalArgumentException();
		}
		catch (IllegalArgumentException ex) {
			throw new SurveillanceException(422, "INVALID_REFERENCE");
		}
		Validated v = new Validated();
		v.patient = clinical.patient(r.patientUuid);
		v.source = clinical.encounter(r.sourceEncounterUuid);
		v.location = clinical.location(r.locationUuid);
		v.provider = clinical.provider(r.providerUuid);
		if (v.patient == null || v.patient.getVoided() || v.source == null || v.source.getVoided()
		        || !v.patient.equals(v.source.getPatient()) || v.source.getVisit() == null
		        || v.source.getEncounterDatetime() == null || v.source.getEncounterDatetime().after(new Date())
		        || v.source.getVisit().getVoided() || !v.patient.equals(v.source.getVisit().getPatient()))
			throw new SurveillanceException(422, "INVALID_SOURCE_ENCOUNTER");
		if (v.location == null || v.location.getRetired())
			throw new SurveillanceException(422, "INVALID_LOCATION");
		if (v.source.getLocation() == null || !v.source.getLocation().equals(v.location))
			throw new SurveillanceException(422, "INVALID_LOCATION");
		if (v.provider == null || v.provider.getRetired() || v.provider.getPerson() == null || actor == null
		        || !v.provider.getPerson().equals(actor.getPerson()))
			throw new SurveillanceException(403, "INVALID_PROVIDER");
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(m);
		try {
			v.onset = LocalDate.parse(r.onsetDate);
		}
		catch (Exception ex) {
			throw new SurveillanceException(422, "INVALID_ONSET_DATE");
		}
		if (v.onset.isAfter(calendar.today())
		        || (v.patient.getBirthdate() != null && v.onset.isBefore(calendar.local(v.patient.getBirthdate())))
		        || v.onset.isAfter(calendar.local(v.source.getEncounterDatetime()))
		        || (v.patient.getDeathDate() != null && v.onset.isAfter(calendar.local(v.patient.getDeathDate()))))
			throw new SurveillanceException(422, "INVALID_ONSET_DATE");
		v.disease = ClinicalCatalogService.disease(m, r.eventUuid);
		ClinicalCatalogService.choice(m.statuses, r.status);
		ClinicalCatalogService.choice(m.origins, r.origin);
		if (v.disease != null && !v.disease.severities.isEmpty()) {
			if (!ClinicalCatalogService.present(r.severity))
				throw new SurveillanceException(422, "REQUIRED_FIELDS", Collections.singletonList("severity"));
			ClinicalCatalogService.choice(v.disease.severities, r.severity);
		}
		if (v.disease != null && !v.disease.species.isEmpty())
			ClinicalCatalogService.choice(v.disease.species, r.species);
		else if (ClinicalCatalogService.present(r.species))
			throw new SurveillanceException(422, "INVALID_CLASSIFICATION");
		if (v.disease != null && v.disease.diagnoses != null) {
			for (ClinicalCatalog.DiagnosisMapping mapping : v.disease.diagnoses)
				if (Objects.equals(empty(mapping.severity), empty(r.severity))
				        && Objects.equals(empty(mapping.species), empty(r.species))) {
					v.diagnosis = clinical.concept(mapping.diagnosisConceptUuid);
					v.icd10 = mapping.icd10Code;
				}
			if (v.diagnosis == null && "SEVERE".equals(r.severity))
				for (ClinicalCatalog.DiagnosisMapping mapping : v.disease.diagnoses)
					if ("SEVERE".equals(mapping.severity) && !ClinicalCatalogService.present(mapping.species)) {
						v.diagnosis = clinical.concept(mapping.diagnosisConceptUuid);
						v.icd10 = mapping.icd10Code;
					}
		}
		if (v.diagnosis == null && dao != null) {
			org.openmrs.module.sihsalusepidemiologicalsurveillance.model.NotifiableEvent event = dao
			        .byUuid(org.openmrs.module.sihsalusepidemiologicalsurveillance.model.NotifiableEvent.class, r.eventUuid);
			if (event != null && event.getConcept() != null) {
				v.diagnosis = event.getConcept();
			}
		}
		if (v.diagnosis == null || v.diagnosis.getRetired())
			throw new SurveillanceException(422, "DIAGNOSIS_MAPPING_UNAVAILABLE");
		if (!ClinicalCatalogService.present(v.icd10))
			v.icd10 = ClinicalCatalogService.icd10OrNull(v.diagnosis, m);
		validateLab(r, m, v, calendar);
		Obs pregnancy = CaseObservations.find(v.source, m.questions.get("pregnancy"));
		if (pregnancy != null)
			v.pregnant = CaseObservations.booleanValue(pregnancy, m);
		if (v.pregnant == null && ClinicalCatalogService.present(m.pregnancyAttributeTypeUuid)) {
			PersonAttributeType type = clinical.attributeType(m.pregnancyAttributeTypeUuid);
			PersonAttribute attr = type == null || !"java.lang.Boolean".equals(type.getFormat()) ? null
			        : v.patient.getAttribute(type);
			if (attr != null && !attr.getVoided()) {
				if ("true".equalsIgnoreCase(attr.getValue()))
					v.pregnant = true;
				else if ("false".equalsIgnoreCase(attr.getValue()))
					v.pregnant = false;
			}
		}
		if (ClinicalCatalogService.present(m.ethnicityAttributeTypeUuid)) {
			PersonAttributeType type = clinical.attributeType(m.ethnicityAttributeTypeUuid);
			PersonAttribute attr = type == null || !"org.openmrs.Concept".equals(type.getFormat()) ? null
			        : v.patient.getAttribute(type);
			if (attr != null && !attr.getVoided())
				try {
					Concept ethnicity = clinical.concept(Integer.valueOf(attr.getValue()));
					if (ethnicity != null && !ethnicity.getRetired())
						v.ethnicity = ethnicity.getUuid();
				}
				catch (NumberFormatException ignored) {
					// An invalid person attribute must not be represented as a clinical concept.
				}
		}
		if (v.ethnicity == null)
			v.ethnicity = CaseObservations.coded(v.source, m, "ethnicity");
		return v;
	}
	
	private void validateLab(CaseRequest r, ClinicalCatalog m, Validated v, EpidemiologicalCalendar calendar) {
		if (!ClinicalCatalogService.present(r.laboratoryResultUuid)) {
			if (!"SUSPECTED".equals(r.status))
				throw new SurveillanceException(422, "LAB_RESULT_REQUIRED");
			return;
		}
		Obs result = clinical.observation(r.laboratoryResultUuid);
		v.laboratory = result;
		if (result == null || result.getVoided() || result.getPerson() == null
		        || !result.getPerson().getUuid().equals(v.patient.getUuid()) || result.getConcept() == null
		        || result.getEncounter() == null || result.getEncounter().getVoided()
		        || !v.patient.equals(result.getEncounter().getPatient())
		        || (!result.getEncounter().equals(v.source)
		                && (v.source.getVisit() == null || !v.source.getVisit().equals(result.getEncounter().getVisit())))
		        || (result.getOrder() != null && (result.getOrder().getVoided()
		                || !v.patient.equals(result.getOrder().getPatient())
		                || (result.getOrder().getEncounter() != null && result.getOrder().getEncounter().getVoided())))
		        || result.getValueCoded() == null || result.getObsDatetime() == null
		        || calendar.local(result.getObsDatetime()).isBefore(v.onset) || result.getObsDatetime().after(new Date()))
			throw new SurveillanceException(422, "INVALID_LAB_RESULT");
		String expected = null;
		if (v.disease != null && v.disease.laboratoryTests != null && !v.disease.laboratoryTests.isEmpty()) {
			for (ClinicalCatalog.LabTest test : v.disease.laboratoryTests) {
				if (test.resultConceptUuid.equals(result.getConcept().getUuid()) && (result.getOrder() == null
				        || result.getOrder().getConcept() == null || test.orderConceptUuid == null
				        || test.orderConceptUuid.equals(result.getOrder().getConcept().getUuid()))) {
					if (test.positiveAnswerUuids.contains(result.getValueCoded().getUuid()))
						expected = "CONFIRMED";
					if (test.negativeAnswerUuids.contains(result.getValueCoded().getUuid()))
						expected = "DISCARDED";
				}
			}
		}
		if (expected == null && result.getValueCoded() != null) {
			String codedUuid = result.getValueCoded().getUuid();
			if (Objects.equals(codedUuid, m.trueConceptUuid)) {
				expected = "CONFIRMED";
			} else if (Objects.equals(codedUuid, m.falseConceptUuid)) {
				expected = "DISCARDED";
			} else {
				String text = "";
				if (result.getValueCoded().getName() != null && result.getValueCoded().getName().getName() != null) {
					text = result.getValueCoded().getName().getName().toLowerCase();
				} else if (result.getValueCoded().getDisplayString() != null) {
					text = result.getValueCoded().getDisplayString().toLowerCase();
				}
				if (text.contains("no reactiv") || text.contains("non-reactive") || text.contains("negativ")
				        || text.contains("no detectad") || text.contains("ausent")) {
					expected = "DISCARDED";
				} else if (text.contains("reactiv") || text.contains("positiv") || text.contains("detectad")
				        || text.contains("present")) {
					expected = "CONFIRMED";
				}
			}
		}
		if (expected == null || !expected.equals(r.status))
			throw new SurveillanceException(422, "LAB_STATUS_CONFLICT");
	}
	
	private static String empty(String value) {
		return value == null ? "" : value;
	}
}
