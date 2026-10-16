package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.time.LocalDate;
import java.util.*;

import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.GeographicReport;

/** Pure geographic aggregation; history is independent of the displayed date window. */
public class GeographicCalculator {

	public static final List<String> ORIGINS = Collections.unmodifiableList(Arrays.asList("AUTOCTONO", "IMPORTADO_NACIONAL",
	    "IMPORTADO_INTERNACIONAL", "RECAIDA", "RECRUDESCENCIA", "INDUCIDO", "INTRODUCIDO", "UNKNOWN"));

	public static int[] thresholds(String value) {
		try {
			String[] parts = (value == null ? "10,20,50" : value).split(",", -1);
			if (parts.length != 3)
				throw new IllegalArgumentException();
			int[] bounds = new int[3];
			int previous = 1;
			for (int i = 0; i < 3; i++) {
				bounds[i] = Integer.parseInt(parts[i]);
				if (bounds[i] <= previous)
					throw new IllegalArgumentException();
				previous = bounds[i];
			}
			return bounds;
		}
		catch (RuntimeException ex) {
			throw new SurveillanceException(422, "INVALID_RISK_LEVELS");
		}
	}

	public static Map<String, double[]> parseCsv(String csv) {
		Map<String, double[]> result = new LinkedHashMap<String, double[]>();
		if (csv == null)
			throw new SurveillanceException(422, "INVALID_COORDINATES_CSV");
		String[] lines = csv.replace("\uFEFF", "").split("\\r?\\n", -1);
		if (lines.length < 2 || !"address_hierarchy_entry_id,latitude,longitude".equals(lines[0].trim()))
			throw new SurveillanceException(422, "INVALID_COORDINATES_CSV");
		try {
			for (int i = 1; i < lines.length; i++) {
				if (lines[i].trim().isEmpty())
					continue;
				String[] cells = lines[i].split(",", -1);
				if (cells.length != 3)
					throw new IllegalArgumentException();
				String code = cells[0].trim();
				if (code.length() > 255 || !code.matches("[0-9]+") || result.containsKey(code))
					throw new IllegalArgumentException();
				double lat = Double.parseDouble(cells[1].trim()), lon = Double.parseDouble(cells[2].trim());
				if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180)
					throw new IllegalArgumentException();
				result.put(code, new double[] { lat, lon });
			}
			if (result.isEmpty())
				throw new IllegalArgumentException();
		}
		catch (RuntimeException ex) {
			throw new SurveillanceException(422, "INVALID_COORDINATES_CSV");
		}
		return result;
	}

	public static class Record {

		public int center;

		public LocalDate date;

		public String origin;
	}

	public GeographicReport calculate(List<Record> records, LocalDate from, LocalDate to, int[] bounds) {
		GeographicReport report = new GeographicReport();
		report.referenceYear = from.getYear();
		report.from = from.toString();
		report.to = to.toString();
		report.riskThresholds = bounds;
		Map<Integer, GeographicReport.Focus> centers = new TreeMap<Integer, GeographicReport.Focus>();
		Map<Integer, Integer> lastLocal = new HashMap<Integer, Integer>();
		Set<Integer> uncertain = new HashSet<Integer>();
		for (Record record : records) {
			if (record.date == null || record.date.getYear() > report.referenceYear)
				continue;
			String origin = ORIGINS.contains(record.origin) ? record.origin : "UNKNOWN";
			if ("AUTOCTONO".equals(origin))
				lastLocal.merge(record.center, record.date.getYear(), Math::max);
			if (record.date.getYear() >= report.referenceYear - 3 && "UNKNOWN".equals(origin))
				uncertain.add(record.center);
			if (record.date.isBefore(from) || record.date.isAfter(to))
				continue;
			GeographicReport.Focus focus = centers.computeIfAbsent(record.center, id -> new GeographicReport.Focus(id));
			focus.total++;
			focus.origins.put(origin, focus.origins.getOrDefault(origin, 0) + 1);
			report.total++;
		}
		for (GeographicReport.Focus focus : centers.values()) {
			focus.lastAutochthonousYear = lastLocal.get(focus.centerId);
			Integer year = focus.lastAutochthonousYear;
			focus.classification = year != null && year == report.referenceYear ? "ACTIVE"
			        : year != null && year >= report.referenceYear - 3 ? "RESIDUAL"
			                : uncertain.contains(focus.centerId) ? "UNCLASSIFIED" : "ELIMINATED";
			focus.risk = focus.total >= bounds[2] ? "VERY_HIGH"
			        : focus.total >= bounds[1] ? "HIGH" : focus.total >= bounds[0] ? "MEDIUM" : "LOW";
			report.foci.add(focus);
		}
		return report;
	}
}
