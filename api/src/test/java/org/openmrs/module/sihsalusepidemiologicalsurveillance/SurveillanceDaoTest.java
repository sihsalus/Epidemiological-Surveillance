package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.openmrs.api.db.hibernate.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db.HibernateSurveillanceDao;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

public class SurveillanceDaoTest {
	
	@Test
	public void assignsUuidBeforePersisting() {
		DbSessionFactory factory = mock(DbSessionFactory.class);
		DbSession session = mock(DbSession.class);
		when(factory.getCurrentSession()).thenReturn(session);
		HibernateSurveillanceDao dao = new HibernateSurveillanceDao();
		dao.setSessionFactory(factory);
		NotifiableEvent event = new NotifiableEvent();
		dao.save(event);
		assertNotNull(event.getUuid());
		verify(session).saveOrUpdate(event);
		String uuid = event.getUuid();
		dao.save(event);
		assertEquals(uuid, event.getUuid());
	}
}
