package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db.ClinicalData;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog;

public class ClinicalCatalogServiceTest {
	
	@Test
	public void opensFixedCatalogWithoutRequiringInstalledConceptsOrEvents() {
		ClinicalData clinical = mock(ClinicalData.class);
		ClinicalCatalogService service = new ClinicalCatalogService();
		service.setClinical(clinical);
		ClinicalCatalog catalog = service.get();
		assertEquals("America/Lima", catalog.timezone);
		assertEquals("SUNDAY", catalog.firstDayOfWeek);
		assertEquals(4, catalog.minimalDaysInFirstWeek);
		assertEquals("4faa9f66-d80f-4685-9645-af206fce7fa5", catalog.icd10SourceUuid);
		assertEquals("8d871386-c2cc-11de-8d13-0010c6dffd0f", catalog.ethnicityAttributeTypeUuid);
		assertNull(catalog.trueConceptUuid);
		verify(clinical, never()).concept(anyString());
	}
	
	@Test
	public void eachRequestReceivesAnIndependentFixedCatalog() {
		ClinicalCatalog first = SurveillanceCatalog.create();
		first.ethnicityGroups.clear();
		assertEquals(4, SurveillanceCatalog.create().ethnicityGroups.size());
	}
}
