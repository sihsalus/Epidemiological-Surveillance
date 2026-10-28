package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.Test;
import org.openmrs.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db.SurveillanceDao;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.NotifiableEvent;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.SurveillanceCase;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

/** OpenMRS core synthetic dataset, real Spring service wiring and Hibernate queries. */
public class NativePersistenceTest extends BaseModuleContextSensitiveTest {
	
	@Test
	public void outbreakRuleConfigurationAuditSurvivesReload() {
		org.openmrs.module.sihsalusepidemiologicalsurveillance.model.OutbreakRuleConfiguration config = new org.openmrs.module.sihsalusepidemiologicalsurveillance.model.OutbreakRuleConfiguration();
		config.setUuid(UUID.randomUUID().toString());
		config.setRules("[]");
		User actor = Context.getAuthenticatedUser();
		Date created = new Date(1000000L);
		Date changed = new Date(2000000L);
		config.setCreator(actor);
		config.setDateCreated(created);
		dao.save(config);
		Context.flushSession();
		Context.clearSession();
		config = dao.byUuid(org.openmrs.module.sihsalusepidemiologicalsurveillance.model.OutbreakRuleConfiguration.class,
		    config.getUuid());
		assertEquals(actor.getId(), config.getCreator().getId());
		assertEquals(created, config.getDateCreated());
		assertNull(config.getChangedBy());
		assertNull(config.getDateChanged());
		config.setChangedBy(Context.getAuthenticatedUser());
		config.setDateChanged(changed);
		dao.save(config);
		Context.flushSession();
		Context.clearSession();
		config = dao.byUuid(org.openmrs.module.sihsalusepidemiologicalsurveillance.model.OutbreakRuleConfiguration.class,
		    config.getUuid());
		assertEquals(actor.getId(), config.getCreator().getId());
		assertEquals(created, config.getDateCreated());
		assertEquals(actor.getId(), config.getChangedBy().getId());
		assertEquals(changed, config.getDateChanged());
	}

	@Test
	public void ageGroupConfigurationAuditSurvivesReload() {
		org.openmrs.module.sihsalusepidemiologicalsurveillance.model.AgeGroupConfiguration config = new org.openmrs.module.sihsalusepidemiologicalsurveillance.model.AgeGroupConfiguration();
		config.setUuid(UUID.randomUUID().toString());
		config.setLowerBounds("0,18,60");
		User actor = Context.getAuthenticatedUser();
		Date created = new Date(1000000L);
		Date changed = new Date(2000000L);
		config.setCreator(actor);
		config.setDateCreated(created);
		dao.save(config);
		Context.flushSession();
		Context.clearSession();
		config = dao.byUuid(org.openmrs.module.sihsalusepidemiologicalsurveillance.model.AgeGroupConfiguration.class,
		    config.getUuid());
		assertEquals(actor.getId(), config.getCreator().getId());
		assertEquals(created, config.getDateCreated());
		assertNull(config.getChangedBy());
		assertNull(config.getDateChanged());
		config.setChangedBy(Context.getAuthenticatedUser());
		config.setDateChanged(changed);
		dao.save(config);
		Context.flushSession();
		Context.clearSession();
		config = dao.byUuid(org.openmrs.module.sihsalusepidemiologicalsurveillance.model.AgeGroupConfiguration.class,
		    config.getUuid());
		assertEquals(actor.getId(), config.getCreator().getId());
		assertEquals(created, config.getDateCreated());
		assertEquals(actor.getId(), config.getChangedBy().getId());
		assertEquals(changed, config.getDateChanged());
	}

	@Test
	public void outbreakRuleConfigurationSurvivesReload() {
		org.openmrs.module.sihsalusepidemiologicalsurveillance.model.OutbreakRuleConfiguration config = new org.openmrs.module.sihsalusepidemiologicalsurveillance.model.OutbreakRuleConfiguration();
		config.setUuid(org.openmrs.module.sihsalusepidemiologicalsurveillance.model.OutbreakRuleConfiguration.UUID);
		java.util.List<org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.OutbreakRule> rules = org.openmrs.module.sihsalusepidemiologicalsurveillance.api.OutbreakRuleSettings
		        .defaults();
		rules.get(1).weeks = 3;
		String stored = org.openmrs.module.sihsalusepidemiologicalsurveillance.api.OutbreakRuleSettings.validate(rules);
		config.setRules(stored);
		dao.save(config);
		Context.flushSession();
		Context.clearSession();
		assertEquals(
		    stored,
		    dao.byUuid(org.openmrs.module.sihsalusepidemiologicalsurveillance.model.OutbreakRuleConfiguration.class,
		        config.getUuid()).getRules());
	}

