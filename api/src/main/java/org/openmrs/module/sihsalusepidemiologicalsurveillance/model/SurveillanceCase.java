package org.openmrs.module.sihsalusepidemiologicalsurveillance.model;

import java.util.Date;

import org.openmrs.BaseOpenmrsObject;
import org.openmrs.Diagnosis;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.User;

public class SurveillanceCase extends BaseOpenmrsObject {
	
	private Integer id;
	
	private Boolean pregnant;
	
	private String patientGivenNames;
	
	private String patientPaternalSurname;
	
	private String patientMaternalSurname;
	
	private String patientSex;
	
	private Integer patientAge;
	
	private String patientResidence;
	
	public Boolean getPregnant() {
		return pregnant;
	}
	
	public void setPregnant(Boolean value) {
		pregnant = value;
	}
	
	public String getPatientGivenNames() {
		return patientGivenNames;
	}
	
	public void setPatientGivenNames(String value) {
		patientGivenNames = value;
	}
	
	public String getPatientPaternalSurname() {
		return patientPaternalSurname;
	}
	
	public void setPatientPaternalSurname(String value) {
		patientPaternalSurname = value;
	}
	
	public String getPatientMaternalSurname() {
		return patientMaternalSurname;
	}
	
	public void setPatientMaternalSurname(String value) {
		patientMaternalSurname = value;
	}
	
	public String getPatientSex() {
		return patientSex;
	}
	
	public void setPatientSex(String value) {
		patientSex = value;
	}
	
	public Integer getPatientAge() {
		return patientAge;
	}
	
	public void setPatientAge(Integer value) {
		patientAge = value;
	}
	
	public String getPatientResidence() {
		return patientResidence;
	}
	
	public void setPatientResidence(String value) {
		patientResidence = value;
	}
	
	private Patient patient;
	
	private Encounter encounter;
	
	private Provider provider;
	
	private Location location;
	
	private Diagnosis diagnosis;
	
	private Order testOrder;
	
	private org.openmrs.Obs laboratoryObservation;
	
	public org.openmrs.Obs getLaboratoryObservation() {
		return laboratoryObservation;
	}
	
	public void setLaboratoryObservation(org.openmrs.Obs value) {
		laboratoryObservation = value;
	}
	
	private String origin;
	
	private Date onsetDate;
	
	private Integer infectionAddress;
	
	private String diagnosisType;
	
	private String vaccinationStatus;
	
	private Date investigationDate;
	
	private Date notificationDate;
	
	private Date deathDate;
	
	private String surveillanceType;
	
	private IndividualRecord individualRecord;
	
	private boolean voided;
	
	private User creator;
	
	private Date dateCreated;
	
	private User changedBy;
	
	private Date dateChanged;
	
	private User voidedBy;
	
	private Date dateVoided;
	
	private String voidReason;
	
	@Override
	public Integer getId() {
		return id;
	}
	
	@Override
	public void setId(Integer value) {
		id = value;
	}
	
	public Patient getPatient() {
		return patient;
	}
	
	public void setPatient(Patient value) {
		patient = value;
	}
	
	public Encounter getEncounter() {
		return encounter;
	}
	
	public void setEncounter(Encounter value) {
		encounter = value;
	}
	
	public Provider getProvider() {
		return provider;
	}
	
	public void setProvider(Provider value) {
		provider = value;
	}
	
	public Location getLocation() {
		return location;
	}
	
	public void setLocation(Location value) {
		location = value;
	}
	
	public Diagnosis getDiagnosis() {
		return diagnosis;
	}
	
	public void setDiagnosis(Diagnosis value) {
		diagnosis = value;
	}
	
	public Order getTestOrder() {
		return testOrder;
	}
	
	public void setTestOrder(Order value) {
		testOrder = value;
	}
	
	public String getOrigin() {
		return origin;
	}
	
	public void setOrigin(String value) {
		origin = value;
	}
	
	public Date getOnsetDate() {
		return onsetDate;
	}
	
	public void setOnsetDate(Date value) {
		onsetDate = value;
	}
	
	public Integer getInfectionAddress() {
		return infectionAddress;
	}
	
	public void setInfectionAddress(Integer value) {
		infectionAddress = value;
	}
	
	public String getDiagnosisType() {
		return diagnosisType;
	}
	
	public void setDiagnosisType(String value) {
		diagnosisType = value;
	}
	
	public String getVaccinationStatus() {
		return vaccinationStatus;
	}
	
	public void setVaccinationStatus(String value) {
		vaccinationStatus = value;
	}
	
	public Date getInvestigationDate() {
		return investigationDate;
	}
	
	public void setInvestigationDate(Date value) {
		investigationDate = value;
	}
	
	public Date getNotificationDate() {
		return notificationDate;
	}
	
	public void setNotificationDate(Date value) {
		notificationDate = value;
	}
	
	public Date getDeathDate() {
		return deathDate;
	}
	
	public void setDeathDate(Date value) {
		deathDate = value;
	}
	
	public String getSurveillanceType() {
		return surveillanceType;
	}
	
	public void setSurveillanceType(String value) {
		surveillanceType = value;
	}
	
	public IndividualRecord getIndividualRecord() {
		return individualRecord;
	}
	
	public void setIndividualRecord(IndividualRecord value) {
		individualRecord = value;
	}
	
	public boolean isVoided() {
		return voided;
	}
	
	public void setVoided(boolean value) {
		voided = value;
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
	
	public User getVoidedBy() {
		return voidedBy;
	}
	
	public void setVoidedBy(User value) {
		voidedBy = value;
	}
	
	public Date getDateVoided() {
		return dateVoided;
	}
	
	public void setDateVoided(Date value) {
		dateVoided = value;
	}
	
	public String getVoidReason() {
		return voidReason;
	}
	
	public void setVoidReason(String value) {
		voidReason = value;
	}
}
