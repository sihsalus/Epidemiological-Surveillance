package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.util.List;
import java.util.Map;

import org.openmrs.annotation.Authorized;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.SurveillanceConstants;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface SurveillanceService extends OpenmrsService {
	
	@Authorized(SurveillanceConstants.ACCESS)
	@Transactional(readOnly = true)
	int[] getNotificationSettings();
	
	@Authorized(SurveillanceConstants.MANAGE)
	int[] saveNotificationSettings(List<Integer> values);
	
	@Authorized(SurveillanceConstants.REPORT)
	@Transactional(readOnly = true)
	Map<String, Object> indicators(String event, String from, String to, String zoneLevel, String address,
	        String diagnosisType, String indicator);
	
	@Authorized(SurveillanceConstants.ACCESS)
	@Transactional(readOnly = true)
	int[] getEndemicSettings();
	
	@Authorized(SurveillanceConstants.MANAGE)
	int[] saveEndemicSettings(List<Integer> values);
	
	@Authorized(SurveillanceConstants.ACCESS)
	@Transactional(readOnly = true)
	int[] getRiskThresholds();
	
	@Authorized(SurveillanceConstants.MANAGE)
	int[] saveRiskThresholds(List<Integer> bounds);
	
	@Authorized(SurveillanceConstants.MANAGE)
	Map<String, Integer> importCoordinates(String csv);
	
	@Authorized(SurveillanceConstants.REPORT)
	@Transactional(readOnly = true)
	GeographicReport geography(String event, String from, String to, String zoneLevel, String address);
	
	GeographicReport geography(String event, String from, String to, String zoneLevel, String address, String diagnosisType);
	
	@Authorized(SurveillanceConstants.REPORT)
	@Transactional(readOnly = true)
	GeographicReport geography(String event, String from, String to, String zoneLevel, String address, String diagnosisType,
	        String column, String period);
	
	@Authorized(SurveillanceConstants.ACCESS)
	@Transactional(readOnly = true)
	List<AgeGroup> getAgeGroups();
	
	@Authorized(SurveillanceConstants.MANAGE)
	List<AgeGroup> saveAgeGroups(List<AgeGroup> groups);
	
	@Authorized(SurveillanceConstants.REPORT)
	@Transactional(readOnly = true)
	DemographicReport demographics(String from, String to, String row, String column, String period, String event,
	        String compareEvents, String diagnosisType);
	
	@Authorized(SurveillanceConstants.REPORT)
	@Transactional(readOnly = true)
	DemographicReport demographics(String from, String to, String row, String column, String period, String event,
	        String compareEvents, String diagnosisType, String zoneLevel, String address);
	
	@Authorized(SurveillanceConstants.ACCESS)
	@Transactional(readOnly = true)
	Map<String, Object> getCatalog();
	
	@Authorized(SurveillanceConstants.ACCESS)
	@Transactional(readOnly = true)
	Map<String, Object> healthcheck();
	
	@Authorized(SurveillanceConstants.ACCESS)
	@Transactional(readOnly = true)
	List<Map<String, Object>> getEvents(boolean includeRetired);
	
	@Authorized(SurveillanceConstants.ACCESS)
	@Transactional(readOnly = true)
	Map<String, Object> getEvent(String uuid);
	
	@Authorized(SurveillanceConstants.MANAGE)
	Map<String, Object> updateEvent(String uuid, Map<String, Object> body);
	
	@Authorized(SurveillanceConstants.REGISTER)
	@Transactional(isolation = Isolation.READ_COMMITTED)
	CaseResult registerCase(CaseRequest request);
	
	SurveillanceCaseResponse createDraft(SurveillanceCaseRequest request);
	
	@Authorized(value = { SurveillanceConstants.VIEW, SurveillanceConstants.REPORT }, requireAll = true)
	@Transactional(readOnly = true)
	Map<String, Object> weeklyRecords(int year, int week);
	
	List<SurveillanceCaseResponse> listCases(String patientUuid, String diagnosisConceptUuid, String onsetFrom,
	        String onsetTo, String notificationFrom, String notificationTo, Boolean fatal);
	
	SurveillanceCaseResponse updateDraft(String uuid, SurveillanceCaseRequest request);
	
	SurveillanceCaseResponse getDraft(String uuid);
	
	SurveillanceCaseResponse closeDraft(String uuid);
	
	List<Map<String, Object>> addressChildren(String level, String parentUuid);
	
	@Authorized(SurveillanceConstants.VIEW)
	CaseResult getCase(String uuid);
	
	@Authorized(SurveillanceConstants.REPORT)
	SurveillanceReport report(String eventUuid, String from, String to, String period);
	
	@Authorized(SurveillanceConstants.REPORT)
	SurveillanceReport report(String eventUuid, String from, String to, String period, String zoneLevel, String address,
	        String diagnosisType);
	
	@Authorized(SurveillanceConstants.MANAGE)
	void refreshCounts();
	
	@Authorized(SurveillanceConstants.MANAGE)
	Map<String, Object> saveEvent(Map<String, Object> body);
	
}