	@Test
	public void endemicSettingsSurviveReload() {
		Context.getAdministrationService()
		        .executeSQL(
		            "create table if not exists surveillance_endemic_config (id int primary key, historical_years int, minimum_years int)",
		            false);
		Context.getAdministrationService().executeSQL("delete from surveillance_endemic_config", false);
		Context.getAdministrationService().executeSQL("insert into surveillance_endemic_config values (1,5,3)", false);
		assertArrayEquals(new int[] { 5, 3 }, dao.endemicSettings());
		dao.saveEndemicSettings(8, 4);
		Context.flushSession();
		Context.clearSession();
		assertArrayEquals(new int[] { 8, 4 }, dao.endemicSettings());
	}
	
	@Test
	public void coordinatesAndRiskSettingsSurviveReloadAndRepeatedImports() {
		Context.getAdministrationService()
		        .executeSQL(
		            "create table if not exists surveillance_coordinate_codes (center_code varchar(255) primary key, latitude double, longitude double)",
		            false);
		Context.getAdministrationService()
		        .executeSQL(
		            "create table if not exists address_hierarchy_entry (address_hierarchy_entry_id int primary key, parent_id int, name varchar(255), level_id int)",
		            false);
		Context.getAdministrationService().executeSQL(
		    "alter table address_hierarchy_entry add column if not exists user_generated_id varchar(255)", false);
		Context.getAdministrationService()
		        .executeSQL(
		            "create table if not exists address_hierarchy_level (address_hierarchy_level_id int primary key, address_field varchar(255))",
		            false);
		Context.getAdministrationService().executeSQL(
		    "create table if not exists surveillance_risk_config (id int primary key, lower_bounds varchar(100))", false);
		Context.getAdministrationService()
		        .executeSQL(
		            "create table if not exists surveillance_center_coordinates (address_hierarchy_entry_id int primary key, latitude double, longitude double)",
		            false);
		Context.getAdministrationService().executeSQL("delete from surveillance_risk_config", false);
		Context.getAdministrationService().executeSQL("delete from surveillance_center_coordinates", false);
		Context.getAdministrationService().executeSQL("insert into surveillance_risk_config values (1,'10,20,50')", false);
		dao.saveCoordinates(42, -3.75, -73.25);
		dao.saveCoordinates(42, -3.75, -73.25);
		dao.saveCoordinates(43, -4.0, -74.0);
		dao.saveCoordinates(42, -3.5, -73.0);
		dao.saveRiskThresholds("5,15,30");
		Context.flushSession();
		Context.clearSession();
		assertEquals("5,15,30", dao.riskThresholds());
		assertEquals(2, dao.centerCoordinates().size());
		dao.saveCoordinateCode("pending-center", -5, -75);
		dao.saveCoordinateCode("pending-center", -5, -75);
		assertEquals(2, dao.centerCoordinates().size());
		Context.getAdministrationService().executeSQL("insert into address_hierarchy_level values (9900,'CITY_VILLAGE')",
		    false);
		Context.getAdministrationService()
		        .executeSQL(
		            "insert into address_hierarchy_entry (address_hierarchy_entry_id,level_id,user_generated_id) values (9900,9900,'pending-center')",
		            false);
		assertEquals(3, dao.centerCoordinates().size());
		Context.getAdministrationService()
		        .executeSQL(
		            "insert into address_hierarchy_entry (address_hierarchy_entry_id,level_id,user_generated_id) values (9901,9900,'pending-center')",
		            false);
		assertEquals(2, dao.centerCoordinates().size());
		for (Object[] row : dao.centerCoordinates())
			if (((Number) row[0]).intValue() == 42) {
				assertEquals(-3.5, ((Number) row[1]).doubleValue(), 0);
				assertEquals(-73.0, ((Number) row[2]).doubleValue(), 0);
			}
	}
	
