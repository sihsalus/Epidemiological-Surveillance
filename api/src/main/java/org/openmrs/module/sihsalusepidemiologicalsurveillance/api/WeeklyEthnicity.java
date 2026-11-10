package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.util.HashSet;
import java.util.Set;

import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog;

/** Weekly form groups, using content's MINSA codes rather than concept IDs or PDF ranges. */
public final class WeeklyEthnicity {

	public final Integer group;

	public final Integer ethnicity;

	private WeeklyEthnicity(Integer group, Integer ethnicity) {
		this.group = group;
		this.ethnicity = ethnicity;
	}

	public static WeeklyEthnicity from(Concept concept, ClinicalCatalog metadata) {
		if (concept == null || concept.getRetired())
			return new WeeklyEthnicity(null, null);
		Set<Integer> codes = new HashSet<Integer>();
		for (ConceptMap mapping : concept.getConceptMappings()) {
			ConceptReferenceTerm term = mapping.getConceptReferenceTerm();
			if (term == null || term.getRetired() || term.getConceptSource() == null
			        || term.getConceptSource().getRetired()
			        || !metadata.ethnicityCodeSource.equals(term.getConceptSource().getName())
			        || mapping.getConceptMapType() == null || mapping.getConceptMapType().getRetired()
			        || !"SAME-AS".equals(mapping.getConceptMapType().getName()))
				continue;
			try {
				codes.add(Integer.valueOf(term.getCode()));
			}
			catch (NumberFormatException ex) {
				return new WeeklyEthnicity(null, null);
			}
		}
		if (codes.isEmpty())
			return new WeeklyEthnicity(metadata.ethnicityGroups.get(concept.getUuid()), null);
		if (codes.size() != 1)
			return new WeeklyEthnicity(null, null);
		int code = codes.iterator().next();
		if (code >= 1 && code <= 55)
			return new WeeklyEthnicity(code == 2 || code == 19 || code == 40 || code == 49 ? 3 : 4, code);
		switch (code) {
			case 56: return new WeeklyEthnicity(2, null); // Afroperuano
			case 57: return new WeeklyEthnicity(6, null); // Blanco
			case 58: return new WeeklyEthnicity(1, null); // Mestizo
			case 59: return new WeeklyEthnicity(5, null); // Asiaticodescendiente
			case 60: return new WeeklyEthnicity(6, null); // Otro
			default: return new WeeklyEthnicity(null, null);
		}
	}
}
