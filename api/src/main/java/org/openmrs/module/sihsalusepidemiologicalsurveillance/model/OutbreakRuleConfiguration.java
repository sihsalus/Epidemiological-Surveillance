package org.openmrs.module.sihsalusepidemiologicalsurveillance.model;

import org.openmrs.BaseOpenmrsObject;

public class OutbreakRuleConfiguration extends BaseOpenmrsObject {

	public static final String UUID = "3d340cac-7857-41cf-8a18-0a38a413d180";

	private Integer id;

	private String rules;

	public Integer getId() {
		return id;
	}

	public void setId(Integer value) {
		id = value;
	}

	public String getRules() {
		return rules;
	}

	public void setRules(String value) {
		rules = value;
	}
}
