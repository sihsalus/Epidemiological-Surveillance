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
 * Runs the actual retirement changelog against a populated active schema and empty retired tables.
 */
public class RetireUnusedTablesTest {
	
	private static final String[] RETIRED = { "epidemiologysurveillance_item", "outbreak_alert", "outbreak_alert_rule",
	        "epidemiological_focus", "individual_record" };
	
	private Connection schema() throws Exception {
		Connection c = DriverManager.getConnection("jdbc:h2:mem:retire-" + UUID.randomUUID(), "sa", "");
		try (Statement s = c.createStatement()) {
			s.execute("create table individual_record(individual_record_id int primary key)");
			s.execute("create table outbreak_alert_rule(rule_id int primary key)");
			s.execute(
			    "create table outbreak_alert(id int primary key, rule_id int, constraint oa_rule_fk foreign key(rule_id) references outbreak_alert_rule(rule_id))");
			s.execute(
			    "create table surveillance_case(id int primary key, individual_record_id int, constraint sc_record_fk foreign key(individual_record_id) references individual_record(individual_record_id))");
			s.execute(
			    "create table epidemiological_focus(id int primary key, last_case_id int references surveillance_case(id))");
			s.execute("create table epidemiologysurveillance_item(id int primary key)");
			s.execute("insert into surveillance_case values(1,null)");
			for (String active : new String[] { "surveillance_alert", "surveillance_outbreak_config",
			        "period_case_count" }) {
				s.execute("create table " + active + "(id int primary key)");
				s.execute("insert into " + active + " values(1)");
			}
		}
		return c;
	}
	
	private void migrate(Connection c) throws Exception {
		Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(c));
		new Liquibase("liquibase-retire-unused-tables.xml", new ClassLoaderResourceAccessor(), database).update("");
	}
	
	private boolean exists(Connection c, String table) throws Exception {
		try (ResultSet r = c.getMetaData().getTables(null, null, table.toUpperCase(java.util.Locale.ROOT), null)) {
			return r.next();
		}
	}
	
	private void assertBlocked(Connection c) throws Exception {
		try {
			migrate(c);
			fail("Retirement must halt before DDL");
		}
		catch (liquibase.exception.LiquibaseException expected) {
			assertNotNull(expected.getMessage());
		}
		for (String table : RETIRED)
			assertTrue("Must retain " + table, exists(c, table));
		try (ResultSet r = c.getMetaData().getColumns(null, null, "SURVEILLANCE_CASE", "INDIVIDUAL_RECORD_ID")) {
			assertTrue("Must retain the case column", r.next());
		}
	}
	
	@Test
	public void dropsRetiredTablesAndPreservesCasesAndActiveDataOnRepeatedUpdate() throws Exception {
		try (Connection c = schema()) {
			migrate(c);
			migrate(c);
			for (String table : RETIRED)
				assertFalse(exists(c, table));
			try (ResultSet r = c.getMetaData().getColumns(null, null, "SURVEILLANCE_CASE", "INDIVIDUAL_RECORD_ID")) {
				assertFalse(r.next());
			}
			try (Statement s = c.createStatement()) {
				for (String active : new String[] { "surveillance_case", "surveillance_alert",
				        "surveillance_outbreak_config", "period_case_count" })
					try (ResultSet r = s.executeQuery("select count(*) from " + active + " where id=1")) {
						r.next();
						assertEquals(1, r.getInt(1));
					}
			}
		}
	}
	
	@Test
	public void blocksDataInEveryRetiredTableBeforeAnyDdl() throws Exception {
		for (String table : RETIRED)
			try (Connection c = schema(); Statement s = c.createStatement()) {
				String column = "individual_record".equals(table) ? "individual_record_id"
				        : "outbreak_alert_rule".equals(table) ? "rule_id" : "id";
				s.execute("insert into " + table + "(" + column + ") values(1)");
				assertBlocked(c);
			}
	}
	
	@Test
	public void blocksUnexpectedIncomingForeignKeysEvenWhenEmpty() throws Exception {
		try (Connection c = schema(); Statement s = c.createStatement()) {
			s.execute("create table external_consumer(id int references individual_record(individual_record_id))");
			assertBlocked(c);
		}
	}
	
	@Test
	public void blocksForeignKeysFromAnotherSchema() throws Exception {
		try (Connection c = schema(); Statement s = c.createStatement()) {
			s.execute("create schema external");
			s.execute("create table external.consumer(id int references public.outbreak_alert_rule(rule_id))");
			assertBlocked(c);
		}
	}
	
	@Test
	public void blocksLinkedCasesWithoutClearingTheLink() throws Exception {
		try (Connection c = schema(); Statement s = c.createStatement()) {
			s.execute("insert into individual_record values(1)");
			s.execute("update surveillance_case set individual_record_id=1");
			assertBlocked(c);
			try (ResultSet r = s.executeQuery("select individual_record_id from surveillance_case")) {
				r.next();
				assertEquals(1, r.getInt(1));
			}
		}
	}
}
