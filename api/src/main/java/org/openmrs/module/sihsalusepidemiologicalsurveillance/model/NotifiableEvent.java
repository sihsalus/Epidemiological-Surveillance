package org.openmrs.module.sihsalusepidemiologicalsurveillance.model;

import java.util.Date;

import org.openmrs.BaseOpenmrsObject;
import org.openmrs.Concept;
import org.openmrs.User;

/** Persisted surveillance entity. Clinical data remains in native OpenMRS entities. */
public class NotifiableEvent extends BaseOpenmrsObject {
	
	private Integer id;
	
	private Concept concept;
	
	private String periodicity;
	
	private String referenceRegulation;
	
	private Date validFrom;
	
	private Date validTo;
	
	private boolean active = true;
	
	public boolean isActive() {
		return active;
	}
	
	public void setActive(boolean value) {
		active = value;
	}
	
	private User creator;
	
	private Date dateCreated;
	
	private User changedBy;
	
	private Date dateChanged;
	
	@Override
	public Integer getId() {
		return id;
	}
	
	@Override
	public void setId(Integer id) {
		this.id = id;
	}
	
	public Concept getConcept() {
		return concept;
	}
	
	public void setConcept(Concept concept) {
		this.concept = concept;
	}
	
	public String getPeriodicity() {
		return periodicity;
	}
	
	public void setPeriodicity(String periodicity) {
		this.periodicity = periodicity;
	}
	
	public String getReferenceRegulation() {
		return referenceRegulation;
	}
	
	public void setReferenceRegulation(String value) {
		referenceRegulation = value;
	}
	
	public Date getValidFrom() {
		return validFrom;
	}
	
	public void setValidFrom(Date value) {
		validFrom = value;
	}
	
	public Date getValidTo() {
		return validTo;
	}
	
	public void setValidTo(Date value) {
		validTo = value;
	}
	
	public boolean isActiveOn(Date date) {
		return active;
	}
	
	/** Compatibility projection of the persisted state. */
	public boolean isRetired() {
		return !active;
	}
	
	/** Notification deadline is derived from periodicity, not a database column. */
	public Integer getDeadlineDays() {
		return "INMEDIATA".equals(periodicity) ? 0 : "DIARIA".equals(periodicity) ? 1 : 7;
	}
	
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
}
