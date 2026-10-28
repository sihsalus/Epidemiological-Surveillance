package org.openmrs.module.sihsalusepidemiologicalsurveillance.model;

import java.util.Date;

import org.openmrs.BaseOpenmrsObject;
import org.openmrs.User;

/** One shared configuration; consecutive lower bounds cover every nonnegative age. */
public class AgeGroupConfiguration extends BaseOpenmrsObject {
	
	public static final String UUID = "b5ee012c-6c37-4f1c-a949-61af2b607688";
	
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
