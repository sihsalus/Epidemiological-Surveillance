package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.SurveillanceCase;

/** RF-21–23. The service supplies the disease, territory and investigation-period cohort. */
public class IndicatorCalculator {
	
	public Map<String, Object> calculate(List<SurveillanceCase> cases, String indicator, String periodicity,
	        ClinicalCatalog metadata, int[] deadline) {
		int numerator = 0;
		for (SurveillanceCase value : cases) {
			boolean matches = "completeness".equals(indicator) ? complete(value)
			        : "fatality".equals(indicator)
			                ? "CONFIRMADO".equals(value.getDiagnosisType()) && value.getPatient() != null
			                        && value.getPatient().getDeathDate() != null && "SI".equals(value.getDeathAttribution())
			                : timely(value, periodicity, metadata, deadline);
			if (matches)
				numerator++;
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("indicator", indicator);
		result.put("numerator", numerator);
		result.put("denominator", cases.size());
		result.put("percentage", cases.isEmpty() ? null : 100.0 * numerator / cases.size());
		return result;
	}
	
	private boolean complete(SurveillanceCase value) {
		return value.getOnsetDate() != null && value.getNotificationDate() != null && value.getInfectionAddress() != null
		        && ClinicalCatalogService.present(value.getOrigin());
	}
	
	public boolean timely(SurveillanceCase value, String periodicity, ClinicalCatalog metadata, int[] deadline) {
		if (value.getNotificationDate() == null || value.getEncounter() == null
		        || value.getEncounter().getEncounterDatetime() == null)
			return false;
		Instant care = Instant.ofEpochMilli(value.getEncounter().getEncounterDatetime().getTime());
		Instant notified = Instant.ofEpochMilli(value.getNotificationDate().getTime());
		if (notified.isBefore(care))
			return false;
		if ("INMEDIATA".equals(periodicity))
			return notified.isBefore(care.plus(Duration.ofHours(24)));
		if (!"SEMANAL".equals(periodicity))
			return false;
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(metadata);
		LocalDate nextWeek = calendar.start(calendar.local(value.getEncounter().getEncounterDatetime()), "semana")
		        .plusWeeks(1);
		LocalDate due = nextWeek.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(deadline[0])));
		Instant limit = due.atTime(deadline[1], deadline[2]).atZone(ZoneId.of(metadata.timezone)).toInstant();
		return notified.isBefore(limit);
	}
}
