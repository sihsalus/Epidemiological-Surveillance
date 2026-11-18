package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;

import java.util.*;

import org.junit.Test;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ServiceContext;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.web.controller.SurveillanceController;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Routes that delegate directly to the service and the lifecycle of the alert stream. */
public class ControllerRoutesTest {
	
	private final SurveillanceService service = mock(SurveillanceService.class);
	
	private final SurveillanceController controller = new SurveillanceController();
	
	{
		controller.setService(service);
	}
	
	private static void publishAlertChange() {
		TransactionSynchronizationManager.initSynchronization();
		try {
			AlertChanges.afterCommit();
			for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations())
				synchronization.afterCommit();
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}
	
	@Test
	public void readAndWriteRoutesDelegateToTheService() {
		int[] thresholds = { 10, 20, 50 };
		when(service.getRiskThresholds()).thenReturn(thresholds);
		assertSame(thresholds, controller.riskThresholds());
		Map<String, Object> catalog = Collections.singletonMap("catalog", "synthetic");
		when(service.getCatalog()).thenReturn(catalog);
		assertSame(catalog, controller.catalog());
		List<SurveillanceCaseResponse> cases = Collections.singletonList(new SurveillanceCaseResponse());
		when(service.listCases("p", "d", "2026-01-01", "2026-01-31", null, null, Boolean.TRUE)).thenReturn(cases);
		assertSame(cases, controller.cases("p", "d", "2026-01-01", "2026-01-31", null, null, Boolean.TRUE).get("results"));
		SurveillanceCaseRequest body = new SurveillanceCaseRequest();
		SurveillanceCaseResponse updated = new SurveillanceCaseResponse();
		when(service.updateDraft("uuid", body)).thenReturn(updated);
		assertSame(updated, controller.updateDraft("uuid", body));
		when(service.getDraft("uuid")).thenReturn(updated);
		assertSame(updated, controller.get("uuid"));
		List<Map<String, Object>> children = Collections.emptyList();
		when(service.addressChildren("districts", "parent")).thenReturn(children);
		assertSame(children, controller.addresses("districts", "parent").get("results"));
		assertEquals(Boolean.TRUE, controller.refresh().get("refreshed"));
		verify(service).refreshCounts();
		Map<String, Object> event = Collections.singletonMap("uuid", "event");
		when(service.saveEvent(event)).thenReturn(event);
		assertSame(event, controller.event(event));
	}
	
	@Test
	public void alertStreamCompletesOnFailedDeliveryAndUnsubscribesOnTimeout() throws Exception {
		org.springframework.web.servlet.mvc.method.annotation.SseEmitter completed = controller.alertStream();
		completed.complete();
		publishAlertChange();
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
		MvcResult result = mvc.perform(get("/rest/v1/sihsalusepidemiologicalsurveillance/alerts/stream"))
		        .andExpect(request().asyncStarted()).andReturn();
		int delivered = result.getResponse().getContentAsString().length();
		MockAsyncContext context = (MockAsyncContext) result.getRequest().getAsyncContext();
		for (AsyncListener listener : new ArrayList<>(context.getListeners()))
			listener.onTimeout(new AsyncEvent(context));
		publishAlertChange();
		assertEquals(delivered, result.getResponse().getContentAsString().length());
		verify(service, times(2)).alerts(null, null, "PENDING");
	}
	
	@Test
	public void routesUseTheRegisteredServiceWhenNoneIsInjected() {
		ServiceContext.destroyInstance();
		ServiceContext services = ServiceContext.getInstance();
		Context.setContext(services);
		try {
			services.setService(SurveillanceService.class, service);
			Map<String, Object> records = Collections.<String, Object> singletonMap("rows", Collections.emptyList());
			when(service.weeklyRecords(2026, 3)).thenReturn(records);
			assertSame(records, new SurveillanceController().weeklyRecords(2026, 3));
		}
		finally {
			ServiceContext.destroyInstance();
		}
	}
}
