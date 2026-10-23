package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * No patient data crosses this in-process signal. Clients fetch through the authorized REST
 * endpoint.
 */
public final class AlertChanges {
	
	private static final CopyOnWriteArraySet<Runnable> listeners = new CopyOnWriteArraySet<>();
	
	public static void subscribe(Runnable listener) {
		listeners.add(listener);
	}
	
	public static void unsubscribe(Runnable listener) {
		listeners.remove(listener);
	}
	
	public static void afterCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
				
				@Override
				public void afterCommit() {
					publish();
				}
			});
		}
	}
	
	private static void publish() {
		for (Runnable listener : listeners) {
			try {
				listener.run();
			}
			catch (RuntimeException failure) {
				listeners.remove(listener);
			}
		}
	}
	
	private AlertChanges() {
	}
}
