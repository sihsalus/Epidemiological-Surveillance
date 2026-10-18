package org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model;

import java.util.*;

/** Only aggregates are exposed, never individual case or patient identifiers. */
public class GeographicReport {
	
	public String from, to;
	
	public DemographicReport table;

	public int referenceYear, total, unmappedCases;
	
	public int[] riskThresholds;
	
	public List<Focus> foci = new ArrayList<Focus>();
	
	public static class Focus {
		
		public int centerId, total;
		
		public String label, classification, risk;
		
		public Double latitude, longitude;
		
		public Integer lastAutochthonousYear;
		
		public Map<String, Integer> origins = new LinkedHashMap<String, Integer>();
		
		public Focus(int id) {
			centerId = id;
		}
	}
}
