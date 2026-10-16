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
	
	public String notificationDate;
	
	public String deathDate;
	
	public String surveillanceType;
}
