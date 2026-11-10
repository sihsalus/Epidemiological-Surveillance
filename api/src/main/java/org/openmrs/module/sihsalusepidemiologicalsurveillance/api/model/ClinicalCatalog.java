package org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fixed configuration exposed to the frontend: the epidemiological calendar and the content
 * references that the component cannot derive from OpenMRS. Notifiable events, diagnoses and
 * laboratory results are never listed here; each notifiable event is the diagnosis concept
 * configured by epidemiology.
 */
public class ClinicalCatalog {
	
	public String trueConceptUuid;
	
	public String falseConceptUuid;
	
	public String surveillanceStartDate;
	
	public String ethnicityAttributeTypeUuid;
	
	public String ethnicityCodeSource = "MINSA-ETNIAS";
	
	public Map<String, Integer> ethnicityGroups = new LinkedHashMap<String, Integer>();
	
	public String icd10SourceUuid;
	
	public int historicalYears = 5;
	
	public int minimumHistoricalYears = 3;
	
	public String timezone = "America/Lima";
	
	/** SUNDAY,4 = epidemiological weeks; must be confirmed by local epidemiology. */
	public String firstDayOfWeek = "SUNDAY";
	
	public int minimalDaysInFirstWeek = 4;
}
