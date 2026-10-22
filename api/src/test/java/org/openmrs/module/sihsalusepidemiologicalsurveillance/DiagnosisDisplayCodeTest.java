package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;

import java.util.Locale;

import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.api.ConceptNameType;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.ClinicalCatalogService;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog;

public class DiagnosisDisplayCodeTest {
	
	@Test
	public void resolvesMinsaShortCodeWithoutMappingsButDoesNotGuessFromDiagnosisText() {
		Concept concept = new Concept();
		ClinicalCatalog catalog = new ClinicalCatalog();
		catalog.icd10SourceUuid = "icd";
		concept.addName(new ConceptName("DENGUE GRAVE", Locale.ENGLISH));
		assertNull(ClinicalCatalogService.icd10OrNull(concept, catalog));
		ConceptName code = new ConceptName("A972", Locale.ENGLISH);
		code.setConceptNameType(ConceptNameType.SHORT);
		concept.addName(code);
		assertEquals("A972", ClinicalCatalogService.icd10OrNull(concept, catalog));
		code.setVoided(true);
		assertNull(ClinicalCatalogService.icd10OrNull(concept, catalog));
	}
}