	@Test
	public void ageConfigurationSurvivesSessionReloadAndUpdate() {
		org.openmrs.module.sihsalusepidemiologicalsurveillance.model.AgeGroupConfiguration config = new org.openmrs.module.sihsalusepidemiologicalsurveillance.model.AgeGroupConfiguration();
		config.setUuid(org.openmrs.module.sihsalusepidemiologicalsurveillance.model.AgeGroupConfiguration.UUID);
		config.setLowerBounds("0,2,12,18,30,60");
		dao.save(config);
		Context.flushSession();
		Context.clearSession();
		config = dao.byUuid(org.openmrs.module.sihsalusepidemiologicalsurveillance.model.AgeGroupConfiguration.class,
		    org.openmrs.module.sihsalusepidemiologicalsurveillance.model.AgeGroupConfiguration.UUID);
		assertEquals("0,2,12,18,30,60", config.getLowerBounds());
		org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.AgeGroup custom = new org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.AgeGroup(
		        "custom", 0, null);
		custom.name = String.join("", java.util.Collections.nCopies(120, "Grupo \u00e9 "));
		String stored = org.openmrs.module.sihsalusepidemiologicalsurveillance.api.DemographicCalculator
		        .validateGroups(java.util.Collections.singletonList(custom));
		config.setLowerBounds(stored);
		dao.save(config);
		Context.flushSession();
		Context.clearSession();
		assertEquals(
		    stored,
		    dao.byUuid(org.openmrs.module.sihsalusepidemiologicalsurveillance.model.AgeGroupConfiguration.class,
		        org.openmrs.module.sihsalusepidemiologicalsurveillance.model.AgeGroupConfiguration.UUID).getLowerBounds());
	}
	
	@Test
	public void personAndPatientSavesClearAttributionPersistently() {
		Patient patient = Context.getPatientService().getPatient(2);
		Encounter encounter = new Encounter();
		encounter.setPatient(patient);
		encounter.setEncounterDatetime(new Date());
		encounter.setLocation(Context.getLocationService().getLocation(1));
		encounter.setEncounterType(Context.getEncounterService().getAllEncounterTypes(false).get(0));
		Context.getEncounterService().saveEncounter(encounter);
		Diagnosis diagnosis = new Diagnosis();
		diagnosis.setPatient(patient);
		diagnosis.setEncounter(encounter);
		diagnosis.setDiagnosis(new CodedOrFreeText(Context.getConceptService().getConcept(3), null, null));
		diagnosis.setCertainty(ConditionVerificationStatus.CONFIRMED);
		diagnosis.setRank(1);
		Context.getDiagnosisService().save(diagnosis);
		Provider provider = new Provider();
		provider.setPerson(Context.getAuthenticatedUser().getPerson());
		provider.setIdentifier("snapshot-synthetic");
		Context.getProviderService().saveProvider(provider);
		SurveillanceCase value = new SurveillanceCase();
		value.setPatient(patient);
		value.setEncounter(encounter);
		value.setDiagnosis(diagnosis);
		value.setProvider(provider);
		value.setLocation(encounter.getLocation());
		value.setDiagnosisType("PROBABLE");
		value.setCreator(Context.getAuthenticatedUser());
		value.setDateCreated(new Date());
		
		patient.setCauseOfDeathNonCoded("Synthetic cause");
		patient.setDead(true);
		patient.setDeathDate(new Date());
		Context.getPatientService().savePatient(patient);
		value.setDeathAttribution("SI");
		dao.save(value);
		String uuid = value.getUuid();
		Context.flushSession();
		Context.clearSession();
		value = dao.byUuid(SurveillanceCase.class, uuid);
		assertEquals("SI", value.getDeathAttribution());
		patient = value.getPatient();
		patient.setDead(false);
		patient.setDeathDate(null);
		Context.getPatientService().savePatient(patient);
		Context.flushSession();
		Context.clearSession();
		value = dao.byUuid(SurveillanceCase.class, uuid);
		assertNull(value.getDeathAttribution());
		Person person = Context.getPersonService().getPerson(value.getPatient().getId());
		person.setCauseOfDeathNonCoded("Synthetic cause");
		person.setDead(true);
		person.setDeathDate(new Date());
		Context.getPersonService().savePerson(person);
		value.setDeathAttribution("NO");
		dao.save(value);
		Context.flushSession();
		Context.clearSession();
		person = Context.getPersonService().getPerson(person.getId());
		person.setDead(false);
		person.setDeathDate(null);
		Context.getPersonService().savePerson(person);
		Context.flushSession();
		Context.clearSession();
		assertNull(dao.byUuid(SurveillanceCase.class, uuid).getDeathAttribution());
	}
	
