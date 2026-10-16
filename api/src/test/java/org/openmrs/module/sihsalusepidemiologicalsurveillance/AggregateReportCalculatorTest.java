package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;

import java.time.LocalDate;
import java.util.*;

import org.junit.Test;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

public class AggregateReportCalculatorTest {
	
	private final ClinicalCatalog metadata = new ClinicalCatalog();
	
	private final AggregateReportCalculator calculator = new AggregateReportCalculator();
	
	private PeriodCaseCount count(String date, String period, int address, String level, String diagnosis, int cases) {
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(metadata);
		LocalDate day = LocalDate.parse(date);
		NotifiableEvent event = new NotifiableEvent();
		event.setUuid("synthetic-event");
		PeriodCaseCount count = new PeriodCaseCount();
		count.setEvent(event);
		count.setPeriodType(period.toUpperCase(Locale.ENGLISH));
		count.setYear(calendar.year(day, period));
		count.setPeriodNumber(calendar.number(day, period));
		count.setStartDate(calendar.date(calendar.start(day, period)));
		count.setAddressHierarchyEntryId(address);
		count.setZoneLevel(level);
		count.setDiagnosisType(diagnosis);
		count.setCaseCount(cases);
		return count;
	}
	
	@Test
	public void sumsSelectedDiagnosesWithoutDoubleCountingGeographicalLevels() {
		List<PeriodCaseCount> counts = Arrays.asList(count("2026-03-01", "dia", 100, "CENTRO_POBLADO", "CONFIRMADO", 2),
		    count("2026-03-01", "dia", 100, "CENTRO_POBLADO", "PROBABLE", 3),
		    count("2026-03-01", "dia", 101, "CENTRO_POBLADO", "CONFIRMADO", 4),
		    count("2026-03-01", "dia", 10, "DISTRITO", "CONFIRMADO", 6),
		    count("2026-03-01", "dia", 10, "DISTRITO", "PROBABLE", 3),
		    count("2026-03-01", "dia", 100, "CENTRO_POBLADO", "DESCARTADO", 50));
		for (String level : Arrays.asList("CENTRO_POBLADO", "DISTRITO")) {
			for (String diagnosis : Arrays.asList("CONFIRMADO", "PROBABLE", "TODOS")) {
				SurveillanceReport report = calculator.calculate("synthetic-event", LocalDate.parse("2026-03-01"),
				    LocalDate.parse("2026-03-02"), "dia", level, null, diagnosis, counts, Collections.emptyList(), metadata);
				assertEquals("CONFIRMADO".equals(diagnosis) ? 6 : "PROBABLE".equals(diagnosis) ? 3 : 9, report.total);
				assertEquals(0, report.curve.get(1).cases);
				assertTrue(report.demographics.isEmpty());
			}
		}
		SurveillanceReport selected = calculator.calculate("synthetic-event", LocalDate.parse("2026-03-01"),
		    LocalDate.parse("2026-03-02"), "dia", "CENTRO_POBLADO", 100, "TODOS", counts, Collections.emptyList(), metadata);
		assertEquals(5, selected.total);
	}
	
	@Test
	public void sumsHistoryPerYearBeforeQuartilesAndMatchesLeapYearDates() {
		List<PeriodCaseCount> history = new ArrayList<>();
		for (int year = 2021; year <= 2025; year++) {
			history.add(count(year + "-03-01", "dia", 100, "CENTRO_POBLADO", "CONFIRMADO", 2));
			history.add(count(year + "-03-01", "dia", 101, "CENTRO_POBLADO", "PROBABLE", 3));
			history.add(count(year + "-03-01", "dia", 10, "DISTRITO", "CONFIRMADO", 100));
		}
		SurveillanceReport report = calculator.calculate("synthetic-event", LocalDate.parse("2026-03-01"),
		    LocalDate.parse("2026-03-01"), "dia", "CENTRO_POBLADO", null, "TODOS", Collections.emptyList(), history,
		    metadata);
		assertEquals(5, report.channel.get(0).sampleSize);
		assertEquals(5d, report.channel.get(0).q3, 0d);
		assertEquals(Arrays.asList(2025, 2024, 2023, 2022, 2021), report.historicalYears);
		assertEquals(Integer.valueOf(5), report.channel.get(0).historicalCases.get(2024));
		metadata.historicalYears = 2;
		metadata.minimumHistoricalYears = 2;
		SurveillanceReport shorter = calculator.calculate("synthetic-event", LocalDate.parse("2026-03-01"),
		    LocalDate.parse("2026-03-01"), "dia", "CENTRO_POBLADO", null, "TODOS", Collections.emptyList(), history,
		    metadata);
		assertEquals(Arrays.asList(2025, 2024), shorter.historicalYears);
		assertEquals(2, shorter.channel.get(0).sampleSize);
		assertEquals(5d, shorter.channel.get(0).q3, 0d);
	}
	
	@Test
	public void boundsCurrentCountsByInclusiveDatesAndMarksPartialPeriods() {
		List<PeriodCaseCount> daily = Arrays.asList(count("2026-03-01", "dia", 100, "CENTRO_POBLADO", "CONFIRMADO", 20),
		    count("2026-03-02", "dia", 100, "CENTRO_POBLADO", "CONFIRMADO", 2),
		    count("2026-03-03", "dia", 100, "CENTRO_POBLADO", "CONFIRMADO", 3),
		    count("2026-03-04", "dia", 100, "CENTRO_POBLADO", "CONFIRMADO", 40));
		SurveillanceReport report = calculator.calculate("synthetic-event", LocalDate.parse("2026-03-02"),
		    LocalDate.parse("2026-03-03"), "semana", "CENTRO_POBLADO", null, "CONFIRMADO", daily, Collections.emptyList(),
		    metadata);
		assertEquals(5, report.total);
		assertEquals(5, report.channel.get(0).cases);
		assertEquals("PARTIAL_PERIOD", report.channel.get(0).zone);
		assertTrue(report.warnings.contains("PARTIAL_PERIOD"));
		assertEquals(0, report.channel.get(0).sampleSize);
		assertNull(report.channel.get(0).q3);
	}
	
	@Test
	public void emptyHistoricalPeriodsDisplayZeroWithoutIncreasingStatisticalSample() {
		SurveillanceReport report = calculator.calculate("synthetic-event", LocalDate.parse("2026-03-01"),
		    LocalDate.parse("2026-03-01"), "dia", "CENTRO_POBLADO", null, "CONFIRMADO", Collections.emptyList(),
		    Collections.singletonList(count("2025-03-01", "dia", 100, "CENTRO_POBLADO", "CONFIRMADO", 0)), metadata);
		assertEquals(1, report.channel.get(0).sampleSize);
		assertEquals(Integer.valueOf(0), report.channel.get(0).historicalCases.get(2025));
		assertEquals(Integer.valueOf(0), report.channel.get(0).historicalCases.get(2024));
		assertTrue(report.warnings.contains("INSUFFICIENT_HISTORY"));
	}
	
	@Test
	public void nonExistentHistoricalCalendarPeriodsRemainAbsent() {
		SurveillanceReport leapDay = calculator.calculate("synthetic-event", LocalDate.parse("2024-02-29"),
		    LocalDate.parse("2024-02-29"), "dia", "CENTRO_POBLADO", null, "CONFIRMADO", Collections.emptyList(),
		    Collections.emptyList(), metadata);
		assertFalse(leapDay.channel.get(0).historicalCases.containsKey(2023));
		assertEquals(Integer.valueOf(0), leapDay.channel.get(0).historicalCases.get(2020));
	}
}
