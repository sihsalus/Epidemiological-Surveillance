package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.time.*;
import java.util.*;

import org.openmrs.Concept;
import org.openmrs.User;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

public class ReportCalculator {
	
	/** Builds the persisted, zone-specific aggregates required by RF-17 through RF-19. */
	public List<PeriodCaseCount> aggregateSurveillanceCases(NotifiableEvent event, List<SurveillanceCase> cases,
	        Map<Integer, Integer> districts, ClinicalCatalog metadata, User actor, LocalDate calculatedOn) {
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(metadata);
		Map<String, PeriodCaseCount> values = new LinkedHashMap<String, PeriodCaseCount>();
		for (SurveillanceCase surveillanceCase : cases) {
			if (surveillanceCase.getOnsetDate() == null || surveillanceCase.getInfectionAddress() == null
			        || "DESCARTADO".equals(surveillanceCase.getDiagnosisType()) || !belongsTo(event, surveillanceCase))
				continue;
			Integer district = districts.get(surveillanceCase.getInfectionAddress());
			if (district == null)
				continue;
			LocalDate onset = calendar.local(surveillanceCase.getOnsetDate());
			for (String period : Arrays.asList("dia", "semana", "mes", "trimestre", "semestre")) {
				add(values, event, surveillanceCase.getInfectionAddress(), "CENTRO_POBLADO", period, onset,
				    surveillanceCase.getDiagnosisType(), calendar, actor, calculatedOn);
				add(values, event, district, "DISTRITO", period, onset, surveillanceCase.getDiagnosisType(), calendar, actor,
				    calculatedOn);
			}
		}
		return new ArrayList<PeriodCaseCount>(values.values());
	}
	
	private boolean belongsTo(NotifiableEvent event, SurveillanceCase surveillanceCase) {
		if (surveillanceCase.getDiagnosis() == null || surveillanceCase.getDiagnosis().getDiagnosis() == null)
			return false;
		Concept diagnosis = surveillanceCase.getDiagnosis().getDiagnosis().getCoded();
		return diagnosis != null && event.getConcept() != null
		        && (event.getConcept().equals(diagnosis) || event.getConcept().getSetMembers().contains(diagnosis));
	}
	
	private void add(Map<String, PeriodCaseCount> values, NotifiableEvent event, Integer zone, String zoneLevel,
	        String period, LocalDate onset, String diagnosisType, EpidemiologicalCalendar calendar, User actor,
	        LocalDate calculatedOn) {
		LocalDate start = calendar.start(onset, period);
		String key = zone + ":" + period + ":" + calendar.year(onset, period) + ":" + calendar.number(onset, period) + ":"
		        + diagnosisType;
		PeriodCaseCount count = values.get(key);
		if (count == null) {
			count = new PeriodCaseCount();
			count.setEvent(event);
			count.setAddressHierarchyEntryId(zone);
			count.setZoneLevel(zoneLevel);
			count.setPeriodType(period.toUpperCase(Locale.ENGLISH));
			count.setYear(calendar.year(onset, period));
			count.setPeriodNumber(calendar.number(onset, period));
			count.setStartDate(java.sql.Date.valueOf(start));
			count.setEndDate(java.sql.Date.valueOf(calendar.next(start, period).minusDays(1)));
			count.setDiagnosisType(diagnosisType);
			count.setCaseCount(0);
			count.setCalculationDate(calendar.date(calculatedOn));
			count.setCreator(actor);
			count.setDateCreated(calendar.date(calculatedOn));
			values.put(key, count);
		}
		count.setCaseCount(count.getCaseCount() + 1);
	}
}