	@Test
	public void hibernatePersistsSnapshotButNeverUpdatesItsColumns() {
		Patient patient = Context.getPatientService().getPatient(2);
		Encounter encounter = new Encounter();
		encounter.setPatient(patient);
		encounter.setEncounterDatetime(new Date());
		encounter.setLocation(Context.getLocationService().getLocation(1));
		encounter.setEncounterType(Context.getEncounterService().getAllEncounterTypes(false).get(0));
		Context.getEncounterService().saveEncounter(encounter);
		Diagnosis diagnosis = new Diagnosis();
		diagnosis.setPatient(patient);
		diagnosis.setEncounter(encounter);
		diagnosis.setDiagnosis(new CodedOrFreeText(Context.getConceptService().getConcept(3), null, null));
		diagnosis.setCertainty(ConditionVerificationStatus.CONFIRMED);
		diagnosis.setRank(1);
		Context.getDiagnosisService().save(diagnosis);
		Provider provider = new Provider();
		provider.setPerson(Context.getAuthenticatedUser().getPerson());
		provider.setIdentifier("snapshot-synthetic");
		Context.getProviderService().saveProvider(provider);
		SurveillanceCase value = new SurveillanceCase();
		value.setPatient(patient);
		value.setEncounter(encounter);
		value.setDiagnosis(diagnosis);
		value.setProvider(provider);
		value.setLocation(encounter.getLocation());
		value.setDiagnosisType("PROBABLE");
		value.setCreator(Context.getAuthenticatedUser());
		value.setDateCreated(new Date());
		value.setNotificationDate(java.util.Date.from(java.time.Instant.parse("2026-01-22T04:35:12Z")));
		value.setHabitualResidence("3");
		value.setPatientGivenNames("Synthetic Given");
		value.setPatientPaternalSurname("Paternal");
		value.setPatientMaternalSurname("Maternal");
		value.setPatientSex("F");
		value.setPatientAge(35);
		value.setPatientResidence("Synthetic residence");
		value.setPregnant(true);
		dao.save(value);
		String uuid = value.getUuid();
		Context.flushSession();
		Context.clearSession();
		value = dao.byUuid(SurveillanceCase.class, uuid);
		assertEquals(java.time.Instant.parse("2026-01-22T04:35:12Z").toEpochMilli(), value.getNotificationDate().getTime());
		assertEquals("3", value.getHabitualResidence());
		assertEquals("Synthetic Given", value.getPatientGivenNames());
		assertTrue(value.getPregnant());
		Date notification = value.getNotificationDate();
		assertEquals(1, dao.weeklyCases(notification, new Date(notification.getTime() + 1000)).size());
		assertTrue(dao.weeklyCases(new Date(notification.getTime() - 1000), notification).isEmpty());
		value.setPatientGivenNames("Changed");
		value.setPatientPaternalSurname("Changed");
		value.setPatientMaternalSurname("Changed");
		value.setPatientSex("M");
		value.setPatientAge(99);
		value.setPatientResidence("Changed");
		value.setPregnant(false);
		dao.save(value);
		Context.flushSession();
		Context.clearSession();
		value = dao.byUuid(SurveillanceCase.class, uuid);
		assertEquals(java.time.Instant.parse("2026-01-22T04:35:12Z").toEpochMilli(), value.getNotificationDate().getTime());
		assertEquals("3", value.getHabitualResidence());
		assertEquals("Synthetic Given", value.getPatientGivenNames());
		assertEquals("Paternal", value.getPatientPaternalSurname());
		assertEquals("Maternal", value.getPatientMaternalSurname());
		assertEquals("F", value.getPatientSex());
		assertEquals(Integer.valueOf(35), value.getPatientAge());
		assertEquals("Synthetic residence", value.getPatientResidence());
		assertFalse(value.getPregnant());
	}
	
