package org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db;

import java.util.Date;
import java.util.List;

import org.openmrs.BaseOpenmrsObject;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

public interface SurveillanceDao {
	
	org.openmrs.User alertAttendant(String reference);
	
	List<SurveillanceAlert> alerts();
	
	SurveillanceAlert saveAlert(SurveillanceAlert alert);
	
	void lockAlerts();
	
	void lockEventConcept(org.openmrs.Concept concept);
	
	int[] notificationSettings();
	
	void saveNotificationSettings(int day, int hour, int minute);
	
	int[] endemicSettings();
	
	void saveEndemicSettings(int years, int minimum);
	
	String riskThresholds();
	
	void saveRiskThresholds(String bounds);
	
	List<Object[]> populatedCenterCodes();
	
	List<Object[]> centerCoordinates();
	
	void saveCoordinates(Integer id, double latitude, double longitude);
	
	void saveCoordinateCode(String code, double latitude, double longitude);
	
	<T extends BaseOpenmrsObject> T save(T entity);
	
	<T extends BaseOpenmrsObject> T byUuid(Class<T> type, String uuid);
	
	List<NotifiableEvent> events();
	
	NotifiableEvent eventByConcept(org.openmrs.Concept concept);
	
	List<PeriodCaseCount> counts(NotifiableEvent event, String period, int fromYear, int toYear);
	
	List<PeriodCaseCount> counts(NotifiableEvent event, String period, int fromYear, int toYear, String zoneLevel,
	        Integer addressHierarchyEntryId);
	
	void replaceCounts(NotifiableEvent event, List<PeriodCaseCount> counts);
	
	void lockEvent(NotifiableEvent event);
	
	Integer populatedCenterId(String uuid);
	
	Integer districtIdForPopulatedCenter(Integer populatedCenterId);
	
	Integer ancestorIdForPopulatedCenter(Integer populatedCenterId, String field);
	
	List<SurveillanceCase> surveillanceCases();
	
	List<SurveillanceCase> weeklyCases(Date from, Date to);
	
	List<SurveillanceCase> casesForPerson(Integer personId);
	
	SurveillanceCase caseByDiagnosis(org.openmrs.Diagnosis diagnosis);
	
	String addressUuid(Integer id);
	
	String addressDisplay(Integer id);
	
	String addressCode(Integer id);
	
	String residenceCode(String department, String province, String district, String locality);
	
	Integer addressId(String uuid);
	
	Integer addressId(String uuid, String field);
	
	List<Object[]> addressChildren(String addressField, String parentUuid);
}
