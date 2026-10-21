package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.sql.*;
import java.util.UUID;

import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.SurveillanceException;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.SurveillanceCase;

public class NotificationTimeTest {
	
	@Test
	public void preservesNotificationInstantAndReturnsUtcMinusFive() {
		SyntheticFixture f = new SyntheticFixture();
		f.surveillanceRequest.notificationDate = "2026-01-21T23:35:12-05:00";
		assertEquals(f.surveillanceRequest.notificationDate, f.service.createDraft(f.surveillanceRequest).notificationDate);
		ArgumentCaptor<SurveillanceCase> capture = ArgumentCaptor.forClass(SurveillanceCase.class);
		verify(f.dao).save(capture.capture());
		assertEquals(java.time.Instant.parse("2026-01-22T04:35:12Z").toEpochMilli(),
		    capture.getValue().getNotificationDate().getTime());
	}
	
	@Test
	public void notificationFiltersIncludeTheEntireFinalDayInUtcMinusFive() {
		SyntheticFixture f = new SyntheticFixture();
		f.surveillanceRequest.notificationDate = "2026-01-21T23:35:12-05:00";
		f.service.createDraft(f.surveillanceRequest);
		ArgumentCaptor<SurveillanceCase> capture = ArgumentCaptor.forClass(SurveillanceCase.class);
		verify(f.dao).save(capture.capture());
		when(f.dao.surveillanceCases()).thenReturn(java.util.Collections.singletonList(capture.getValue()));
		assertEquals(1, f.service.listCases(null, null, null, null, "2026-01-21", "2026-01-21", null).size());
		assertEquals(0, f.service.listCases(null, null, null, null, "2026-01-22", "2026-01-22", null).size());
	}
	
	@Test
	public void rejectsDateWithoutTimeAndNotificationBeforeEncounter() {
		for (String value : new String[] { "2026-01-21", "2026-01-19T12:00:00-05:00", "2026-02-30T12:00:00-05:00",
		        "2999-01-01T00:00:00-05:00" }) {
			SyntheticFixture f = new SyntheticFixture();
			f.surveillanceRequest.notificationDate = value;
			try {
				f.service.createDraft(f.surveillanceRequest);
				fail();
			}
			catch (SurveillanceException ex) {
				assertEquals(422, ex.getStatus());
			}
		}
	}
	
	@Test
	public void migrationAddsOneHourToEncounterAndPreservesUnnotifiedDrafts() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:notification-" + UUID.randomUUID(), "sa",
		    "")) {
			Statement sql = connection.createStatement();
			sql.execute("create table encounter(encounter_id int primary key, encounter_datetime timestamp)");
			sql.execute("create table surveillance_case(id int primary key, encounter_id int, notification_date date)");
			sql.execute("insert into encounter values(1, TIMESTAMP '2026-12-31 23:30:15')");
			sql.execute("insert into surveillance_case values(1,1,DATE '2026-12-31'),(2,1,null)");
			Liquibase migration = new Liquibase("liquibase-notification-time.xml", new ClassLoaderResourceAccessor(),
			        DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)));
			migration.update("");
			migration.update("");
			ResultSet rows = sql.executeQuery("select notification_date from surveillance_case order by id");
			assertTrue(rows.next());
			assertEquals(Timestamp.valueOf("2027-01-01 00:30:15"), rows.getTimestamp(1));
			assertTrue(rows.next());
			assertNull(rows.getTimestamp(1));
		}
	}
}
