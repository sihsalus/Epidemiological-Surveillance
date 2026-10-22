package org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model;

/** JSON contract for a surveillance-case draft. References are OpenMRS UUIDs. */
public class SurveillanceCaseRequest {
	
	public String patientUuid;
	
	public String encounterUuid;
	
	public String providerUuid;
	
	public String locationUuid;
	
	public String diagnosisUuid;
	
	public String testOrderUuid;
	
	/** Temporary test harness input; only Obs.order is persisted as test_order_id. */
	public String laboratoryObservationUuid;
	
	public String origin;
	
	public String onsetDate;
	
	public String infectionAddressUuid;
	
	public String diagnosisType;
	
	public String vaccinationStatus;
	
	public Boolean pregnant;
	
	public String investigationDate;
	
	/** Investigation form: true = yes, false = no; omitted updates preserve the current value. */
	public Boolean investigationForm;
	
	public String notificationDate;
	
	/** Legacy input ignored; death date belongs to Person. */
	public String deathDate;
	
	public String deathAttribution;
	
	public String habitualResidence;
	
	public String surveillanceType;
}
