package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.time.temporal.WeekFields;
import java.util.*;

import org.junit.Test;
import org.openmrs.User;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.AlertRules.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog;

public class AlertRulesTest {
	
	private final ClinicalCatalog metadata = new ClinicalCatalog();
	
	private Snapshot record(LocalDate date) {
		Snapshot s = new Snapshot();
		s.uuid = UUID.randomUUID().toString();
		s.center = 1;
		s.status = "CONFIRMADO";
		s.origin = "IMPORTADO_NACIONAL";
		s.onset = date;
		return s;
	}
	
	private LocalDate week(int year, int number) {
		WeekFields fields = WeekFields.of(DayOfWeek.valueOf(metadata.firstDayOfWeek), metadata.minimalDaysInFirstWeek);
		return LocalDate.of(year, 7, 1).with(fields.weekOfWeekBasedYear(), number).with(fields.dayOfWeek(), 1);
	}
	
	private List<Snapshot> history(int number, int count) {
		List<Snapshot> records = new ArrayList<>();
		for (int year = 2021; year <= 2025; year++)
			for (int n = 0; n < count; n++)
				records.add(record(week(year, number)));
		return records;
	}
	
	private List<Trigger> evaluate(Snapshot current, Snapshot previous, List<Snapshot> before) {
		List<Snapshot> after = new ArrayList<>(before);
		after.removeIf(s -> s.uuid.equals(current.uuid));
		after.add(current);
		return new AlertRules().evaluate(current, previous, before, after, "INMEDIATA", metadata);
	}
	
	private boolean has(List<Trigger> triggers, String reason) {
		return triggers.stream().anyMatch(t -> t.reason.equals(reason));
	}
	
	@Test
	public void immediateRegistrationAndReplay() {
		Snapshot s = record(week(2026, 5));
		assertTrue(has(evaluate(s, null, new ArrayList<>()), "IMMEDIATE_EVENT"));
		assertTrue(evaluate(s, s, Arrays.asList(s)).isEmpty());
	}
	
	@Test
	public void statusChangeIncludesBothStatesEvenWhenDiscarded() {
		Snapshot old = record(week(2026, 5)), current = record(old.onset);
		current.uuid = old.uuid;
		current.status = "DESCARTADO";
		Trigger trigger = evaluate(current, old, Arrays.asList(old)).get(0);
		assertEquals("STATUS_CHANGED", trigger.reason);
		assertEquals("CONFIRMADO → DESCARTADO", trigger.information);
	}
	
	@Test
	public void pregnancyAndDeathOnlyOnRisingCondition() {
		Snapshot s = record(week(2026, 5));
		s.pregnant = true;
		s.death = true;
		List<Trigger> result = evaluate(s, null, new ArrayList<>());
		assertTrue(has(result, "CONFIRMED_PREGNANCY"));
		assertTrue(has(result, "DEATH"));
		assertTrue(evaluate(s, s, Arrays.asList(s)).isEmpty());
	}
	
	@Test
	public void crossesStrictQ3AndDoesNotRepeatAboveIt() {
		List<Snapshot> records = history(5, 2);
		records.add(record(week(2026, 5)));
		Snapshot second = record(week(2026, 5));
		assertFalse(has(evaluate(second, null, records), "EPIDEMIC_THRESHOLD"));
		records.add(second);
		Snapshot third = record(week(2026, 5));
		assertTrue(has(evaluate(third, null, records), "EPIDEMIC_THRESHOLD"));
		records.add(third);
		assertFalse(has(evaluate(record(week(2026, 5)), null, records), "EPIDEMIC_THRESHOLD"));
	}
	
	@Test
	public void twoWeeksUseTheirOwnQ3IncludingEqualCounts() {
		List<Snapshot> records = history(5, 1);
		records.addAll(history(6, 2));
		for (int i = 0; i < 3; i++)
			records.add(record(week(2026, 5)));
		records.add(record(week(2026, 6)));
		records.add(record(week(2026, 6)));
		assertTrue(has(evaluate(record(week(2026, 6)), null, records), "SUSTAINED_INCREASE"));
	}
	
	@Test
	public void lateRegistrationCompletesFollowingWeek() {
		List<Snapshot> records = history(5, 1);
		records.addAll(history(6, 1));
		records.add(record(week(2026, 5)));
		records.add(record(week(2026, 6)));
		records.add(record(week(2026, 6)));
		assertTrue(has(evaluate(record(week(2026, 5)), null, records), "SUSTAINED_INCREASE"));
	}
	
	@Test
	public void insufficientHistoryAndOtherCentersNeverCreateThreshold() {
		List<Snapshot> records = history(5, 1);
		records.forEach(s -> s.center = 2);
		assertFalse(has(evaluate(record(week(2026, 5)), null, records), "EPIDEMIC_THRESHOLD"));
	}
	
	@Test
	public void eliminatedFocusUsesBeforeImageAndExcludesImportedCases() {
		Snapshot local = record(week(2026, 5));
		local.origin = "AUTOCTONO";
		assertTrue(has(evaluate(local, null, new ArrayList<>()), "AUTOCHTHONOUS_ELIMINATED"));
		Snapshot previous = record(week(2025, 5));
		previous.origin = "AUTOCTONO";
		assertFalse(has(evaluate(local, null, Arrays.asList(previous)), "AUTOCHTHONOUS_ELIMINATED"));
		previous.origin = null;
		assertFalse(has(evaluate(local, null, Arrays.asList(previous)), "AUTOCHTHONOUS_ELIMINATED"));
		local.origin = "IMPORTADO_NACIONAL";
		assertFalse(has(evaluate(local, null, new ArrayList<>()), "AUTOCHTHONOUS_ELIMINATED"));
	}
	
	@Test
	public void allPrivilegesAreRequired() {
		User user = mock(User.class);
		when(user.hasPrivilege(anyString())).thenReturn(true);
		assertTrue(AlertRules.isResponsible(user));
		for (String privilege : AlertRules.PRIVILEGES) {
			when(user.hasPrivilege(privilege)).thenReturn(false);
			assertFalse(AlertRules.isResponsible(user));
			when(user.hasPrivilege(privilege)).thenReturn(true);
		}
	}
}
