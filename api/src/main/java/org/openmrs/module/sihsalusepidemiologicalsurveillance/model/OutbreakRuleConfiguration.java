package org.openmrs.module.sihsalusepidemiologicalsurveillance.model;

import java.util.Date;

import org.openmrs.BaseOpenmrsObject;
import org.openmrs.User;

public class OutbreakRuleConfiguration extends BaseOpenmrsObject {

	public static final String UUID = "3d340cac-7857-41cf-8a18-0a38a413d180";

	private Integer id;

	private User creator;

	private Date dateCreated;

	private User changedBy;

	private Date dateChanged;

	public User getCreator() {
		return creator;
	}

	public void setCreator(User value) {
		creator = value;
	}

	public Date getDateCreated() {
		return dateCreated;
	}

	public void setDateCreated(Date value) {
		dateCreated = value;
	}

	public User getChangedBy() {
		return changedBy;
	}

	public void setChangedBy(User value) {
		changedBy = value;
	}

	public Date getDateChanged() {
		return dateChanged;
	}

	public void setDateChanged(Date value) {
		dateChanged = value;
	}

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
