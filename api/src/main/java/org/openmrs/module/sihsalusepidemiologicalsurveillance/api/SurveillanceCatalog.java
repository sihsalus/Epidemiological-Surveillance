package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.util.Arrays;

import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog;

/** UUIDs checked against sihsalus-content; no runtime JSON or global property. */
public final class SurveillanceCatalog {
	
	private SurveillanceCatalog() {
	}
	
	public static ClinicalCatalog create() {
		ClinicalCatalog m = new ClinicalCatalog();
		m.surveillanceStartDate = "2026-01-01";
		m.encounterRoleUuid = "240b26f9-dd88-4172-823d-4a8bfeb7841f";
		m.ethnicityAttributeTypeUuid = "8d871386-c2cc-11de-8d13-0010c6dffd0f";
		m.icd10SourceUuid = "4faa9f66-d80f-4685-9645-af206fce7fa5";
		m.questions.put("event", "4dfe981b-6604-5806-ae5c-74410a3a1d16");
		m.questions.put("status", "130be497-8e61-5b6d-9c72-79f7fbe5ec32");
		m.questions.put("severity", "d77a072d-01aa-5f50-907e-24889ad321f7");
		m.questions.put("origin", "a2982f6d-5422-5693-88a3-c9ccd704d544");
		m.questions.put("species", "6fbf94f6-ed2b-5ebe-853d-94b36116d2b8");
		m.questions.put("onset", "e439836b-1428-5c06-94a8-8598a6824770");
		m.questions.put("pregnancy", "ad88cd67-8e8f-5bcd-a925-82e034ae5813");
		m.questions.put("laboratoryResult", "a42dff01-d03e-5aa2-b0bf-734333abd78a");
		m.questions.put("ethnicity", "658d0e8e-b505-5e6c-a672-a0d111d42b36");
		m.statuses.addAll(Arrays.asList(choice("SUSPECTED", "Probable", "2c5f7bf2-a5f6-53d9-8de8-7bf5aa868f79"),
		    choice("CONFIRMED", "Confirmado", "99e455bc-63d9-59da-94f8-08a290611d86"),
		    choice("DISCARDED", "Descartado", "17b9149a-555a-5e2f-875c-6bd98c59be48")));
		m.origins.addAll(Arrays.asList(choice("AUTOCHTHONOUS", "Autóctono", "9bdcf5da-c70b-5695-8c27-b0f17dfd395e"),
		    choice("IMPORTED_NATIONAL", "Importado nacional", "fa4c6147-b4e1-5a16-a492-e2011f6d04f2"),
		    choice("IMPORTED_INTERNATIONAL", "Importado internacional", "a67a5632-a84d-5f40-9bd8-524a73ab3861"),
		    choice("INDUCED", "Inducido", "7d88d617-fb6b-5b06-8615-c2ae2abecf05"),
		    choice("INTRODUCED", "Introducido", "26489304-ab5d-5054-b264-90237755c7dd"),
		    choice("RELAPSE", "Recaída", "8d72526b-d887-5726-92ad-64c5786ecc53"),
		    choice("RECRUDESCENCE", "Recrudescencia", "5f25f0b3-e4fa-5ab4-8f68-617f814f3e35")));
		ClinicalCatalog.Disease dengue = disease("bfed631e-99de-5e50-a3a6-cbfec73437a1", "DENGUE");
		dengue.severities.addAll(
		    Arrays.asList(choice("NO_WARNING", "Dengue sin señales de alarma", "3631f126-c62d-58de-bada-9c1977faa791"),
		        choice("WARNING", "Dengue con señales de alarma", "1715a15d-35fa-5649-85b8-31aa6e09aef9"),
		        choice("SEVERE", "Dengue grave", "963a03db-373a-59b5-8fe0-9dae054eab96")));
		dengue.diagnoses.addAll(Arrays.asList(diagnosis("NO_WARNING", null, "db38b18a-6bce-423b-a776-61623584952c", "A970"),
		    diagnosis("WARNING", null, "cde66bef-328b-436e-9fc8-d99381be6902", "A971"),
		    diagnosis("SEVERE", null, "bb01974a-a2e2-42de-af95-0201882eacb5", "A972")));
		dengue.laboratoryTests.add(lab("09b60459-3661-50e5-b762-18543b9e5ed1", "ad17b417-d6b2-5769-b553-a8bd8d78bd11"));
		dengue.laboratoryTests.add(lab("48b98d15-b407-5ef3-8b5d-1f51a7bf649b", "693ab609-656f-55af-9584-7a229234f232"));
		ClinicalCatalog.Disease malaria = disease("ae35724f-6a37-5777-8fc9-799f5733e899", "MALARIA");
		malaria.severities
		        .addAll(Arrays.asList(choice("NO_WARNING", "Malaria no complicada", "23def129-d8dd-5e1c-a9dd-d5164265e138"),
		            choice("SEVERE", "Malaria grave", "203f0a2a-59f4-58bf-8d2b-df08d5fd20be")));
		malaria.species.addAll(Arrays.asList(choice("VIVAX", "Plasmodium vivax", "80795ec4-2018-52da-a523-7ca845101d19"),
		    choice("FALCIPARUM", "Plasmodium falciparum", "215b5bf8-5b15-50db-9718-19b2a7acc349"),
		    choice("MALARIAE", "Plasmodium malariae", "3aae116c-cf7c-5a4f-a582-3575581a22b4"),
		    choice("OVALE", "Plasmodium ovale", "432ff041-da5e-51c5-945f-89a044f01c5d"),
		    choice("MIXED", "Infección mixta", "60fe519d-10aa-5986-b8d8-a4414a352838")));
		malaria.diagnoses
		        .addAll(Arrays.asList(diagnosis("NO_WARNING", "VIVAX", "5c539385-80ed-4380-a675-ffe7e7627d19", "B519"),
		            diagnosis("NO_WARNING", "FALCIPARUM", "8829e3f4-3fb7-4ae1-b6b1-49f8ee524773", "B509"),
		            diagnosis("NO_WARNING", "MALARIAE", "15b93315-0d80-4401-9f2b-f1436bbe83fe", "B529"),
		            diagnosis("NO_WARNING", "OVALE", "f2c3b330-c49f-454e-9fe3-a555b650f6a9", "B530"),
		            diagnosis("NO_WARNING", "MIXED", "c5a3f652-a1b6-4ad0-bde4-232088cbeed3", "B538"),
		            diagnosis("SEVERE", "FALCIPARUM", "094d1472-74d0-4f3b-9ba2-3958cf0edea8", "B500"),
		            diagnosis("SEVERE", null, "650b80ed-02eb-4e31-aaa0-7f8a77efbc59", "B508")));
		malaria.laboratoryTests.add(lab("15a10c7c-e913-5e02-93b5-5800bc1b2720", "7717247f-528b-5719-a6db-6cc137314b77"));
		malaria.laboratoryTests.add(lab("d30793ed-ff6a-5a7c-9485-19aeff719bb6", "f7dd7a2c-6980-5b8a-819c-e1214da477c5"));
		m.diseases.add(dengue);
		m.diseases.add(malaria);
		return m;
	}
	
