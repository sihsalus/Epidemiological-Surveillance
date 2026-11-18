package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.*;

import org.junit.Test;
import org.openmrs.CodedOrFreeText;
import org.openmrs.Concept;
import org.openmrs.Diagnosis;
import org.openmrs.Encounter;
import org.openmrs.User;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Validation boundaries and defensive paths of the pure calculators and settings parsers. */
public class CalculatorBoundaryTest {
	
	private final ClinicalCatalog metadata = new ClinicalCatalog();
	
	private final EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(metadata);
	
	private static void assertCode(String code, Runnable action) {
		try {
			action.run();
			fail("Expected " + code);
		}
		catch (SurveillanceException ex) {
			assertEquals(code, ex.getCode());
		}
	}
	
	/** A value whose serialization fails, to verify that write failures are reported safely. */
	public static class UnserializableAgeGroup extends AgeGroup {
		
		public UnserializableAgeGroup() {
			super("INFANTS", 0, null);
		}
		
		public String getBroken() {
			throw new IllegalStateException("synthetic serialization failure");
		}
	}
	
	public static class UnserializableRule extends OutbreakRule {
		
		public String getBroken() {
			throw new IllegalStateException("synthetic serialization failure");
		}
	}
	
	@Test
	public void ageGroupConfigurationRejectsMalformedJsonWrongBoundCountAndUnwritableValues() {
		assertCode("INVALID_AGE_GROUPS", () -> DemographicCalculator.groups("[{\"key\":"));
		assertCode("INVALID_AGE_GROUPS", () -> DemographicCalculator.groups("0,2,12"));
		assertCode("INVALID_AGE_GROUPS",
		    () -> DemographicCalculator.validateGroups(Collections.<AgeGroup> singletonList(new UnserializableAgeGroup())));
	}
	
	@Test
	public void demographicCalculationRejectsInvalidDimensionsAndOversizedTables() {
		DemographicCalculator calculator = new DemographicCalculator();
		List<AgeGroup> groups = DemographicCalculator.groups(null);
		LocalDate from = LocalDate.of(2024, 1, 1), to = LocalDate.of(2024, 12, 31);
		assertCode("INVALID_DEMOGRAPHIC_DIMENSIONS", () -> calculator.calculate(Collections.emptyList(), "age", "age", "mes",
		    from, to, groups, Collections.emptyMap(), calendar));
		List<DemographicCalculator.Record> records = new ArrayList<>();
		for (int age = 0; age < 300; age++) {
			DemographicCalculator.Record record = new DemographicCalculator.Record();
			record.age = age;
			record.date = from;
			records.add(record);
		}
		assertCode("DEMOGRAPHIC_TABLE_TOO_LARGE",
		    () -> calculator.calculate(records, "age", "period", "dia", from, to, groups, Collections.emptyMap(), calendar));
	}
	
