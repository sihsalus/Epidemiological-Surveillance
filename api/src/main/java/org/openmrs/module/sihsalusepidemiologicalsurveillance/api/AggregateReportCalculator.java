package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.time.*;
import java.util.*;

import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.PeriodCaseCount;

/** Indicators from persisted counts only; no patient or encounter data is needed. */
public class AggregateReportCalculator {
	
	public SurveillanceReport calculate(String eventUuid, LocalDate from, LocalDate to, String period, String zoneLevel,
	        Integer addressId, String diagnosisType, List<PeriodCaseCount> dailyCounts,
	        List<PeriodCaseCount> historicalCounts, ClinicalCatalog metadata) {
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(metadata);
		SurveillanceReport report = new SurveillanceReport();
		report.generatedAt = Instant.now().toString();
		report.eventUuid = eventUuid;
		report.from = from.toString();
		report.to = to.toString();
		report.period = period;
		report.zoneLevel = zoneLevel;
		report.diagnosisType = diagnosisType;
		report.population = diagnosisType;
		for (int year = from.getYear() - 1; year >= from.getYear() - metadata.historicalYears; year--)
			report.historicalYears.add(year);
		Map<LocalDate, Integer> daily = new TreeMap<>();
		Map<LocalDate, Integer> periods = new TreeMap<>();
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1))
			daily.put(date, 0);
		for (LocalDate date = calendar.start(from, period); !date.isAfter(to); date = calendar.next(date, period))
			periods.put(date, 0);
		for (PeriodCaseCount count : dailyCounts) {
			if (!matches(count, eventUuid, "dia", zoneLevel, addressId, diagnosisType))
				continue;
			LocalDate date = calendar.local(count.getStartDate());
			if (daily.containsKey(date)) {
				daily.merge(date, count.getCaseCount(), Integer::sum);
				periods.merge(calendar.start(date, period), count.getCaseCount(), Integer::sum);
				report.total += count.getCaseCount();
			}
		}
		for (Map.Entry<LocalDate, Integer> entry : daily.entrySet()) {
			SurveillanceReport.Point point = new SurveillanceReport.Point();
			point.date = entry.getKey().toString();
			point.cases = entry.getValue();
			report.curve.add(point);
		}
		for (Map.Entry<LocalDate, Integer> entry : periods.entrySet()) {
			LocalDate date = entry.getKey();
			SurveillanceReport.ChannelPoint point = new SurveillanceReport.ChannelPoint();
			point.date = date.toString();
			point.year = calendar.year(date, period);
			point.number = calendar.number(date, period);
			point.cases = entry.getValue();
			Map<Integer, Integer> years = new TreeMap<>();
			for (PeriodCaseCount count : historicalCounts) {
				if (!matches(count, eventUuid, period, zoneLevel, addressId, diagnosisType)
				        || !report.historicalYears.contains(count.getYear()))
					continue;
				int number = point.number;
				if ("dia".equals(period)) {
					if (!MonthDay.from(date).isValidYear(count.getYear()))
						continue;
					number = date.withYear(count.getYear()).getDayOfYear();
				}
				// Sum zones and diagnosis types BEFORE calculating the historical distribution.
				if (count.getPeriodNumber() == number)
					years.merge(count.getYear(), count.getCaseCount(), Integer::sum);
			}
			// Comparison lines show recorded case totals, including zero when the sparse aggregate has no row.
			// Keep the statistical sample separate: an absent row does not prove sufficient surveillance history.
			for (Integer year : report.historicalYears) {
				if ("dia".equals(period) && !MonthDay.from(date).isValidYear(year))
					continue;
				if ("semana".equals(period)) {
					java.time.temporal.WeekFields weeks = java.time.temporal.WeekFields
					        .of(DayOfWeek.valueOf(metadata.firstDayOfWeek), metadata.minimalDaysInFirstWeek);
					LocalDate historical = LocalDate.of(year, 7, 1).with(weeks.weekOfWeekBasedYear(), point.number);
					if (calendar.year(historical, period) != year || calendar.number(historical, period) != point.number)
						continue;
				}
				point.historicalCases.put(year, years.getOrDefault(year, 0));
			}
			EndemicChannel.Thresholds thresholds = new EndemicChannel().evaluate(new ArrayList<>(years.values()),
			    point.cases, metadata.minimumHistoricalYears);
			point.q1 = thresholds.q1;
			point.q2 = thresholds.q2;
			point.q3 = thresholds.q3;
			point.sampleSize = thresholds.sampleSize;
			point.zone = thresholds.zone;
			if (date.isBefore(from) || calendar.next(date, period).isAfter(to.plusDays(1)))
				point.zone = "PARTIAL_PERIOD";
			report.channel.add(point);
		}
		for (String warning : Arrays.asList("INSUFFICIENT_HISTORY", "PARTIAL_PERIOD"))
			if (report.channel.stream().anyMatch(point -> warning.equals(point.zone)))
				report.warnings.add(warning);
		return report;
	}
	
	private boolean matches(PeriodCaseCount count, String eventUuid, String period, String zoneLevel, Integer addressId,
	        String diagnosisType) {
		return count.getEvent() != null && eventUuid.equals(count.getEvent().getUuid())
		        && period.equalsIgnoreCase(count.getPeriodType()) && zoneLevel.equals(count.getZoneLevel())
		        && (addressId == null || addressId.equals(count.getAddressHierarchyEntryId()))
		        && ("CONFIRMADO".equals(count.getDiagnosisType()) || "PROBABLE".equals(count.getDiagnosisType()))
		        && ("TODOS".equals(diagnosisType) || diagnosisType.equals(count.getDiagnosisType()));
	}
}
