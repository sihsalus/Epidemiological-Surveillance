package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.time.LocalDate;
import java.util.*;

import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

public class OutbreakEngine {
	
	/** Calculated compatibility value; not a persistent entity. */
	public static class FocusStatus {

		public String classification;

		public Boolean receptive;
	}

	public void evaluate(CaseRecord current, FocusStatus focus, List<OutbreakRule> rules, List<CaseRecord> recent,
	        List<PeriodCaseCount> history, ClinicalCatalog m, CaseResult result) {
		if ("DISCARDED".equals(current.status))
			return;
		boolean eliminated = focus != null && "ELIMINATED".equals(focus.classification);
		if ("SEVERE".equals(current.severity))
			result.immediateAlerts.add("SEVERE_CASE");
		if (current.deceased)
			result.immediateAlerts.add("DEATH");
		if (eliminated)
			result.immediateAlerts.add("ELIMINATED_FOCUS");
		if (focus != null && Boolean.FALSE.equals(focus.receptive))
			result.immediateAlerts.add("NON_RECEPTIVE_FOCUS");
		if (Boolean.TRUE.equals(current.pregnant) && "CONFIRMED".equals(current.status))
			result.immediateAlerts.add("CONFIRMED_PREGNANCY");
		if (focus == null)
			result.warnings.add("FOCUS_UNKNOWN");
		if (current.pregnant == null)
			result.warnings.add("PREGNANCY_UNKNOWN");
		if (!"CONFIRMED".equals(current.status))
			return;
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(m);
		LocalDate week = calendar.start(current.onset, "semana");
		EndemicChannel channel = new EndemicChannel();
		for (OutbreakRule rule : rules) {
			if ("FOCUS_CASES".equals(rule.type)) {
				if (focus != null && rule.focus.equals(focus.classification) && rule.origin.equals(origin(current.origin))
				        && countOrigin(recent, current.eventUuid, week, week.plusWeeks(1), rule.origin) > rule.minimumCases)
					result.outbreakAlerts.add(eliminated && "AUTOCTONO".equals(rule.origin) ? "AUTOCHTHONOUS_ELIMINATED"
					        : "FOCUS_CASES");
			} else if ("THRESHOLD".equals(rule.type)) {
				List<Integer> sample = ReportCalculator.history(history, calendar.year(week, "semana"),
				    calendar.number(week, "semana"), m);
				int count = count(recent, current.eventUuid, week, week.plusWeeks(1));
				Double threshold = sample.size() < m.minimumHistoricalYears ? null : channel.quantile(sample,
				    rule.percentile / 100.0);
				if (threshold == null)
					warnHistory(result);
				else if (count > threshold && count - 1 <= threshold)
					result.outbreakAlerts.add("EPIDEMIC_THRESHOLD");
			} else if ("CONSECUTIVE".equals(rule.type)) {
				int window = rule.weeks;
				if (window < 1 || window > 53)
					throw new SurveillanceException(503, "INVALID_OUTBREAK_RULE");
				boolean sustained = true;
				for (int offset = window - 1; offset >= 0; offset--) {
					LocalDate date = week.minusWeeks(offset);
					int count = count(recent, current.eventUuid, date, date.plusWeeks(1));
					List<Integer> sample = ReportCalculator.history(history, calendar.year(date, "semana"),
					    calendar.number(date, "semana"), m);
					Double threshold = sample.size() < m.minimumHistoricalYears ? null : channel.quantile(sample,
					    rule.percentile / 100.0);
					if (threshold == null) {
						sustained = false;
						warnHistory(result);
					} else if (count <= threshold)
						sustained = false;
				}
				if (sustained)
					result.outbreakAlerts.add("SUSTAINED_INCREASE");
			} else
				throw new SurveillanceException(503, "INVALID_OUTBREAK_RULE");
		}
	}
	
	private int count(List<CaseRecord> records, String event, LocalDate start, LocalDate end) {
		Set<String> unique = new HashSet<String>();
		for (CaseRecord record : records)
			if (event.equals(record.eventUuid) && "CONFIRMED".equals(record.status) && record.onset != null
			        && !record.onset.isBefore(start) && record.onset.isBefore(end))
				unique.add(record.encounterUuid);
		return unique.size();
	}
	
	private void warnHistory(CaseResult result) {
		if (!result.warnings.contains("INSUFFICIENT_HISTORY"))
			result.warnings.add("INSUFFICIENT_HISTORY");
	}

	private String origin(String value) {
		String[] legacy = { "AUTOCHTHONOUS", "IMPORTED_NATIONAL", "IMPORTED_INTERNATIONAL", "INDUCED", "INTRODUCED",
		        "RELAPSE", "RECRUDESCENCE" };
		String[] current = { "AUTOCTONO", "IMPORTADO_NACIONAL", "IMPORTADO_INTERNACIONAL", "INDUCIDO", "INTRODUCIDO",
		        "RECAIDA", "RECRUDESCENCIA" };
		for (int i = 0; i < legacy.length; i++)
			if (legacy[i].equals(value))
				return current[i];
		return value;
	}

	private int countOrigin(List<CaseRecord> records, String event, LocalDate start, LocalDate end, String origin) {
		List<CaseRecord> matching = new ArrayList<>();
		for (CaseRecord record : records)
			if (origin.equals(origin(record.origin)))
				matching.add(record);
		return count(matching, event, start, end);
	}
}
