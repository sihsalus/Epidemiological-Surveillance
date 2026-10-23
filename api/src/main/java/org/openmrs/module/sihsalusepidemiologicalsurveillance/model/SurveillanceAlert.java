package org.openmrs.module.sihsalusepidemiologicalsurveillance.model;

public class SurveillanceAlert extends org.openmrs.BaseOpenmrsObject {
	
	private Integer id;
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer value) {
		id = value;
	}
	
	private String type;
	
	public String getType() {
		return type;
	}
	
	public void setType(String value) {
		type = value;
	}
	
	private String reason;
	
	public String getReason() {
		return reason;
	}
	
	public void setReason(String value) {
		reason = value;
	}
	
	private String caseUuid;
	
	public String getCaseUuid() {
		return caseUuid;
	}
	
	public void setCaseUuid(String value) {
		caseUuid = value;
	}
	
	private String eventUuid;
	
	public String getEventUuid() {
		return eventUuid;
	}
	
	public void setEventUuid(String value) {
		eventUuid = value;
	}
	
	private String eventName;
	
	public String getEventName() {
		return eventName;
	}
	
	public void setEventName(String value) {
		eventName = value;
	}
	
	private String center;
	
	public String getCenter() {
		return center;
	}
	
	public void setCenter(String value) {
		center = value;
	}
	
	private String additionalInformation;
	
	public String getAdditionalInformation() {
		return additionalInformation;
	}
	
	public void setAdditionalInformation(String value) {
		additionalInformation = value;
	}
	
	private String occurrenceKey;
	
	public String getOccurrenceKey() {
		return occurrenceKey;
	}
	
	public void setOccurrenceKey(String value) {
		occurrenceKey = value;
	}
	
	private String status;
	
	public String getStatus() {
		return status;
	}
	
	public void setStatus(String value) {
		status = value;
	}
	
	private String attendedBy;
	
	public String getAttendedBy() {
		return attendedBy;
	}
	
	public void setAttendedBy(String value) {
		attendedBy = value;
	}
	
	private java.util.Date generatedAt;
	
	public java.util.Date getGeneratedAt() {
		return generatedAt;
	}
	
	public void setGeneratedAt(java.util.Date value) {
		generatedAt = value;
	}
	
	private java.util.Date attendedAt;
	
	public java.util.Date getAttendedAt() {
		return attendedAt;
	}
	
	public void setAttendedAt(java.util.Date value) {
		attendedAt = value;
	}
}
