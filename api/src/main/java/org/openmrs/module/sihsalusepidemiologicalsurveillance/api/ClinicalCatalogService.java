package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.util.*;

import org.openmrs.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db.ClinicalData;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog;

/** Fixed application catalogue. Opening the component never validates all clinical content. */
public class ClinicalCatalogService {
	
	private ClinicalData clinical;
	
	public void setClinical(ClinicalData value) {
		clinical = value;
	}
	
	public ClinicalCatalog get() {
		ClinicalCatalog catalog = SurveillanceCatalog.create();
		Concept yes = clinical.trueConcept(), no = clinical.falseConcept();
		catalog.trueConceptUuid = yes == null ? null : yes.getUuid();
		catalog.falseConceptUuid = no == null ? null : no.getUuid();
		return catalog;
	}
	
	public static ClinicalCatalog.Choice choice(List<ClinicalCatalog.Choice> choices, String key) {
		for (ClinicalCatalog.Choice choice : choices)
			if (Objects.equals(choice.key, key))
				return choice;
		throw new SurveillanceException(422, "INVALID_CLASSIFICATION");
	}
	
	public static ClinicalCatalog.Disease disease(ClinicalCatalog m, String uuid) {
		if (m == null || m.diseases == null || uuid == null)
			return null;
		for (ClinicalCatalog.Disease disease : m.diseases)
			if (Objects.equals(disease.eventUuid, uuid))
				return disease;
		return null;
	}
	
	public static String icd10(Concept concept, ClinicalCatalog m) {
		Set<String> codes = new HashSet<String>();
		for (ConceptMap map : concept.getConceptMappings()) {
			ConceptReferenceTerm term = map.getConceptReferenceTerm();
			if (term != null && !term.getRetired() && term.getConceptSource() != null
			        && m.icd10SourceUuid.equals(term.getConceptSource().getUuid()))
				codes.add(term.getCode());
		}
		if (codes.size() != 1)
			throw new SurveillanceException(503, "ICD10_MAPPING_UNAVAILABLE");
		return codes.iterator().next();
	}
	
	/**
	 * Like {@link #icd10} but returns {@code null} when no unique ICD10 code is found (dynamic events).
	 */
	public static String icd10OrNull(Concept concept, ClinicalCatalog m) {
		Set<String> codes = new HashSet<String>();
		for (ConceptMap map : concept.getConceptMappings()) {
			ConceptReferenceTerm term = map.getConceptReferenceTerm();
			if (term != null && !term.getRetired() && term.getConceptSource() != null
			        && m.icd10SourceUuid.equals(term.getConceptSource().getUuid()))
				codes.add(term.getCode());
		}
		return codes.size() == 1 ? codes.iterator().next() : null;
	}
	
	public static String diagnosisCode(ClinicalCatalog m, Concept concept) {
		for (ClinicalCatalog.Disease disease : m.diseases)
			for (ClinicalCatalog.DiagnosisMapping mapping : disease.diagnoses)
				if (mapping.diagnosisConceptUuid.equals(concept.getUuid()) && present(mapping.icd10Code))
					return mapping.icd10Code;
		return icd10(concept, m);
	}
	
	public static boolean present(String text) {
		return text != null && !text.trim().isEmpty();
	}
	
}