	@Autowired
	private SurveillanceDao dao;
	
	@Test
	public void editingOneCaseReplacesOldBucketsInsteadOfAddingAnotherCase() {
		NotifiableEvent event = new NotifiableEvent();
		event.setConcept(Context.getConceptService().getConcept(3));
		event.setPeriodicity("SEMANAL");
		event.setReferenceRegulation("Synthetic edit regression");
		event.setValidFrom(new Date());
		dao.save(event);
		org.openmrs.Diagnosis diagnosis = new org.openmrs.Diagnosis();
		diagnosis.setDiagnosis(new org.openmrs.CodedOrFreeText(event.getConcept(), null, null));
		org.openmrs.module.sihsalusepidemiologicalsurveillance.model.SurveillanceCase value = new org.openmrs.module.sihsalusepidemiologicalsurveillance.model.SurveillanceCase();
		value.setDiagnosis(diagnosis);
		value.setDiagnosisType("PROBABLE");
		value.setInfectionAddress(100);
		value.setOnsetDate(java.sql.Date.valueOf("2026-09-11"));
		org.openmrs.module.sihsalusepidemiologicalsurveillance.api.ReportCalculator calculator = new org.openmrs.module.sihsalusepidemiologicalsurveillance.api.ReportCalculator();
		org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog metadata = new org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog();
		for (int edit = 0; edit < 5; edit++) {
			event = dao.byUuid(NotifiableEvent.class, event.getUuid());
			diagnosis.setDiagnosis(new org.openmrs.CodedOrFreeText(event.getConcept(), null, null));
			if (edit == 2)
				value.setOnsetDate(java.sql.Date.valueOf("2026-09-12"));
			if (edit == 3)
				value.setDiagnosisType("CONFIRMADO");
			if (edit == 4)
				value.setInfectionAddress(101);
			dao.replaceCounts(event, calculator.aggregateSurveillanceCases(event, Collections.singletonList(value),
			    Collections.singletonMap(value.getInfectionAddress(), 10), metadata, Context.getAuthenticatedUser(),
			    java.time.LocalDate.of(2026, 9, 13)));
			Context.flushSession();
			Context.clearSession();
			java.util.List<org.openmrs.module.sihsalusepidemiologicalsurveillance.model.PeriodCaseCount> daily = dao.counts(
			    event, "dia", 2026, 2026, "CENTRO_POBLADO", null);
			assertEquals("Only one daily bucket after edit " + edit, 1, daily.size());
			assertEquals(Integer.valueOf(1), daily.get(0).getCaseCount());
			assertEquals(value.getOnsetDate().toString(), daily.get(0).getStartDate().toString());
			assertEquals(value.getDiagnosisType(), daily.get(0).getDiagnosisType());
			assertEquals(value.getInfectionAddress(), daily.get(0).getAddressHierarchyEntryId());
		}
		value.setDiagnosisType("DESCARTADO");
		dao.replaceCounts(event,
		    calculator.aggregateSurveillanceCases(event, Collections.singletonList(value),
		        Collections.singletonMap(101, 10), metadata, Context.getAuthenticatedUser(),
		        java.time.LocalDate.of(2026, 9, 13)));
		Context.flushSession();
		Context.clearSession();
		assertTrue(dao.counts(event, "dia", 2026, 2026).isEmpty());
	}
	
	@Autowired
	private org.openmrs.api.db.hibernate.DbSessionFactory sessionFactory;
	