	@Test
	public void ethnicityAxisIsSortedAlphabeticallyIgnoringCaseWithUnknownLast() {
		List<DemographicCalculator.Record> records = new ArrayList<>();
		for (String ethnicity : Arrays.asList("Shipibo", "awajún", null)) {
			DemographicCalculator.Record record = new DemographicCalculator.Record();
			record.ethnicity = ethnicity;
			record.sex = "F";
			record.date = LocalDate.of(2026, 1, 5);
			records.add(record);
		}
		DemographicReport report = new DemographicCalculator().calculate(records, "ethnicity", "sex", "mes",
		    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), DemographicCalculator.groups(null), Collections.emptyMap(),
		    calendar);
		assertEquals("awajún", report.rows.get(0).key);
		assertEquals("Shipibo", report.rows.get(1).key);
		assertEquals("UNKNOWN", report.rows.get(2).key);
	}
	
	@Test
	public void unsupportedDimensionIsRejectedByTheValueResolver() throws Exception {
		Method value = DemographicCalculator.class.getDeclaredMethod("value", DemographicCalculator.Record.class,
		    String.class, List.class, String.class, EpidemiologicalCalendar.class);
		value.setAccessible(true);
		try {
			value.invoke(new DemographicCalculator(), new DemographicCalculator.Record(), "unsupported",
			    DemographicCalculator.groups(null), "mes", calendar);
			fail();
		}
		catch (InvocationTargetException ex) {
			assertEquals("INVALID_DEMOGRAPHIC_DIMENSIONS", ((SurveillanceException) ex.getCause()).getCode());
		}
	}
	
	@Test
	public void quantilesAndCalendarRejectEmptySamplesAndUnknownPeriods() {
		try {
			new EndemicChannel().quantile(Collections.<Integer> emptyList(), 0.5);
			fail();
		}
		catch (IllegalArgumentException expected) {}
		LocalDate day = LocalDate.of(2026, 1, 5);
		assertCode("INVALID_PERIOD", () -> calendar.number(day, "decada"));
		assertCode("INVALID_PERIOD", () -> calendar.start(day, "decada"));
		assertCode("INVALID_PERIOD", () -> calendar.next(day, "decada"));
	}
	
	@Test
	public void geographicSettingsRejectMalformedThresholdsAndCoordinateFiles() {
		assertCode("INVALID_RISK_LEVELS", () -> GeographicCalculator.thresholds("10,20"));
		assertCode("INVALID_COORDINATES_CSV", () -> GeographicCalculator.parseCsv(null));
		assertCode("INVALID_COORDINATES_CSV", () -> GeographicCalculator.parseCsv("code,lat,lon\n1,1,1"));
		assertCode("INVALID_COORDINATES_CSV",
		    () -> GeographicCalculator.parseCsv("address_hierarchy_entry_id,latitude,longitude\n1,-3.7"));
		assertCode("INVALID_COORDINATES_CSV",
		    () -> GeographicCalculator.parseCsv("address_hierarchy_entry_id,latitude,longitude\n\n   \n"));
	}
	
	@Test
	public void geographicReportFlagsRecentUnknownOriginsAndExcludesCasesOutsideTheWindow() {
		List<GeographicCalculator.Record> records = new ArrayList<>();
		for (String[] row : new String[][] { { "2026-03-01", "UNKNOWN" }, { "2025-06-01", "AUTOCTONO" },
		        { "2026-09-01", "AUTOCTONO" } }) {
			GeographicCalculator.Record record = new GeographicCalculator.Record();
			record.center = 7;
			record.date = LocalDate.parse(row[0]);
			record.origin = row[1];
			records.add(record);
		}
		GeographicReport report = new GeographicCalculator().calculate(records, LocalDate.of(2026, 1, 1),
		    LocalDate.of(2026, 6, 30), GeographicCalculator.thresholds(null));
		assertEquals(1, report.total);
		assertEquals(1, report.foci.size());
		assertEquals(Integer.valueOf(2026), report.foci.get(0).lastAutochthonousYear);
	}
	
	@Test
	public void timelinessIsFalseForAnUnknownPeriodicity() {
		SurveillanceCase value = new SurveillanceCase();
		Encounter encounter = new Encounter(1);
		encounter.setEncounterDatetime(calendar.date(LocalDate.of(2026, 1, 5)));
		value.setEncounter(encounter);
		value.setNotificationDate(calendar.date(LocalDate.of(2026, 1, 6)));
		assertFalse(new IndicatorCalculator().timely(value, "MENSUAL", metadata, new int[] { 1, 12, 0 }));
	}
	
	@Test
	public void outbreakRulesRejectMalformedStoredValuesAndEveryInvalidRuleShape() {
		assertNotNull(new OutbreakRuleSettings());
		assertCode("INVALID_OUTBREAK_RULES", () -> OutbreakRuleSettings.read("not-json"));
		assertCode("INVALID_OUTBREAK_RULES", () -> OutbreakRuleSettings.validate(null));
		List<OutbreakRule> duplicate = OutbreakRuleSettings.defaults();
		duplicate.get(1).id = duplicate.get(0).id;
		assertCode("INVALID_OUTBREAK_RULES", () -> OutbreakRuleSettings.validate(duplicate));
		List<OutbreakRule> focus = OutbreakRuleSettings.defaults();
		focus.get(2).origin = "UNKNOWN";
		assertCode("INVALID_OUTBREAK_RULES", () -> OutbreakRuleSettings.validate(focus));
		List<OutbreakRule> threshold = OutbreakRuleSettings.defaults();
		threshold.get(0).weeks = 2;
		assertCode("INVALID_OUTBREAK_RULES", () -> OutbreakRuleSettings.validate(threshold));
		List<OutbreakRule> unknown = OutbreakRuleSettings.defaults();
		unknown.get(0).type = "UNKNOWN";
		assertCode("INVALID_OUTBREAK_RULES", () -> OutbreakRuleSettings.validate(unknown));
		UnserializableRule rule = new UnserializableRule();
		rule.id = "synthetic";
		rule.type = "THRESHOLD";
		rule.percentile = 50.0;
		rule.weeks = 1;
		assertCode("INVALID_OUTBREAK_RULES",
		    () -> OutbreakRuleSettings.validate(Collections.<OutbreakRule> singletonList(rule)));
		try {
			new OutbreakRule().setWeeks(1.5);
			fail();
		}
		catch (IllegalArgumentException expected) {
			assertEquals("INVALID_OUTBREAK_RULES", expected.getMessage());
		}
	}
	
	@Test
	public void aggregationSkipsCasesWithoutOnsetDistrictOrCodedDiagnosis() {
		NotifiableEvent event = new NotifiableEvent();
		Concept concept = new Concept(1);
		event.setConcept(concept);
		SurveillanceCase withoutOnset = aggregateCase(concept, 100);
		withoutOnset.setOnsetDate(null);
		SurveillanceCase withoutDistrict = aggregateCase(concept, 200);
		SurveillanceCase withoutDiagnosis = aggregateCase(concept, 100);
		withoutDiagnosis.setDiagnosis(null);
		List<PeriodCaseCount> counts = new ReportCalculator().aggregateSurveillanceCases(event,
		    Arrays.asList(withoutOnset, withoutDistrict, withoutDiagnosis), Collections.singletonMap(100, 10), metadata,
		    new User(1), LocalDate.of(2026, 1, 20));
		assertTrue(counts.isEmpty());
	}
	
	private SurveillanceCase aggregateCase(Concept concept, int address) {
		Diagnosis diagnosis = new Diagnosis();
		diagnosis.setDiagnosis(new CodedOrFreeText(concept, null, null));
		SurveillanceCase value = new SurveillanceCase();
		value.setDiagnosis(diagnosis);
		value.setDiagnosisType("CONFIRMADO");
		value.setInfectionAddress(address);
		value.setOnsetDate(calendar.date(LocalDate.of(2026, 1, 19)));
		return value;
	}
	
	private PeriodCaseCount historical(LocalDate day, String period, int cases) {
		NotifiableEvent event = new NotifiableEvent();
		event.setUuid("synthetic-event");
		PeriodCaseCount count = new PeriodCaseCount();
		count.setEvent(event);
		count.setPeriodType(period.toUpperCase(Locale.ENGLISH));
		count.setYear(calendar.year(day, period));
		count.setPeriodNumber(calendar.number(day, period));
		count.setStartDate(calendar.date(calendar.start(day, period)));
		count.setAddressHierarchyEntryId(100);
		count.setZoneLevel("CENTRO_POBLADO");
		count.setDiagnosisType("CONFIRMADO");
		count.setCaseCount(cases);
		return count;
	}
	
	@Test
	public void historicalLeapDaysAndMissingWeek53AreNotProjectedOntoOtherYears() {
		SurveillanceReport leap = new AggregateReportCalculator().calculate("synthetic-event", LocalDate.parse("2024-02-29"),
		    LocalDate.parse("2024-02-29"), "dia", "CENTRO_POBLADO", null, "CONFIRMADO", Collections.emptyList(),
		    Collections.singletonList(historical(LocalDate.parse("2023-03-01"), "dia", 4)), metadata);
		assertFalse(leap.channel.get(0).historicalCases.containsKey(2023));
		LocalDate week53 = null;
		for (LocalDate day = LocalDate.of(2015, 12, 20); week53 == null; day = day.plusDays(1))
			if (calendar.number(day, "semana") == 53)
				week53 = calendar.start(day, "semana");
		SurveillanceReport weekly = new AggregateReportCalculator().calculate("synthetic-event", week53, week53, "semana",
		    "CENTRO_POBLADO", null, "CONFIRMADO", Collections.emptyList(), Collections.emptyList(), metadata);
		assertTrue(weekly.channel.get(0).historicalCases.size() < weekly.historicalYears.size());
	}
	
	@Test
	public void failingAlertListenerIsDroppedAfterCommit() {
		int[] calls = new int[2];
		Runnable failing = () -> {
			calls[0]++;
			throw new IllegalStateException("synthetic listener failure");
		};
		Runnable healthy = () -> calls[1]++;
		AlertChanges.subscribe(failing);
		AlertChanges.subscribe(healthy);
		try {
			for (int round = 0; round < 2; round++) {
				TransactionSynchronizationManager.initSynchronization();
				try {
					AlertChanges.afterCommit();
					for (TransactionSynchronization synchronization : TransactionSynchronizationManager
					        .getSynchronizations())
						synchronization.afterCommit();
				}
				finally {
					TransactionSynchronizationManager.clearSynchronization();
				}
			}
			assertEquals(1, calls[0]);
			assertEquals(2, calls[1]);
		}
		finally {
			AlertChanges.unsubscribe(failing);
			AlertChanges.unsubscribe(healthy);
		}
	}
	
	@Test
	public void modelAccessorsKeepAssignedValues() {
		assertNotNull(new SurveillanceActivator());
		PeriodCaseCount count = new PeriodCaseCount();
		Date date = new Date(0);
		count.setDate(date);
		assertSame(date, count.getDate());
		SurveillanceAlert alert = new SurveillanceAlert();
		alert.setId(9);
		assertEquals(Integer.valueOf(9), alert.getId());
		NotifiableEvent event = new NotifiableEvent();
		event.setActive(true);
		assertTrue(event.isActiveOn(date));
	}
}
