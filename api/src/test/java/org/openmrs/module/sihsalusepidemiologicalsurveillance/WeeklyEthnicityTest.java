package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;

import org.junit.Test;
import org.openmrs.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog;

public class WeeklyEthnicityTest {

	private final ClinicalCatalog metadata = SurveillanceCatalog.create();

	@Test
	public void classifiesAllSpecificContentCodesInsteadOfPdfRanges() {
		for (int code = 1; code <= 55; code++) {
			WeeklyEthnicity result = WeeklyEthnicity.from(concept(String.valueOf(code)), metadata);
			assertEquals(Integer.valueOf(code), result.ethnicity);
			assertEquals(Integer.valueOf(code == 2 || code == 19 || code == 40 || code == 49 ? 3 : 4), result.group);
		}
	}

	@Test
	public void mapsAggregateCodesWithoutInventingSpecificEthnicities() {
		int[] groups = { 2, 6, 1, 5, 6 };
		for (int code = 56; code <= 60; code++) {
			WeeklyEthnicity result = WeeklyEthnicity.from(concept(String.valueOf(code)), metadata);
			assertEquals(Integer.valueOf(groups[code - 56]), result.group);
			assertNull(result.ethnicity);
		}
	}

	@Test
	public void resolvesUnmappedGroupAnswersByStableContentIdentity() {
		for (String uuid : metadata.ethnicityGroups.keySet()) {
			Concept concept = new Concept();
			concept.setUuid(uuid);
			WeeklyEthnicity result = WeeklyEthnicity.from(concept, metadata);
			assertEquals(metadata.ethnicityGroups.get(uuid), result.group);
			assertNull(result.ethnicity);
		}
	}

	@Test
	public void leavesMissingUnknownRetiredAndAmbiguousCodesBlank() {
		assertBlank(null);
		assertBlank(new Concept(2)); // Internal IDs are not MINSA codes.
		for (String code : new String[] { "0", "61", "-1", "not-a-code" })
			assertBlank(concept(code));
		Concept retired = concept("2");
		retired.setRetired(true);
		assertBlank(retired);
		Concept ambiguous = concept("2");
		ambiguous.addConceptMapping(mapping("40"));
		assertBlank(ambiguous);
		Concept wrongSource = concept("2");
		wrongSource.getConceptMappings().iterator().next().getConceptReferenceTerm().getConceptSource().setName("OTHER");
		assertBlank(wrongSource);
		Concept retiredTerm = concept("2");
		retiredTerm.getConceptMappings().iterator().next().getConceptReferenceTerm().setRetired(true);
		assertBlank(retiredTerm);
		Concept nonEquivalent = concept("2");
		nonEquivalent.getConceptMappings().iterator().next().getConceptMapType().setName("NARROWER-THAN");
		assertBlank(nonEquivalent);
	}

	private void assertBlank(Concept concept) {
		WeeklyEthnicity result = WeeklyEthnicity.from(concept, metadata);
		assertNull(result.group);
		assertNull(result.ethnicity);
	}

	private Concept concept(String code) {
		Concept concept = new Concept(501);
		concept.addConceptMapping(mapping(code));
		return concept;
	}

	private ConceptMap mapping(String code) {
		ConceptSource source = new ConceptSource();
		source.setName("MINSA-ETNIAS");
		ConceptMapType type = new ConceptMapType();
		type.setName("SAME-AS");
		return new ConceptMap(new ConceptReferenceTerm(source, code, null), type);
	}
}
