package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog;

/** UUIDs checked against sihsalus-content; no runtime JSON or global property. */
public final class SurveillanceCatalog {
	
	private SurveillanceCatalog() {
	}
	
	public static ClinicalCatalog create() {
		ClinicalCatalog m = new ClinicalCatalog();
		m.surveillanceStartDate = "2026-01-01";
		m.ethnicityAttributeTypeUuid = "8d871386-c2cc-11de-8d13-0010c6dffd0f";
		// Group-only answers in content have no MINSA-ETNIAS mapping.
		m.ethnicityGroups.put("948c61a0-0253-4e2c-9ec9-3baa71e904cb", 3); // Andino
		m.ethnicityGroups.put("0438248a-7a6d-4b62-aad5-7c0128a2fc5d", 2); // Afro Descendiente
		m.ethnicityGroups.put("7bb475f1-79ca-49d4-9dd1-294922f9f2aa", 4); // Indigena Amazonico
		m.ethnicityGroups.put("4cf9f13f-bbac-50db-8fac-85205b58b44c", 6); // Otro no codificado
		m.icd10SourceUuid = "4faa9f66-d80f-4685-9645-af206fce7fa5";
		return m;
	}
}
