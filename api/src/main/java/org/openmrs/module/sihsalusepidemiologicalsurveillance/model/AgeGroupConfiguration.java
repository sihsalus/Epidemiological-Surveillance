package org.openmrs.module.sihsalusepidemiologicalsurveillance.model;

import org.openmrs.BaseOpenmrsObject;

/** One shared configuration; consecutive lower bounds cover every nonnegative age. */
public class AgeGroupConfiguration extends BaseOpenmrsObject {
	
	public static final String UUID = "b5ee012c-6c37-4f1c-a949-61af2b607688";
	
	private Integer id;
	
	private String lowerBounds;
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer value) {
		id = value;
	}
	
	public String getLowerBounds() {
		return lowerBounds;
	}
	
	public void setLowerBounds(String value) {
		lowerBounds = value;
	}
}
