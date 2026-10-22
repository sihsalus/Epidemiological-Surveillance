package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.SurveillanceCase;

public class InvestigationFormTest {
	
	@Test
	public void createsAndEditsBothAnswersAndPreservesOmittedUpdates() {
		SyntheticFixture f = new SyntheticFixture();
		assertEquals(Boolean.FALSE, f.service.createDraft(f.surveillanceRequest).investigationForm);
		ArgumentCaptor<SurveillanceCase> capture = ArgumentCaptor.forClass(SurveillanceCase.class);
		verify(f.dao).save(capture.capture());
		SurveillanceCase value = capture.getValue();
		when(f.dao.byUuid(SurveillanceCase.class, value.getUuid())).thenReturn(value);
		f.surveillanceRequest.investigationForm = true;
		assertEquals(Boolean.TRUE, f.service.updateDraft(value.getUuid(), f.surveillanceRequest).investigationForm);
		f.surveillanceRequest.investigationForm = null;
		assertEquals(Boolean.TRUE, f.service.updateDraft(value.getUuid(), f.surveillanceRequest).investigationForm);
		f.surveillanceRequest.investigationForm = false;
		assertEquals(Boolean.FALSE, f.service.updateDraft(value.getUuid(), f.surveillanceRequest).investigationForm);
	}
	
	@Test
	public void migrationSetsEveryExistingCaseToYesOnlyOnce() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:form-" + UUID.randomUUID(), "sa", "");
		        Statement sql = connection.createStatement()) {
			sql.execute("create table surveillance_case(id int primary key, voided boolean)");
			sql.execute("insert into surveillance_case values(1, false), (2, true)");
			Liquibase migration = new Liquibase("liquibase-case-investigation-form.xml", new ClassLoaderResourceAccessor(),
			        DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)));
			migration.update("");
			try (ResultSet rows = sql.executeQuery("select count(*) from surveillance_case where investigation_form=true")) {
				assertTrue(rows.next());
				assertEquals(2, rows.getInt(1));
			}
			sql.execute("update surveillance_case set investigation_form=false where id=1");
			sql.execute("insert into surveillance_case(id, voided) values(3, false)");
			connection.commit();
			migration.update("");
			try (ResultSet rows = sql
			        .executeQuery("select count(*) from surveillance_case where investigation_form=false")) {
				assertTrue(rows.next());
				assertEquals(2, rows.getInt(1));
			}
		}
	}
}
