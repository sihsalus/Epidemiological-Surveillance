package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.time.LocalDate;
import java.util.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;

public class DemographicCalculator {
	
	public static final List<String> GROUPS = Collections
	        .unmodifiableList(Arrays.asList("INFANTS", "CHILDREN", "ADOLESCENTS", "YOUNG_ADULTS", "ADULTS", "OLDER_ADULTS"));
	
	public static final List<String> DIMENSIONS = Collections
	        .unmodifiableList(Arrays.asList("age", "sex", "disease", "ageGroup", "ethnicity", "pregnancy", "period"));
	
	public static final List<String> PERIODS = Collections
	        .unmodifiableList(Arrays.asList("dia", "semana", "mes", "trimestre", "semestre", "anio"));
	
	public static final String UNKNOWN = "UNKNOWN";
	
	public static List<AgeGroup> groups(String stored) {
		if (stored != null && stored.trim().startsWith("[")) {
			try {
				List<AgeGroup> groups = new ObjectMapper().readValue(stored, new TypeReference<List<AgeGroup>>() {});
				validateGroups(groups);
				return groups;
			}
			catch (Exception ex) {
				throw new SurveillanceException(422, "INVALID_AGE_GROUPS");
			}
		}
		String[] bounds = (stored == null ? "0,2,12,18,30,60" : stored).split(",");
		List<AgeGroup> result = new ArrayList<AgeGroup>();
		try {
			if (bounds.length != GROUPS.size())
				throw new IllegalArgumentException();
			for (int i = 0; i < bounds.length; i++)
				result.add(new AgeGroup(GROUPS.get(i), Integer.parseInt(bounds[i]),
				        i + 1 == bounds.length ? null : Integer.parseInt(bounds[i + 1]) - 1));
			validateGroups(result);
		}
		catch (RuntimeException ex) {
			throw new SurveillanceException(422, "INVALID_AGE_GROUPS");
		}
		return result;
	}
	
	public static String validateGroups(List<AgeGroup> groups) {
		if (groups == null || groups.isEmpty())
			throw new SurveillanceException(422, "INVALID_AGE_GROUPS");
		long next = 0;
		Set<String> keys = new HashSet<String>();
		for (int i = 0; i < groups.size(); i++) {
			AgeGroup group = groups.get(i);
			if (group == null || group.key == null || !group.key.matches("[A-Za-z0-9_-]+") || UNKNOWN.equals(group.key)
			        || !keys.add(group.key)
			        || (group.name == null ? !GROUPS.contains(group.key) : group.name.trim().isEmpty())
			        || group.minAge == null || group.minAge != next
			        || (i == groups.size() - 1 ? group.maxAge != null : group.maxAge == null || group.maxAge < group.minAge))
				throw new SurveillanceException(422, "INVALID_AGE_GROUPS");
			if (group.maxAge != null)
				next = (long) group.maxAge + 1;
		}
		try {
			return new ObjectMapper().writeValueAsString(groups);
		}
		catch (Exception ex) {
			throw new SurveillanceException(422, "INVALID_AGE_GROUPS");
		}
	}
	
	public static String ageGroup(Integer age, List<AgeGroup> groups) {
		if (age != null && age >= 0)
			for (AgeGroup group : groups)
				if (age >= group.minAge && (group.maxAge == null || age <= group.maxAge))
					return group.key;
		return UNKNOWN;
	}
	
	public static LocalDate periodStart(LocalDate date, String period, EpidemiologicalCalendar calendar) {
		return "anio".equals(period) ? date.withDayOfYear(1) : calendar.start(date, period);
	}
	
	public static String periodLabel(LocalDate date, String period, EpidemiologicalCalendar calendar) {
		if ("anio".equals(period))
			return String.valueOf(date.getYear());
		if ("dia".equals(period))
			return date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu"));
		int year = calendar.year(date, period), number = calendar.number(date, period);
		if ("semana".equals(period))
			return String.format(java.util.Locale.ROOT, "%04d-S%02d", year, number);
		if ("mes".equals(period))
			return String.format(java.util.Locale.ROOT, "%04d-%02d", year, number);
		return year + ("trimestre".equals(period) ? "-T" : "-") + number;
	}
	
	public static class Record {
		
		public String center, origin;
		
		public Integer age;
		
		public String sex;
		
		public String disease;
		
		public String ethnicity;
		
		public Boolean pregnant;
		
		public LocalDate date;
	}
	
	public DemographicReport calculate(List<Record> records, String row, String column, String period, LocalDate from,
	        LocalDate to, List<AgeGroup> groups, Map<String, String> diseases, EpidemiologicalCalendar calendar) {
		if (!DIMENSIONS.contains(row) || !DIMENSIONS.contains(column) || row.equals(column) || !PERIODS.contains(period))
			throw new SurveillanceException(422, "INVALID_DEMOGRAPHIC_DIMENSIONS");
		return aggregate(records, row, column, period, from, to, groups, diseases, calendar);
	}
	
	public DemographicReport geography(List<Record> records, String column, String period, LocalDate from, LocalDate to,
	        List<AgeGroup> groups, EpidemiologicalCalendar calendar) {
		if ((!DIMENSIONS.contains(column) && !"origin".equals(column)) || "disease".equals(column)
		        || !PERIODS.contains(period))
			throw new SurveillanceException(422, "INVALID_DEMOGRAPHIC_DIMENSIONS");
		return aggregate(records, "center", column, period, from, to, groups, Collections.emptyMap(), calendar);
	}
	
