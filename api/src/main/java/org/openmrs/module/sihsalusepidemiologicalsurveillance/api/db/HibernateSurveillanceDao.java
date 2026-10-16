package org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.openmrs.BaseOpenmrsObject;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

public class HibernateSurveillanceDao implements SurveillanceDao {

	@Override
	public String riskThresholds() {
		Object value = sessionFactory.getCurrentSession()
		        .createSQLQuery("select lower_bounds from surveillance_risk_config where id = 1").uniqueResult();
		return value == null ? null : value.toString();
	}

	@Override
	public void saveRiskThresholds(String bounds) {
		sessionFactory.getCurrentSession()
		        .createSQLQuery("update surveillance_risk_config set lower_bounds = :bounds where id = 1")
		        .setString("bounds", bounds).executeUpdate();
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<Object[]> populatedCenterCodes() {
		return sessionFactory
		        .getCurrentSession()
		        .createSQLQuery(
		            "select e.address_hierarchy_entry_id, e.user_generated_id from address_hierarchy_entry e join address_hierarchy_level l on l.address_hierarchy_level_id = e.level_id where l.address_field = 'CITY_VILLAGE'")
		        .list();
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<Object[]> centerCoordinates() {
		List<Object[]> result = new java.util.ArrayList<Object[]>(sessionFactory
		        .getCurrentSession()
		        .createSQLQuery(
		            "select address_hierarchy_entry_id, latitude, longitude from surveillance_center_coordinates").list());
		// Resolve codes at read time, including centers added to the territorial catalog after an import.
		result.addAll(sessionFactory
		        .getCurrentSession()
		        .createSQLQuery(
		            "select c.id, s.latitude, s.longitude from surveillance_coordinate_codes s join "
		                    + "(select e.user_generated_id as code, min(e.address_hierarchy_entry_id) as id from address_hierarchy_entry e "
		                    + "join address_hierarchy_level l on l.address_hierarchy_level_id = e.level_id where l.address_field = 'CITY_VILLAGE' "
		                    + "group by e.user_generated_id having count(*) = 1) c on c.code = s.center_code").list());
		return result;
	}

	@Override
	public void saveCoordinateCode(String code, double latitude, double longitude) {
		sessionFactory.getCurrentSession().createSQLQuery("select id from surveillance_risk_config where id = 1 for update")
		        .uniqueResult();
		Object existing = sessionFactory.getCurrentSession()
		        .createSQLQuery("select center_code from surveillance_coordinate_codes where center_code = :code")
		        .setString("code", code).uniqueResult();
		String sql = existing == null ? "insert into surveillance_coordinate_codes (center_code, latitude, longitude) values (:code, :lat, :lon)"
		        : "update surveillance_coordinate_codes set latitude = :lat, longitude = :lon where center_code = :code";
		sessionFactory.getCurrentSession().createSQLQuery(sql).setString("code", code).setDouble("lat", latitude)
		        .setDouble("lon", longitude).executeUpdate();
	}

	@Override
	public void saveCoordinates(Integer id, double latitude, double longitude) {
		// Serialize imports using the singleton configuration row; the service transaction covers the entire file.
		sessionFactory.getCurrentSession().createSQLQuery("select id from surveillance_risk_config where id = 1 for update")
		        .uniqueResult();
		Object existing = sessionFactory
		        .getCurrentSession()
		        .createSQLQuery(
		            "select address_hierarchy_entry_id from surveillance_center_coordinates where address_hierarchy_entry_id = :id")
		        .setInteger("id", id).uniqueResult();
		if (existing != null)
			sessionFactory
			        .getCurrentSession()
			        .createSQLQuery(
			            "update surveillance_center_coordinates set latitude = :lat, longitude = :lon where address_hierarchy_entry_id = :id")
			        .setDouble("lat", latitude).setDouble("lon", longitude).setInteger("id", id).executeUpdate();
		else
			sessionFactory
			        .getCurrentSession()
			        .createSQLQuery(
			            "insert into surveillance_center_coordinates (address_hierarchy_entry_id, latitude, longitude) values (:id, :lat, :lon)")
			        .setDouble("lat", latitude).setDouble("lon", longitude).setInteger("id", id).executeUpdate();
	}
	
	private DbSessionFactory sessionFactory;
	
	public void setSessionFactory(DbSessionFactory value) {
		sessionFactory = value;
	}
	
	@Override
	public <T extends BaseOpenmrsObject> T save(T entity) {
		if (entity.getUuid() == null)
			entity.setUuid(UUID.randomUUID().toString());
		sessionFactory.getCurrentSession().saveOrUpdate(entity);
		return entity;
	}
	
	@Override
	public <T extends BaseOpenmrsObject> T byUuid(Class<T> type, String uuid) {
		return type.cast(sessionFactory.getCurrentSession().createCriteria(type).add(Restrictions.eq("uuid", uuid))
		        .uniqueResult());
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<NotifiableEvent> events() {
		return sessionFactory.getCurrentSession().createCriteria(NotifiableEvent.class).list();
	}
	
	@Override
	public NotifiableEvent eventByConcept(org.openmrs.Concept concept) {
		return (NotifiableEvent) sessionFactory.getCurrentSession().createCriteria(NotifiableEvent.class)
		        .add(Restrictions.eq("concept", concept)).uniqueResult();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<OutbreakAlertRule> rules(NotifiableEvent event) {
		return sessionFactory.getCurrentSession().createCriteria(OutbreakAlertRule.class)
		        .add(Restrictions.eq("event", event)).add(Restrictions.eq("active", true)).list();
	}
	
	@Override
	public EpidemiologicalFocus focus(NotifiableEvent event, String locationUuid) {
		// Foci are keyed by Address Hierarchy entries, not service locations. RF-21 is out of scope.
		return null;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<PeriodCaseCount> counts(NotifiableEvent event, String period, int fromYear, int toYear) {
		return counts(event, period, fromYear, toYear, null, null);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<PeriodCaseCount> counts(NotifiableEvent event, String period, int fromYear, int toYear, String zoneLevel,
	        Integer addressHierarchyEntryId) {
		org.hibernate.Criteria criteria = sessionFactory.getCurrentSession().createCriteria(PeriodCaseCount.class)
		        .add(Restrictions.eq("event", event))
		        .add(Restrictions.eq("periodType", period.toUpperCase(java.util.Locale.ENGLISH)))
		        .add(Restrictions.between("year", fromYear, toYear));
		if (zoneLevel != null)
			criteria.add(Restrictions.eq("zoneLevel", zoneLevel));
		if (addressHierarchyEntryId != null)
			criteria.add(Restrictions.eq("addressHierarchyEntryId", addressHierarchyEntryId));
		return criteria.addOrder(Order.asc("year")).addOrder(Order.asc("periodNumber")).list();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public void replaceCounts(NotifiableEvent event, List<PeriodCaseCount> counts) {
		// Serialize refreshes per event. Natural-key updates preserve idempotence.
		lockEvent(event);
		List<PeriodCaseCount> obsolete = new java.util.ArrayList<PeriodCaseCount>(sessionFactory.getCurrentSession()
		        .createCriteria(PeriodCaseCount.class).add(Restrictions.eq("event", event)).list());
		for (PeriodCaseCount count : counts) {
			PeriodCaseCount existing = (PeriodCaseCount) sessionFactory.getCurrentSession()
			        .createCriteria(PeriodCaseCount.class).add(Restrictions.eq("event", event))
			        .add(Restrictions.eq("addressHierarchyEntryId", count.getAddressHierarchyEntryId()))
			        .add(Restrictions.eq("periodType", count.getPeriodType())).add(Restrictions.eq("year", count.getYear()))
			        .add(Restrictions.eq("periodNumber", count.getPeriodNumber()))
			        .add(Restrictions.eq("diagnosisType", count.getDiagnosisType())).uniqueResult();
			if (existing == null)
				save(count);
			else {
				obsolete.remove(existing);
				existing.setZoneLevel(count.getZoneLevel());
				existing.setCaseCount(count.getCaseCount());
				existing.setStartDate(count.getStartDate());
				existing.setEndDate(count.getEndDate());
				existing.setCalculationDate(count.getCalculationDate());
				existing.setChangedBy(count.getCreator());
				existing.setDateChanged(count.getCalculationDate());
				save(existing);
			}
		}
		// The input is the complete snapshot for this event, not an incremental delta.
		// Remove buckets left behind when a case changes day, place, or classification.
		for (PeriodCaseCount old : obsolete)
			sessionFactory.getCurrentSession().delete(old);
	}
	
	@Override
	public void lockEvent(NotifiableEvent event) {
		sessionFactory
		        .getCurrentSession()
		        .createSQLQuery(
		            "select notifiable_event_id from notifiable_event where notifiable_event_id = :id for update")
		        .setInteger("id", event.getId()).uniqueResult();
		sessionFactory.getCurrentSession().refresh(event);
	}
	
	@Override
	public void lockPatient(Patient patient) {
		sessionFactory.getCurrentSession()
		        .createSQLQuery("select patient_id from patient where patient_id = :id for update")
		        .setInteger("id", patient.getId()).uniqueResult();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<Encounter> encounters(Date from, Date to, String onset) {
		return sessionFactory
		        .getCurrentSession()
		        .createQuery(
		            "select distinct e from Diagnosis d join d.encounter e join e.obs o where d.voided = false and e.voided = false "
		                    + "and e.patient.voided = false and o.voided = false "
		                    + "and o.concept.uuid = :onset and o.valueDatetime >= :from and o.valueDatetime < :to")
		        .setString("onset", onset).setTimestamp("from", from).setTimestamp("to", to).list();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<Encounter> possibleDuplicates(Patient patient, List<String> diagnoses, Date from, Date to, String onset) {
		return sessionFactory
		        .getCurrentSession()
		        .createQuery(
		            "select distinct d.encounter from Diagnosis d, Obs o "
		                    + "where d.encounter = o.encounter and d.voided = false and d.encounter.voided = false "
		                    + "and d.encounter.patient = :patient "
		                    + "and d.diagnosis.coded.uuid in (:diagnoses) and o.voided = false and o.concept.uuid = :onset "
		                    + "and o.valueDatetime >= :from and o.valueDatetime < :to").setParameter("patient", patient)
		        .setParameterList("diagnoses", diagnoses).setString("onset", onset).setTimestamp("from", from)
		        .setTimestamp("to", to).list();
	}
	
	@Override
	public Integer populatedCenterId(String uuid) {
		Object value = sessionFactory
		        .getCurrentSession()
		        .createSQLQuery(
		            "select e.address_hierarchy_entry_id from address_hierarchy_entry e join address_hierarchy_level l on l.address_hierarchy_level_id = e.level_id where e.uuid = :uuid and l.address_field = 'CITY_VILLAGE'")
		        .setString("uuid", uuid).uniqueResult();
		return value == null ? null : ((Number) value).intValue();
	}
	
	@Override
	public Integer districtIdForPopulatedCenter(Integer populatedCenterId) {
		Object value = sessionFactory
		        .getCurrentSession()
		        .createSQLQuery(
		            "select p.address_hierarchy_entry_id from address_hierarchy_entry e join address_hierarchy_entry p on p.address_hierarchy_entry_id = e.parent_id join address_hierarchy_level l on l.address_hierarchy_level_id = p.level_id where e.address_hierarchy_entry_id = :id and l.address_field = 'COUNTY_DISTRICT'")
		        .setInteger("id", populatedCenterId).uniqueResult();
		return value == null ? null : ((Number) value).intValue();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<SurveillanceCase> surveillanceCases() {
		return sessionFactory.getCurrentSession().createCriteria(SurveillanceCase.class)
		        .add(Restrictions.eq("voided", false)).list();
	}
	
	@Override
	public SurveillanceCase caseByDiagnosis(org.openmrs.Diagnosis diagnosis) {
		return (SurveillanceCase) sessionFactory.getCurrentSession().createCriteria(SurveillanceCase.class)
		        .add(Restrictions.eq("diagnosis", diagnosis)).add(Restrictions.eq("voided", false)).uniqueResult();
	}
	
	@Override
	public String addressUuid(Integer id) {
		Object value = sessionFactory.getCurrentSession()
		        .createSQLQuery("select uuid from address_hierarchy_entry where address_hierarchy_entry_id = :id")
		        .setInteger("id", id).uniqueResult();
		return value == null ? null : value.toString();
	}
	
	@Override
	public String addressDisplay(Integer id) {
		Object[] row = (Object[]) sessionFactory
		        .getCurrentSession()
		        .createSQLQuery(
		            "select dep.name as department_name, p.name as province_name, d.name as district_name, c.name as populated_center_name from address_hierarchy_entry c left join address_hierarchy_entry d on d.address_hierarchy_entry_id = c.parent_id left join address_hierarchy_entry p on p.address_hierarchy_entry_id = d.parent_id left join address_hierarchy_entry dep on dep.address_hierarchy_entry_id = p.parent_id where c.address_hierarchy_entry_id = :id")
		        .setInteger("id", id).uniqueResult();
		if (row == null)
			return null;
		return (row[0] == null ? "—" : row[0]) + " → " + (row[1] == null ? "—" : row[1]) + " → "
		        + (row[2] == null ? "—" : row[2]) + " → " + (row[3] == null ? "—" : row[3]);
	}

	@Override
	public Integer ancestorIdForPopulatedCenter(Integer populatedCenterId, String field) {
		Integer current = populatedCenterId;
		java.util.Set<Integer> visited = new java.util.HashSet<Integer>();
		while (current != null && visited.add(current)) {
			Object[] row = (Object[]) sessionFactory
			        .getCurrentSession()
			        .createSQLQuery(
			            "select e.parent_id, l.address_field from address_hierarchy_entry e join address_hierarchy_level l on l.address_hierarchy_level_id = e.level_id where e.address_hierarchy_entry_id = :id")
			        .setInteger("id", current).uniqueResult();
			if (row == null)
				return null;
			if (field.equals(row[1]))
				return current;
			current = row[0] == null ? null : ((Number) row[0]).intValue();
		}
		return null;
	}
	
	@Override
	public Integer addressId(String uuid) {
		Object value = sessionFactory.getCurrentSession()
		        .createSQLQuery("select address_hierarchy_entry_id from address_hierarchy_entry where uuid = :uuid")
		        .setString("uuid", uuid).uniqueResult();
		return value == null ? null : ((Number) value).intValue();
	}
	
	@Override
	public Integer addressId(String uuid, String field) {
		Object value = sessionFactory
		        .getCurrentSession()
		        .createSQLQuery(
		            "select e.address_hierarchy_entry_id from address_hierarchy_entry e join address_hierarchy_level l on l.address_hierarchy_level_id = e.level_id where e.uuid = :uuid and l.address_field = :field")
		        .setString("uuid", uuid).setString("field", field).uniqueResult();
		return value == null ? null : ((Number) value).intValue();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<Object[]> addressChildren(String field, String parentUuid) {
		String sql = "select e.uuid, e.name from address_hierarchy_entry e join address_hierarchy_level l on l.address_hierarchy_level_id = e.level_id "
		        + "left join address_hierarchy_entry p on p.address_hierarchy_entry_id = e.parent_id where l.address_field = :field "
		        + (parentUuid == null ? "" : "and p.uuid = :parent ") + "order by e.name";
		org.hibernate.Query query = sessionFactory.getCurrentSession().createSQLQuery(sql).setString("field", field);
		if (parentUuid != null)
			query.setString("parent", parentUuid);
		return query.list();
	}
}
