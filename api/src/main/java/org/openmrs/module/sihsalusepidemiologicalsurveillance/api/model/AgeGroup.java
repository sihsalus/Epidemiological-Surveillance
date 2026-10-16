package org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model;

public class AgeGroup {
	
	public String key;
	
	public Integer minAge;
	
	public Integer maxAge;
	
	public AgeGroup() {
	}
	
	public AgeGroup(String key, int minAge, Integer maxAge) {
		this.key = key;
		this.minAge = minAge;
		this.maxAge = maxAge;
	}
}