	private DemographicReport aggregate(List<Record> records, String row, String column, String period, LocalDate from,
	        LocalDate to, List<AgeGroup> groups, Map<String, String> diseases, EpidemiologicalCalendar calendar) {
		DemographicReport result = new DemographicReport();
		result.row = row;
		result.column = column;
		result.period = period;
		result.from = from.toString();
		result.to = to.toString();
		result.ageGroups = groups;
		LinkedHashMap<String, String> rows = domain(row, from, to, period, groups, diseases, calendar);
		LinkedHashMap<String, String> columns = domain(column, from, to, period, groups, diseases, calendar);
		Map<String, Map<String, Integer>> counts = new HashMap<String, Map<String, Integer>>();
		for (Record record : records) {
			String r = value(record, row, groups, period, calendar);
			String c = value(record, column, groups, period, calendar);
			rows.putIfAbsent(r, r);
			columns.putIfAbsent(c, c);
			Map<String, Integer> line = counts.computeIfAbsent(r, k -> new HashMap<String, Integer>());
			line.put(c, line.getOrDefault(c, 0) + 1);
			result.total++;
		}
		rows = sorted(rows, row);
		columns = sorted(columns, column);
		if ((long) rows.size() * columns.size() > 100000)
			throw new SurveillanceException(422, "DEMOGRAPHIC_TABLE_TOO_LARGE");
		for (Map.Entry<String, String> entry : rows.entrySet())
			result.rows.add(new DemographicReport.AxisValue(entry.getKey(), entry.getValue()));
		for (Map.Entry<String, String> entry : columns.entrySet()) {
			result.columns.add(new DemographicReport.AxisValue(entry.getKey(), entry.getValue()));
			result.columnTotals.add(0);
		}
		for (String r : rows.keySet()) {
			List<Integer> line = new ArrayList<Integer>();
			int total = 0;
			int i = 0;
			for (String c : columns.keySet()) {
				int count = counts.getOrDefault(r, Collections.<String, Integer> emptyMap()).getOrDefault(c, 0);
				line.add(count);
				total += count;
				result.columnTotals.set(i, result.columnTotals.get(i) + count);
				i++;
			}
			result.values.add(line);
			result.rowTotals.add(total);
		}
		return result;
	}
	
	private LinkedHashMap<String, String> sorted(LinkedHashMap<String, String> values, String dimension) {
		if (!"age".equals(dimension) && !"ethnicity".equals(dimension))
			return values;
		List<String> keys = new ArrayList<String>(values.keySet());
		keys.sort((a, b) -> UNKNOWN.equals(a) ? (UNKNOWN.equals(b) ? 0 : 1)
		        : UNKNOWN.equals(b) ? -1
		                : "age".equals(dimension) ? Integer.compare(Integer.parseInt(a), Integer.parseInt(b))
		                        : a.compareToIgnoreCase(b));
		LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
		for (String key : keys)
			result.put(key, values.get(key));
		return result;
	}
	
	private LinkedHashMap<String, String> domain(String dimension, LocalDate from, LocalDate to, String period,
	        List<AgeGroup> groups, Map<String, String> diseases, EpidemiologicalCalendar calendar) {
		LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
		if ("origin".equals(dimension))
			for (String key : GeographicCalculator.ORIGINS)
				result.put(key, key);
		if ("ageGroup".equals(dimension))
			for (AgeGroup group : groups)
				result.put(group.key, group.name == null ? group.key : group.name);
		if ("sex".equals(dimension))
			for (String key : Arrays.asList("F", "M", "OTHER"))
				result.put(key, key);
		if ("pregnancy".equals(dimension))
			for (String key : Arrays.asList("YES", "NO"))
				result.put(key, key);
		if ("disease".equals(dimension))
			result.putAll(diseases);
		if ("period".equals(dimension)) {
			for (LocalDate date = periodStart(from, period, calendar); !date
			        .isAfter(to); date = "anio".equals(period) ? date.plusYears(1) : calendar.next(date, period))
				result.put(date.toString(), periodLabel(date, period, calendar));
		}
		if (!"center".equals(dimension) && !"disease".equals(dimension) && !"period".equals(dimension))
			result.put(UNKNOWN, UNKNOWN);
		return result;
	}
	
	private String value(Record value, String dimension, List<AgeGroup> groups, String period,
	        EpidemiologicalCalendar calendar) {
		switch (dimension) {
			case "center":
				return value.center;
			case "origin":
				return GeographicCalculator.ORIGINS.contains(value.origin) ? value.origin : UNKNOWN;
			case "age":
				return value.age == null || value.age < 0 ? UNKNOWN : value.age.toString();
			case "sex":
				return value.sex == null || value.sex.trim().isEmpty() ? UNKNOWN
				        : Arrays.asList("F", "M").contains(value.sex) ? value.sex : "OTHER";
			case "ageGroup":
				return ageGroup(value.age, groups);
			case "disease":
				return value.disease;
			case "ethnicity":
				return value.ethnicity == null || value.ethnicity.trim().isEmpty() ? UNKNOWN : value.ethnicity;
			case "pregnancy":
				return value.pregnant == null ? UNKNOWN : value.pregnant ? "YES" : "NO";
			case "period":
				return periodStart(value.date, period, calendar).toString();
			default:
				throw new SurveillanceException(422, "INVALID_DEMOGRAPHIC_DIMENSIONS");
		}
	}
}
