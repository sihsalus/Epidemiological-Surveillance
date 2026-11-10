package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.util.*;

import org.openmrs.*;
import org.openmrs.api.ConceptNameType;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db.ClinicalData;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog;

/** Fixed application configuration. Opening the component never validates all clinical content. */
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
	
	/**
	 * Display/export code: prefer a unique ICD10 mapping, then a unique MINSA SHORT name. Missing or
	 * ambiguous codes return null.
	 */
	public static String icd10OrNull(Concept concept, ClinicalCatalog m) {
		Set<String> codes = new HashSet<String>();
		for (ConceptMap map : concept.getConceptMappings()) {
			ConceptReferenceTerm term = map.getConceptReferenceTerm();
			if (term != null && !term.getRetired() && term.getConceptSource() != null
			        && m.icd10SourceUuid.equals(term.getConceptSource().getUuid()))
				codes.add(term.getCode());
		}
		if (!codes.isEmpty())
			return codes.size() == 1 ? codes.iterator().next() : null;
		// MINSA/OCL diagnoses can carry their ICD-10 code in a SHORT name only.
		for (ConceptName name : concept.getNames()) {
			if (!name.getVoided() && ConceptNameType.SHORT.equals(name.getConceptNameType()) && present(name.getName())) {
				String code = name.getName().trim().toUpperCase(Locale.ROOT);
				if (code.matches("[A-Z][0-9][A-Z0-9.]{1,5}"))
					codes.add(code);
			}
		}
		return codes.size() == 1 ? codes.iterator().next() : null;
	}
	
	public static boolean present(String text) {
		return text != null && !text.trim().isEmpty();
	}
}
