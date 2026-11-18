package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.hibernate.Criteria;
import org.hibernate.SQLQuery;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.Diagnosis;
import org.openmrs.User;
import org.openmrs.api.db.hibernate.DbSession;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db.HibernateSurveillanceDao;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

/** Query construction and result mapping of the DAO, against a synthetic Hibernate session. */
public class DaoQueryTest {
	
	private final DbSession session = mock(DbSession.class);
	
	private final SQLQuery sql = mock(SQLQuery.class, RETURNS_SELF);
	
	private final Criteria criteria = mock(Criteria.class, RETURNS_SELF);
	
	private final HibernateSurveillanceDao dao = new HibernateSurveillanceDao();
	
	@Before
	public void syntheticSession() {
		DbSessionFactory factory = mock(DbSessionFactory.class);
		when(factory.getCurrentSession()).thenReturn(session);
		when(session.createSQLQuery(anyString())).thenReturn(sql);
		when(session.createCriteria(any(Class.class))).thenReturn(criteria);
		when(sql.uniqueResult()).thenReturn(null);
		when(sql.list()).thenReturn(Collections.emptyList());
		when(criteria.uniqueResult()).thenReturn(null);
		when(criteria.list()).thenReturn(Collections.emptyList());
		dao.setSessionFactory(factory);
	}
	
	@Test
	public void healthcheckProbesEveryComponentTable() {
		assertEquals(12, dao.verifiedTableCount());
		verify(session, times(12)).createSQLQuery(anyString());
	}
	
	@Test
	public void alertAttendantIsResolvedByUuidOrLegacyUsername() {
		assertNull(dao.alertAttendant(null));
		User legacy = new User(3);
		when(criteria.uniqueResult()).thenReturn(null, legacy);
		assertSame(legacy, dao.alertAttendant("legacy-user"));
		User current = new User(4);
		when(criteria.uniqueResult()).thenReturn(current);
		assertSame(current, dao.alertAttendant("synthetic-uuid"));
	}
	
	@Test
	public void alertsAreSavedListedAndLocked() {
		SurveillanceAlert alert = new SurveillanceAlert();
		alert.setUuid(null);
		assertSame(alert, dao.saveAlert(alert));
		assertNotNull(alert.getUuid());
		verify(session).saveOrUpdate(alert);
		assertTrue(dao.alerts().isEmpty());
		dao.lockAlerts();
		Concept concept = new Concept(8);
		dao.lockEventConcept(concept);
		verify(sql).setInteger("id", 8);
	}
	
	@Test
	public void notificationSettingsAreReadAndWritten() {
		assertNull(dao.notificationSettings());
		when(sql.uniqueResult()).thenReturn(new Object[] { 6, 20, 30 });
		assertArrayEquals(new int[] { 6, 20, 30 }, dao.notificationSettings());
		dao.saveNotificationSettings(1, 12, 0);
		verify(sql).setInteger("day", 1);
		verify(sql).executeUpdate();
	}
	
	@Test
	public void catalogsCasesAndEventsAreQueriedThroughHibernate() {
		assertTrue(dao.populatedCenterCodes().isEmpty());
		assertTrue(dao.events().isEmpty());
		assertTrue(dao.surveillanceCases().isEmpty());
		NotifiableEvent event = new NotifiableEvent();
		when(criteria.uniqueResult()).thenReturn(event);
		assertSame(event, dao.eventByConcept(new Concept(1)));
		SurveillanceCase value = new SurveillanceCase();
		when(criteria.uniqueResult()).thenReturn(value);
		assertSame(value, dao.caseByDiagnosis(new Diagnosis()));
	}
	
	@Test
	public void addressLookupsMapMissingRowsToNull() {
		assertNull(dao.populatedCenterId("synthetic"));
		assertNull(dao.districtIdForPopulatedCenter(1));
		assertNull(dao.addressUuid(1));
		assertNull(dao.addressCode(null));
		assertNull(dao.addressId("synthetic"));
		assertNull(dao.addressId("synthetic", "CITY_VILLAGE"));
		when(sql.uniqueResult()).thenReturn(42L);
		assertEquals(Integer.valueOf(42), dao.populatedCenterId("synthetic"));
		assertEquals(Integer.valueOf(42), dao.districtIdForPopulatedCenter(1));
		assertEquals("42", dao.addressUuid(1));
		assertEquals(Integer.valueOf(42), dao.addressId("synthetic"));
		assertEquals(Integer.valueOf(42), dao.addressId("synthetic", "CITY_VILLAGE"));
	}
	
	@Test
	public void ancestorSearchStopsOnCyclesAndChildrenAreFilteredByParent() {
		when(sql.uniqueResult()).thenReturn(new Object[] { 7, "CITY_VILLAGE" });
		assertNull(dao.ancestorIdForPopulatedCenter(7, "ADDRESS_1"));
		List<Object[]> children = Collections.singletonList(new Object[] { "uuid", "name" });
		when(sql.list()).thenReturn((List) children);
		assertSame(children, dao.addressChildren("CITY_VILLAGE", "parent-uuid"));
		verify(sql).setString("parent", "parent-uuid");
		assertSame(children, dao.addressChildren("ADDRESS_1", null));
	}
}