	private static ClinicalCatalog.Choice choice(String key, String label, String uuid) {
		ClinicalCatalog.Choice choice = new ClinicalCatalog.Choice();
		choice.key = key;
		choice.label = label;
		choice.conceptUuid = uuid;
		return choice;
	}
	
	private static ClinicalCatalog.Disease disease(String uuid, String family) {
		ClinicalCatalog.Disease disease = new ClinicalCatalog.Disease();
		disease.eventUuid = uuid;
		disease.family = family;
		return disease;
	}
	
	private static ClinicalCatalog.DiagnosisMapping diagnosis(String severity, String species, String uuid, String code) {
		ClinicalCatalog.DiagnosisMapping mapping = new ClinicalCatalog.DiagnosisMapping();
		mapping.severity = severity;
		mapping.species = species;
		mapping.diagnosisConceptUuid = uuid;
		mapping.icd10Code = code;
		return mapping;
	}
	
	private static ClinicalCatalog.LabTest lab(String order, String result) {
		ClinicalCatalog.LabTest test = new ClinicalCatalog.LabTest();
		test.orderConceptUuid = order;
		test.resultConceptUuid = result;
		test.positiveAnswerUuids.add("45c849f7-4c90-5209-a2ce-567eecb8c1ee");
		test.negativeAnswerUuids.add("3333846e-5672-5d3a-bf4e-e159694a918f");
		return test;
	}
}
