package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;

import java.time.LocalDate;
import java.util.*;

import org.junit.Test;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;

public class DemographicCalculatorTest {
	
	private final EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(new ClinicalCatalog());
	
	@Test
	public void groupsCoverEveryAgeAndBoundaryIncludingAnUnboundedLastGroup() {
		List<AgeGroup> groups = DemographicCalculator.groups(null);
		int[] ages = { 0, 1, 2, 11, 12, 17, 18, 29, 30, 59, 60, Integer.MAX_VALUE };
		for (int i = 0; i < ages.length; i++)
			assertEquals(DemographicCalculator.GROUPS.get(i / 2), DemographicCalculator.ageGroup(ages[i], groups));
		assertEquals("UNKNOWN", DemographicCalculator.ageGroup(null, groups));
		assertEquals("UNKNOWN", DemographicCalculator.ageGroup(-1, groups));
		assertEquals(6, DemographicCalculator.groups(DemographicCalculator.validateGroups(groups)).size());
	}
	
	@Test
	public void configurationRejectsGapsOverlapsMissingGroupsAndAnUpperLimit() {
		for (int scenario = 0; scenario < 6; scenario++) {
			List<AgeGroup> groups = DemographicCalculator.groups(null);
			if (scenario == 0)
				groups.get(0).minAge = 1;
			if (scenario == 1)
				groups.get(1).minAge = 3;
			if (scenario == 2)
				groups.get(1).minAge = 1;
			if (scenario == 3)
				groups.get(5).maxAge = 100;
			if (scenario == 4)
				groups.remove(2);
			if (scenario == 5)
				groups.get(1).maxAge = 0;
			try {
				DemographicCalculator.validateGroups(groups);
				fail("Invalid partition accepted");
			}
			catch (SurveillanceException ex) {
				assertEquals("INVALID_AGE_GROUPS", ex.getCode());
			}
		}
	}
	
	@Test
	public void customNamesAndVariableGroupsRoundTripWithoutLosingUnicode() {
		AgeGroup first = new AgeGroup("custom-1", 0, 17);
		first.name = "Ni\u00f1os \uD83D\uDE00";
		AgeGroup last = new AgeGroup("custom-2", 18, null);
		last.name = "Adultos";
		List<AgeGroup> restored = DemographicCalculator
		        .groups(DemographicCalculator.validateGroups(Arrays.asList(first, last)));
		assertEquals(first.name, restored.get(0).name);
		assertEquals("custom-2", DemographicCalculator.ageGroup(18, restored));
		last.minAge = 0;
		assertEquals(1,
		    DemographicCalculator.groups(DemographicCalculator.validateGroups(Collections.singletonList(last))).size());
		last.name = " ";
		try {
			DemographicCalculator.validateGroups(Collections.singletonList(last));
			fail("Blank name accepted");
		}
		catch (SurveillanceException ex) {
			assertEquals("INVALID_AGE_GROUPS", ex.getCode());
		}
	}

	private DemographicCalculator.Record record(Integer age, String sex, String disease, Boolean pregnant, String date) {
		DemographicCalculator.Record value = new DemographicCalculator.Record();
		value.age = age;
		value.sex = sex;
		value.disease = disease;
		value.pregnant = pregnant;
		value.date = LocalDate.parse(date);
		value.ethnicity = age == null ? null : "Synthetic ethnicity";
		return value;
	}
	
	@Test
	public void everyCrossTabPreservesTotalsIncludingUnknownValues() {
		List<DemographicCalculator.Record> records = Arrays.asList(record(10, "F", "a", true, "2026-01-01"),
		    record(60, "M", "b", false, "2026-01-20"), record(null, null, "a", null, "2026-02-01"));
		Map<String, String> diseases = new LinkedHashMap<String, String>();
		diseases.put("a", "Disease A");
		diseases.put("b", "Disease B");
		diseases.put("c", "Disease without cases");
		for (String row : DemographicCalculator.DIMENSIONS)
			for (String column : DemographicCalculator.DIMENSIONS) {
				if (row.equals(column))
					continue;
				DemographicReport result = new DemographicCalculator().calculate(records, row, column, "mes",
				    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28), DemographicCalculator.groups(null), diseases,
				    calendar);
				assertEquals(3, result.total);
				assertEquals(3, result.rowTotals.stream().mapToInt(Integer::intValue).sum());
				assertEquals(3, result.columnTotals.stream().mapToInt(Integer::intValue).sum());
				for (int i = 0; i < result.rows.size(); i++)
					assertEquals(result.rowTotals.get(i).intValue(),
					    result.values.get(i).stream().mapToInt(Integer::intValue).sum());
			}
	}
	
	@Test
	public void periodsIncludeZeroBucketsAndRespectEpidemiologicalWeeks() {
		for (String period : DemographicCalculator.PERIODS) {
			DemographicReport result = new DemographicCalculator().calculate(Collections.emptyList(), "period", "sex",
			    period, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28), DemographicCalculator.groups(null),
			    Collections.emptyMap(), calendar);
			assertFalse(result.rows.isEmpty());
			assertEquals(0, result.total);
			if ("semana".equals(period))
				assertEquals("2025-12-28", result.rows.get(0).key);
			if ("mes".equals(period))
				assertEquals(2, result.rows.size());
			if ("anio".equals(period))
				assertEquals(1, result.rows.size());
		}
	}
}