	@Test
	public void readsInfectionHierarchyWithDistinctSqlColumnAliases() {
		org.openmrs.api.db.hibernate.DbSession session = sessionFactory.getCurrentSession();
		session.createSQLQuery(
		    "create table if not exists address_hierarchy_entry (address_hierarchy_entry_id int primary key, parent_id int, name varchar(255), level_id int)")
		        .executeUpdate();
		session.createSQLQuery(
		    "insert into address_hierarchy_entry (address_hierarchy_entry_id,parent_id,name,level_id) values (9000,null,'Departamento de prueba',1),(9001,9000,'Provincia de prueba',2),(9002,9001,'Distrito de prueba',3),(9003,9002,'Centro de prueba',4)")
		        .executeUpdate();
		assertEquals("Departamento de prueba → Provincia de prueba → Distrito de prueba → Centro de prueba",
		    dao.addressDisplay(9003));
		assertNull(dao.addressDisplay(9999));
		session.createSQLQuery(
		    "create table if not exists address_hierarchy_level (address_hierarchy_level_id int primary key, address_field varchar(255))")
		        .executeUpdate();
		session.createSQLQuery(
		    "insert into address_hierarchy_level values (1,'ADDRESS_1'),(2,'STATE_PROVINCE'),(3,'COUNTY_DISTRICT'),(4,'CITY_VILLAGE')")
		        .executeUpdate();
		assertEquals(Integer.valueOf(9000), dao.ancestorIdForPopulatedCenter(9003, "ADDRESS_1"));
		assertEquals(Integer.valueOf(9001), dao.ancestorIdForPopulatedCenter(9003, "STATE_PROVINCE"));
		assertNull(dao.ancestorIdForPopulatedCenter(9999, "ADDRESS_1"));
		session.createSQLQuery("alter table address_hierarchy_entry add column if not exists user_generated_id varchar(255)")
		        .executeUpdate();
		session.createSQLQuery(
		    "update address_hierarchy_entry set user_generated_id = '0101010001' where address_hierarchy_entry_id = 9003")
		        .executeUpdate();
		assertEquals("0101010001", dao.addressCode(9003));
		assertNull(dao.addressCode(9999));
		assertEquals("0101010001",
		    dao.residenceCode("Departamento de prueba", "Provincia de prueba", "Distrito de prueba", "Centro de prueba"));
		assertNull(dao.residenceCode("Otro departamento", "Provincia de prueba", "Distrito de prueba", "Centro de prueba"));
		session.createSQLQuery(
		    "insert into address_hierarchy_entry (address_hierarchy_entry_id,parent_id,name,level_id,user_generated_id) values (9004,9002,'Centro de prueba',4,'0101010002')")
		        .executeUpdate();
		assertNull(dao.residenceCode("Departamento de prueba", "Provincia de prueba", "Distrito de prueba",
		    "Centro de prueba"));
	}
	
	@Test
	public void aggregateQueryFiltersPeriodYearsAndGeographyAfterReload() {
		NotifiableEvent event = new NotifiableEvent();
		event.setConcept(Context.getConceptService().getConcept(3));
		event.setPeriodicity("SEMANAL");
		event.setReferenceRegulation("Synthetic reporting test");
		event.setValidFrom(new Date());
		dao.save(event);
		for (String level : Arrays.asList("DISTRITO", "CENTRO_POBLADO")) {
			for (int year : Arrays.asList(2025, 2026)) {
				org.openmrs.module.sihsalusepidemiologicalsurveillance.model.PeriodCaseCount count = new org.openmrs.module.sihsalusepidemiologicalsurveillance.model.PeriodCaseCount();
				count.setEvent(event);
				count.setAddressHierarchyEntryId("DISTRITO".equals(level) ? 10 : 100);
				count.setZoneLevel(level);
				count.setPeriodType("DIA");
				count.setYear(year);
				count.setPeriodNumber(1);
				count.setDiagnosisType("CONFIRMADO");
				count.setCaseCount(7);
				count.setStartDate(java.sql.Date.valueOf(year + "-01-01"));
				count.setEndDate(count.getStartDate());
				count.setCalculationDate(new Date());
				count.setCreator(Context.getAuthenticatedUser());
				count.setDateCreated(new Date());
				dao.save(count);
			}
		}
		Context.flushSession();
		Context.clearSession();
		assertEquals(1, dao.counts(event, "dia", 2026, 2026, "DISTRITO", 10).size());
		assertEquals(2, dao.counts(event, "dia", 2025, 2026, "CENTRO_POBLADO", null).size());
		assertTrue(dao.counts(event, "dia", 2026, 2026, "CENTRO_POBLADO", 10).isEmpty());
		assertTrue(dao.counts(event, "semana", 2026, 2026, "DISTRITO", 10).isEmpty());
	}
	
