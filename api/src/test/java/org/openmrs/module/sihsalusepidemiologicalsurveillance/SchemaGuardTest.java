package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.sql.*;
import java.util.*;

import liquibase.changelog.RanChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import org.junit.Test;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db.RecoverEmptySurveillanceSchema;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db.VerifyUnusedSurveillanceTables;

/** Halting conditions of the schema guards that run before any destructive DDL. */
public class SchemaGuardTest {
	
	private Connection connection(String... statements) throws SQLException {
		Connection connection = DriverManager.getConnection("jdbc:h2:mem:guard-" + UUID.randomUUID(), "sa", "");
		try (Statement sql = connection.createStatement()) {
			for (String statement : statements)
				sql.execute(statement);
		}
		return connection;
	}
	
	private Database database(Connection connection) throws Exception {
		return DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
	}
	
	private void assertHalts(String message, Database database) {
		try {
			new VerifyUnusedSurveillanceTables().execute(database);
			fail("Retirement must halt");
		}
		catch (CustomChangeException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains(message));
		}
	}
	
	@Test
	public void retirementHaltsWhenARetiredTableIsMissing() throws Exception {
		assertHalts("missing table",
		    database(connection("create table surveillance_case(id int primary key, individual_record_id int)")));
	}
	
	@Test
	public void retirementHaltsWithoutTheCurrentCaseTable() throws Exception {
		assertHalts("missing surveillance_case",
		    database(connection("create table individual_record(individual_record_id int primary key)",
		        "create table outbreak_alert_rule(rule_id int primary key)",
		        "create table epidemiologysurveillance_item(id int primary key)",
		        "create table epidemiological_focus(id int primary key)",
		        "create table outbreak_alert(id int primary key, rule_id int)")));
	}
	
	@Test
	public void retirementHaltsWhenTheReviewedForeignKeysAreAbsent() throws Exception {
		assertHalts("missing expected foreign keys",
		    database(connection("create table individual_record(individual_record_id int primary key)",
		        "create table outbreak_alert_rule(rule_id int primary key)",
		        "create table epidemiologysurveillance_item(id int primary key)",
		        "create table epidemiological_focus(id int primary key)",
		        "create table outbreak_alert(id int primary key, rule_id int)",
		        "create table surveillance_case(id int primary key, individual_record_id int)")));
	}
	
	@Test
	public void databaseFailuresHaltRetirementAndRecoveryWithoutDdl() throws Exception {
		Connection closed = connection();
		Database database = spy(database(closed));
		doReturn(Collections.<RanChangeSet> emptyList()).when(database).getRanChangeSetList();
		closed.close();
		assertHalts("Could not verify retired tables", database);
		try {
			new RecoverEmptySurveillanceSchema().execute(database);
			fail("Recovery must halt");
		}
		catch (CustomChangeException expected) {
			assertTrue(expected.getMessage().contains("Surveillance schema recovery failed"));
		}
	}
}
