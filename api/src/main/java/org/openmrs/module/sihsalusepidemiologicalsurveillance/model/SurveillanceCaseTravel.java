package org.openmrs.module.sihsalusepidemiologicalsurveillance.model;

import java.util.Date;

import org.openmrs.BaseOpenmrsObject;
import org.openmrs.User;

public class SurveillanceCaseTravel extends BaseOpenmrsObject {

	private Integer id;

	public Integer getId() {
		return id;
	}

	public void setId(Integer value) {
		id = value;
	}

	private SurveillanceCase surveillanceCase;

	public SurveillanceCase getSurveillanceCase() {
		return surveillanceCase;
	}

	public void setSurveillanceCase(SurveillanceCase value) {
		surveillanceCase = value;
	}

	private Integer travelAddress;

	public Integer getTravelAddress() {
		return travelAddress;
	}

	public void setTravelAddress(Integer value) {
		travelAddress = value;
	}

	private Date startDate;

	public Date getStartDate() {
		return startDate;
	}

	public void setStartDate(Date value) {
		startDate = value;
	}

	private Date endDate;

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(Date value) {
		endDate = value;
	}

	private User creator;

	public User getCreator() {
		return creator;
	}

	public void setCreator(User value) {
		creator = value;
	}

	private Date dateCreated;

	public Date getDateCreated() {
		return dateCreated;
	}

	public void setDateCreated(Date value) {
		dateCreated = value;
	}

	private User changedBy;

	public User getChangedBy() {
		return changedBy;
	}

	public void setChangedBy(User value) {
		changedBy = value;
	}

	private Date dateChanged;

	public Date getDateChanged() {
		return dateChanged;
	}

	public void setDateChanged(Date value) {
		dateChanged = value;
	}
}
