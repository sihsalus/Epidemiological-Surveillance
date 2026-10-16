package org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model;

import java.util.*;

/** Aggregate-only response: no patient/case identifiers or individual records. */
public class DemographicReport {
	
	public String zoneLevel;
	
	public String address;
	
	public String row;
	
	public String column;
	
	public String period;
	
	public String from;
	
	public String to;
	
	public Date generatedAt = new Date();
	
	public List<AxisValue> rows = new ArrayList<AxisValue>();
	
	public List<AxisValue> columns = new ArrayList<AxisValue>();
	
	public List<List<Integer>> values = new ArrayList<List<Integer>>();
	
	public List<Integer> rowTotals = new ArrayList<Integer>();
	
	public List<Integer> columnTotals = new ArrayList<Integer>();
	
	public int total;
	
	public int excludedWithoutOnset;
	
	public List<AgeGroup> ageGroups;
	
	public static class AxisValue {
		
		public String key;
		
		public String label;
		
		public AxisValue(String key, String label) {
			this.key = key;
			this.label = label;
		}
	}
}
