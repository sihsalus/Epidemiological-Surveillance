package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.AlertChanges;
import org.springframework.transaction.support.*;

public class AlertChangesTest {
	
	@Test
	public void onlyCommittedTransactionsNotifyListeners() {
		AtomicInteger calls = new AtomicInteger();
		Runnable listener = calls::incrementAndGet;
		AlertChanges.subscribe(listener);
		TransactionSynchronizationManager.initSynchronization();
		try {
			AlertChanges.afterCommit();
			assertEquals(0, calls.get());
			for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations())
				sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
			assertEquals(0, calls.get());
			for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations())
				sync.afterCommit();
			assertEquals(1, calls.get());
			AlertChanges.unsubscribe(listener);
			for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations())
				sync.afterCommit();
			assertEquals(1, calls.get());
		}
		finally {
			AlertChanges.unsubscribe(listener);
			TransactionSynchronizationManager.clearSynchronization();
		}
	}
}
