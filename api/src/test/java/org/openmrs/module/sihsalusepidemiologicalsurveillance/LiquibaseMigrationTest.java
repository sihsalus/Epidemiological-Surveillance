package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;

import java.sql.*;
import java.util.UUID;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.Test;

/**
 * Synthetic native-key stubs exercise migration integrity; this is not a MariaDB acceptance test.
 */
public class LiquibaseMigrationTest {
	
	@Test
	public void migrationsAreRepeatableAndEnforceForeignKeysAndUniquePeriods() throws Exception {
		migrate(false);
	}
	
	@Test
	public void recoversEmptyPartialTablesBeforeCreatingTheCompleteSchema() throws Exception {
		migrate(true);
	}
	
	private void migrate(boolean partialSchema) throws Exception {
		try (Connection connection = DriverManager
		        .getConnection("jdbc:h2:mem:surveillance-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "")) {
			try (Statement sql = connection.createStatement()) {
				sql.execute("create table concept(concept_id int primary key, uuid varchar(38) unique)");
				sql.execute("create table users(user_id int primary key)");
				sql.execute("create table patient(patient_id int primary key)");
				sql.execute("create table location(location_id int primary key)");
				sql.execute("create table encounter(encounter_id int primary key, encounter_datetime timestamp)");
				sql.execute("create table provider(provider_id int primary key)");
				assertEquals("encounter_diagnosis",
				    org.openmrs.Diagnosis.class.getAnnotation(javax.persistence.Table.class).name());
				sql.execute("create table encounter_diagnosis(diagnosis_id int primary key)");
				sql.execute("create table orders(order_id int primary key)");
				sql.execute("create table obs(obs_id int primary key)");
				sql.execute("create table notification_alert(alert_id int primary key)");
				sql.execute("create table address_hierarchy_entry(address_hierarchy_entry_id int primary key)");
				sql.execute(
				    "create table scheduler_task_config(task_config_id int auto_increment primary key, name varchar(255), description varchar(1024), schedulable_class varchar(1024), repeat_interval bigint, start_on_startup boolean, started boolean, uuid varchar(38) unique, created_by int)");
				sql.execute("insert into concept values (1, 'synthetic-concept')");
				sql.execute("insert into concept values (3, '41cb3fbf-f50c-4d7d-87bc-1b5dd963f8d0')");
				sql.execute("insert into concept values (4, 'fae143be-b2c2-4d55-86de-09cf708911d6')");
				sql.execute("insert into users values (1)");
				sql.execute("insert into patient values (1)");
				sql.execute("insert into location values (1)");
				sql.execute("insert into encounter values (1, TIMESTAMP '2026-01-20 23:30:00')");
				sql.execute("insert into provider values (1)");
				sql.execute("insert into encounter_diagnosis values (1)");
				sql.execute("insert into orders values (1)");
				sql.execute("insert into notification_alert values (1)");
				sql.execute("insert into address_hierarchy_entry values (1)");
			}
			Database database = DatabaseFactory.getInstance()
			        .findCorrectDatabaseImplementation(new JdbcConnection(connection));
			Liquibase liquibase = new Liquibase("liquibase.xml", new ClassLoaderResourceAccessor(), database);
			liquibase.update(8, "");
			if (partialSchema) {
				try (Statement sql = connection.createStatement()) {
					for (String table : new String[] { "notifiable_event", "outbreak_alert_rule", "outbreak_alert",
					        "period_case_count", "epidemiological_focus", "surveillance_case", "individual_record" })
						sql.execute("create table " + table + " (id int primary key)");
					sql.execute("alter table surveillance_case add event_id int references notifiable_event(id)");
				}
			}
			liquibase.update("");
			liquibase.update("");
			try (Statement sql = connection.createStatement()) {
				sql.execute(
				    "insert into surveillance_alert (uuid,type,reason,case_uuid,event_uuid,occurrence_key,status,generated_at) values ('a','IMMEDIATE','DEATH','c','e','occurrence','PENDING',CURRENT_TIMESTAMP)");
				fails(sql,
				    "insert into surveillance_alert (uuid,type,reason,case_uuid,event_uuid,occurrence_key,status,generated_at) values ('b','IMMEDIATE','DEATH','c','e','occurrence','PENDING',CURRENT_TIMESTAMP)");
				sql.execute(
				    "update surveillance_alert set status='ATTENDED', attended_at=CURRENT_TIMESTAMP, attended_by='responsible' where uuid='a'");
			}
			
			try (Statement sql = connection.createStatement();
			        ResultSet config = sql.executeQuery(
			            "select historical_years, minimum_years from surveillance_endemic_config where id=1")) {
				assertTrue(config.next());
				assertEquals(5, config.getInt(1));
				assertEquals(3, config.getInt(2));
				assertFalse(config.next());
			}
			try (Statement sql = connection.createStatement();
			        ResultSet config = sql.executeQuery("select lower_bounds from surveillance_risk_config where id=1")) {
				assertTrue(config.next());
				assertEquals("10,20,50", config.getString(1));
				assertFalse(config.next());
			}
			try (Statement sql = connection.createStatement()) {
				sql.execute("insert into surveillance_center_coordinates values (1,-3.75,-73.25)");
				fails(sql, "insert into surveillance_center_coordinates values (1,-4,-74)");
				try (ResultSet coordinates = sql.executeQuery(
				    "select latitude,longitude from surveillance_center_coordinates where address_hierarchy_entry_id=1")) {
					assertTrue(coordinates.next());
					assertEquals(-3.75, coordinates.getDouble(1), 0);
					assertEquals(-73.25, coordinates.getDouble(2), 0);
				}
			}
			try (Statement sql = connection.createStatement();
			        ResultSet config = sql.executeQuery("select lower_bounds from surveillance_age_group_config")) {
				assertTrue(config.next());
				assertEquals("0,2,12,18,30,60", config.getString(1));
				assertFalse(config.next());
			}
			boolean diagnosisForeignKeyFound = false;
			try (ResultSet keys = connection.getMetaData().getImportedKeys(null, null, "SURVEILLANCE_CASE")) {
				while (keys.next()) {
					if ("DIAGNOSIS_ID".equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))) {
						assertEquals("ENCOUNTER_DIAGNOSIS",
						    keys.getString("PKTABLE_NAME").toUpperCase(java.util.Locale.ROOT));
						assertEquals("DIAGNOSIS_ID", keys.getString("PKCOLUMN_NAME").toUpperCase(java.util.Locale.ROOT));
						diagnosisForeignKeyFound = true;
					}
				}
			}
			assertTrue("Expected FK to the native encounter diagnosis table", diagnosisForeignKeyFound);
			try (Statement sql = connection.createStatement()) {
				for (String table : new String[] { "NOTIFIABLE_EVENT", "PERIOD_CASE_COUNT", "SURVEILLANCE_CASE" }) {
					assertTrue("Expected current table " + table,
					    connection.getMetaData().getTables(null, null, table, null).next());
				}
				for (String retired : new String[] { "SURVEILLANCE_NOTIFIABLE_EVENT", "SURVEILLANCE_OUTBREAK_ALERT_RULE",
				        "SURVEILLANCE_PERIOD_CASE_COUNT", "SURVEILLANCE_EPIDEMIOLOGICAL_FOCUS", "SURVEILLANCE_AUDIT",
				        "SURVEILLANCE_NOTI_NOTIFICATION", "EPIDEMIOLOGYSURVEILLANCE_ITEM", "OUTBREAK_ALERT",
				        "OUTBREAK_ALERT_RULE", "EPIDEMIOLOGICAL_FOCUS", "INDIVIDUAL_RECORD" }) {
					assertFalse("Historical table must be retired: " + retired,
					    connection.getMetaData().getTables(null, null, retired, null).next());
				}
				ResultSet tasks = sql.executeQuery(
				    "select count(*) from scheduler_task_config where uuid = '1568be35-4d88-485b-9698-d6cda57f8c5e'");
				tasks.next();
				assertEquals(0, tasks.getInt(1));
				tasks.close();
				sql.execute(
				    "insert into notifiable_event(notifiable_event_id,uuid,concept_id,periodicity,reference_regulation,valid_from,creator,date_created) values(10,'event',1,'SEMANAL','test',CURRENT_DATE,1,CURRENT_TIMESTAMP)");
				fails(sql,
				    "insert into notifiable_event(uuid,concept_id,periodicity,reference_regulation,valid_from,active,creator,date_created) values('duplicate-event',1,'SEMANAL','test','2020-01-01',false,1,CURRENT_TIMESTAMP)");
				sql.execute(
				    "insert into period_case_count(uuid,notifiable_event_id,address_hierarchy_entry_id,zone_level,period_type,year,period_number,start_date,end_date,diagnosis_type,case_count,calculation_date,creator,date_created) values('count',10,1,'DISTRITO','SEMANA',2026,1,CURRENT_DATE,CURRENT_DATE,'CONFIRMADO',2,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP)");
				fails(sql,
				    "insert into period_case_count(uuid,notifiable_event_id,address_hierarchy_entry_id,zone_level,period_type,year,period_number,start_date,end_date,diagnosis_type,case_count,calculation_date,creator,date_created) values('duplicate',10,1,'DISTRITO','SEMANA',2026,1,CURRENT_DATE,CURRENT_DATE,'CONFIRMADO',3,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP)");
				fails(sql, "delete from notifiable_event where notifiable_event_id=10");
				sql.execute(
				    "insert into surveillance_case(surveillance_case_id,uuid,patient_id,encounter_id,provider_id,location_id,diagnosis_id,diagnosis_type,creator,date_created,voided) values(1,'case',1,1,1,1,1,'PROBABLE',1,CURRENT_TIMESTAMP,false)");
				fails(sql,
				    "insert into surveillance_case(surveillance_case_id,uuid,patient_id,encounter_id,provider_id,location_id,diagnosis_id,diagnosis_type,creator,date_created,voided) values(2,'same-diagnosis',1,1,1,1,1,'PROBABLE',1,CURRENT_TIMESTAMP,false)");
				fails(sql,
				    "insert into surveillance_case(surveillance_case_id,uuid,patient_id,encounter_id,provider_id,location_id,diagnosis_id,diagnosis_type,creator,date_created,voided) values(3,'missing-diagnosis',1,1,1,1,999,'PROBABLE',1,CURRENT_TIMESTAMP,false)");
			}
		}
	}
	
	private void fails(Statement sql, String statement) throws Exception {
		try {
			sql.execute(statement);
			fail("Constraint must reject invalid data");
		}
		catch (SQLException expected) {
			assertNotNull(expected.getSQLState());
		}
	}
}
