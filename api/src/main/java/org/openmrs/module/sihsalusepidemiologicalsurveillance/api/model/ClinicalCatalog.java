package org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixed OpenMRS content contract exposed to the frontend. */
public class ClinicalCatalog {
	
	public int version = 1;
	
	public String trueConceptUuid;
	
	public String falseConceptUuid;
	
	public String surveillanceStartDate;
	
	public String encounterRoleUuid;
	
	public String ethnicityAttributeTypeUuid;
	
	public String ethnicityCodeSource = "MINSA-ETNIAS";
	
	public Map<String, Integer> ethnicityGroups = new LinkedHashMap<String, Integer>();
	
	public String pregnancyAttributeTypeUuid;
	
	public String icd10SourceUuid;
	
	public int duplicateWindowDays = 30;
	
	public int historicalYears = 5;
	
	public int minimumHistoricalYears = 3;
	
	public String timezone = "America/Lima";
	
	/** SUNDAY,4 = epidemiological weeks; must be confirmed by local epidemiology. */
	public String firstDayOfWeek = "SUNDAY";
	
	public int minimalDaysInFirstWeek = 4;
	
	public Map<String, String> questions = new LinkedHashMap<String, String>();
	
	public List<Choice> statuses = new ArrayList<Choice>();
	
	public List<Choice> origins = new ArrayList<Choice>();
	
	public List<Disease> diseases = new ArrayList<Disease>();
	
	public static class Choice {
		
		public String key;
		
		public String conceptUuid;
		
		public String label;
	}
	
	public static class Disease {
		
		public String eventUuid;
		
		public String family;
		
		public List<Choice> severities = new ArrayList<Choice>();
		
		public List<Choice> species = new ArrayList<Choice>();
		
		public List<DiagnosisMapping> diagnoses = new ArrayList<DiagnosisMapping>();
		
		public List<LabTest> laboratoryTests = new ArrayList<LabTest>();
	}
	
	public static class DiagnosisMapping {
		
		public String severity;
		
		public String species;
		
		public String diagnosisConceptUuid;
		
		public String icd10Code;
	}
	
	public static class LabTest {
		
		public String orderConceptUuid;
		
		public String resultConceptUuid;
		
		public List<String> positiveAnswerUuids = new ArrayList<String>();
		
		public List<String> negativeAnswerUuids = new ArrayList<String>();
	}
}