	@Test
	public void persistsAndReadsAnEventThroughTheRegisteredHibernateMapping() {
		NotifiableEvent event = new NotifiableEvent();
		event.setConcept(Context.getConceptService().getConcept(3));
		event.setPeriodicity("SEMANAL");
		event.setReferenceRegulation("Synthetic regulation");
		event.setValidFrom(new Date());
		dao.save(event);
		assertNotNull(event.getId());
		assertEquals(event.getUuid(), dao.byUuid(NotifiableEvent.class, event.getUuid()).getUuid());
	}
	
	@Test
	public void nativeClinicalQueriesCompileAgainstCoreMappings() {
		assertTrue(dao.encounters(new Date(0), new Date(), "00000000-0000-4000-8000-000000000002").isEmpty());
		assertTrue(dao.possibleDuplicates(Context.getPatientService().getPatient(2),
		    Arrays.asList("00000000-0000-4000-8000-000000000003"), new Date(0), new Date(),
		    "00000000-0000-4000-8000-000000000002").isEmpty());
	}
	
	@Test
	public void registersNativeClinicalDataAndReadsItAfterClearingTheSession() {
		SyntheticFixture f = new SyntheticFixture();
		org.openmrs.Patient patient = Context.getPatientService().getPatient(2);
		org.openmrs.User actor = Context.getAuthenticatedUser();
		org.openmrs.Location location = Context.getLocationService().getLocation(1);
		org.openmrs.Provider provider = new org.openmrs.Provider();
		provider.setPerson(actor.getPerson());
		provider.setIdentifier("synthetic-surveillance");
		Context.getProviderService().saveProvider(provider);
		org.openmrs.EncounterRole role = Context.getEncounterService().getAllEncounterRoles(false).get(0);
		org.openmrs.EncounterType type = Context.getEncounterService().getAllEncounterTypes(false).get(0);
		Date careDate = new Date(System.currentTimeMillis() - 86400000L);
		org.openmrs.Visit visit = new org.openmrs.Visit(patient, Context.getVisitService().getAllVisitTypes().get(0),
		        careDate);
		Context.getVisitService().saveVisit(visit);
		org.openmrs.Encounter source = new org.openmrs.Encounter();
		source.setPatient(patient);
		source.setVisit(visit);
		source.setLocation(location);
		source.setEncounterType(type);
		source.setEncounterDatetime(careDate);
		Context.getEncounterService().saveEncounter(source);
		for (String key : f.m.questions.keySet()) {
			String datatype = java.util.Arrays.asList("event", "status", "origin", "severity", "species").contains(key)
			        ? "Coded"
			        : "onset".equals(key) ? "Date" : "pregnancy".equals(key) ? "Boolean" : "Text";
			org.openmrs.Concept concept = nativeConcept("Synthetic " + key, datatype);
			f.m.questions.put(key, concept.getUuid());
		}
		for (org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog.Choice choice : f.m.statuses)
			choice.conceptUuid = nativeConcept("Synthetic status " + choice.key, "N/A").getUuid();
		for (org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog.Choice choice : f.m.origins)
			choice.conceptUuid = nativeConcept("Synthetic origin " + choice.key, "N/A").getUuid();
		for (org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog.Choice choice : f.disease.severities)
			choice.conceptUuid = nativeConcept("Synthetic severity " + choice.key, "N/A").getUuid();
		org.openmrs.ConceptSource codingSource = new org.openmrs.ConceptSource();
		codingSource.setName("Synthetic ICD-10");
		codingSource.setDescription("Test only");
		Context.getConceptService().saveConceptSource(codingSource);
		f.m.icd10SourceUuid = codingSource.getUuid();
		org.openmrs.ConceptReferenceTerm term = new org.openmrs.ConceptReferenceTerm();
		term.setConceptSource(codingSource);
		term.setCode("A90");
		Context.getConceptService().saveConceptReferenceTerm(term);
		org.openmrs.Concept diagnosis = nativeConcept("Synthetic diagnosis", "N/A");
		org.openmrs.ConceptMap mapping = new org.openmrs.ConceptMap();
		mapping.setConceptReferenceTerm(term);
		mapping.setConceptMapType(Context.getConceptService().getConceptMapType(1));
		diagnosis.addConceptMapping(mapping);
		Context.getConceptService().saveConcept(diagnosis);
		for (org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog.DiagnosisMapping d : f.disease.diagnoses)
			d.diagnosisConceptUuid = diagnosis.getUuid();
		NotifiableEvent event = new NotifiableEvent();
		event.setConcept(nativeConcept("Synthetic disease event", "N/A"));
		event.setPeriodicity("SEMANAL");
		event.setReferenceRegulation("Synthetic regulation");
		event.setValidFrom(new Date());
		dao.save(event);
		f.disease.eventUuid = event.getUuid();
		f.request.eventUuid = event.getUuid();
		f.request.patientUuid = patient.getUuid();
		f.request.sourceEncounterUuid = source.getUuid();
		f.request.providerUuid = provider.getUuid();
		f.request.locationUuid = location.getUuid();
		f.m.encounterRoleUuid = role.getUuid();
		f.request.onsetDate = new org.openmrs.module.sihsalusepidemiologicalsurveillance.api.EpidemiologicalCalendar(f.m)
		        .local(careDate).toString();
		org.mockito.Mockito.when(f.access.require(org.mockito.Mockito.anyString())).thenReturn(actor);
		org.mockito.Mockito.when(f.clinical.patient(patient.getUuid())).thenReturn(patient);
		org.mockito.Mockito.when(f.clinical.encounter(source.getUuid())).thenReturn(source);
		org.mockito.Mockito.when(f.clinical.provider(provider.getUuid())).thenReturn(provider);
		org.mockito.Mockito.when(f.clinical.location(location.getUuid())).thenReturn(location);
		org.mockito.Mockito.when(f.clinical.encounterRole(role.getUuid())).thenReturn(role);
		org.mockito.Mockito.when(f.clinical.encounterType(type.getUuid())).thenReturn(type);
		org.mockito.Mockito.when(f.clinical.concept(org.mockito.Mockito.anyString()))
		        .thenAnswer(i -> Context.getConceptService().getConceptByUuid(i.getArgument(0)));
		org.mockito.Mockito.when(f.clinical.saveEncounter(org.mockito.Mockito.any()))
		        .thenAnswer(i -> Context.getEncounterService().saveEncounter(i.getArgument(0)));
		org.mockito.Mockito.when(f.clinical.saveDiagnosis(org.mockito.Mockito.any()))
		        .thenAnswer(i -> Context.getDiagnosisService().save(i.getArgument(0)));
		f.service.setDao(dao);
		org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.CaseResult result = f.service
		        .registerCase(f.request);
		Context.flushSession();
		Context.clearSession();
		org.openmrs.Encounter persisted = Context.getEncounterService().getEncounterByUuid(result.uuid);
		assertEquals(5, persisted.getAllObs(false).size());
		assertEquals(1, persisted.getDiagnoses().size());
		assertEquals("A90", result.icd10);
		assertEquals(visit.getUuid(), persisted.getVisit().getUuid());
		assertEquals(1,
		    dao.encounters(new Date(careDate.getTime() - 86400000L), new Date(), f.m.questions.get("onset")).size());
	}
	
	private org.openmrs.Concept nativeConcept(String name, String datatype) {
		org.openmrs.Concept concept = new org.openmrs.Concept();
		concept.addName(new org.openmrs.ConceptName(name, java.util.Locale.ENGLISH));
		concept.setDatatype(Context.getConceptService().getConceptDatatypeByName(datatype));
		concept.setConceptClass(Context.getConceptService().getConceptClass(1));
		return Context.getConceptService().saveConcept(concept);
	}
}
