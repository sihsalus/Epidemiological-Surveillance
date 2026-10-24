package org.openmrs.module.sihsalusepidemiologicalsurveillance.web.controller;

import java.util.*;

import org.openmrs.api.context.Context;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/rest/v1/sihsalusepidemiologicalsurveillance")
public class SurveillanceController {
	
	@RequestMapping(value = "/alerts/stream", method = RequestMethod.GET, produces = "text/event-stream")
	@ResponseBody
	public org.springframework.web.servlet.mvc.method.annotation.SseEmitter alertStream() {
		service().alerts(null, null, "PENDING"); // All five privileges, including on every reconnect.
		org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(
		        30000L);
		Runnable listener = () -> {
			try {
				emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().data("changed"));
			}
			catch (Exception failure) {
				emitter.complete();
			}
		};
		AlertChanges.subscribe(listener);
		emitter.onCompletion(() -> AlertChanges.unsubscribe(listener));
		emitter.onTimeout(() -> {
			AlertChanges.unsubscribe(listener);
			emitter.complete();
		});
		listener.run(); // Fetch after subscribing closes the connect/commit race.
		return emitter;
	}
	
	@RequestMapping(value = "/alerts", method = RequestMethod.GET)
	@ResponseBody
	public List<Map<String, Object>> alerts(@RequestParam(value = "type", required = false) String type,
	        @RequestParam(value = "reason", required = false) String reason,
	        @RequestParam(value = "status", required = false) String status) {
		return service().alerts(type, reason, status);
	}
	
	@RequestMapping(value = "/alerts/{uuid}/attend", method = RequestMethod.POST)
	@ResponseBody
	public Map<String, Object> attendAlert(@PathVariable("uuid") String uuid) {
		return service().attendAlert(uuid);
	}
	
	@RequestMapping(value = "/configuration/notification", method = RequestMethod.GET)
	@ResponseBody
	public int[] notificationSettings() {
		return service().getNotificationSettings();
	}
	
	@RequestMapping(value = "/configuration/notification", method = RequestMethod.PUT, consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public int[] saveNotificationSettings(@RequestBody List<Integer> values) {
		return service().saveNotificationSettings(values);
	}
	
	@RequestMapping(value = "/reports/indicators", method = RequestMethod.GET)
	@ResponseBody
	public Map<String, Object> indicators(@RequestParam("event") String event, @RequestParam("from") String from,
	        @RequestParam("to") String to,
	        @RequestParam(value = "zoneLevel", defaultValue = "CENTRO_POBLADO") String zoneLevel,
	        @RequestParam(value = "address", required = false) String address,
	        @RequestParam(value = "diagnosisType", defaultValue = "CONFIRMADO") String diagnosisType,
	        @RequestParam("indicator") String indicator) {
		return service().indicators(event, from, to, zoneLevel, address, diagnosisType, indicator);
	}
	
	@RequestMapping(value = "/configuration/outbreak-rules", method = RequestMethod.GET)
	@ResponseBody
	public List<OutbreakRule> outbreakRules() {
		return service().getOutbreakRules();
	}

	@RequestMapping(value = "/configuration/outbreak-rules", method = RequestMethod.PUT, consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public List<OutbreakRule> saveOutbreakRules(@RequestBody List<OutbreakRule> rules) {
		return service().saveOutbreakRules(rules);
	}

	@RequestMapping(value = "/configuration/endemic-channel", method = RequestMethod.GET)
	@ResponseBody
	public int[] endemicSettings() {
		return service().getEndemicSettings();
	}
	
	@RequestMapping(value = "/configuration/endemic-channel", method = RequestMethod.PUT, consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public int[] saveEndemicSettings(@RequestBody List<Integer> values) {
		return service().saveEndemicSettings(values);
	}
	
	@RequestMapping(value = "/configuration/risk-levels", method = RequestMethod.GET)
	@ResponseBody
	public int[] riskThresholds() {
		return service().getRiskThresholds();
	}
	
	@RequestMapping(value = "/configuration/risk-levels", method = RequestMethod.PUT, consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public int[] saveRiskThresholds(@RequestBody List<Integer> bounds) {
		return service().saveRiskThresholds(bounds);
	}
	
	@RequestMapping(value = "/configuration/coordinates", method = RequestMethod.PUT, consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Integer> importCoordinates(@RequestBody Map<String, String> body) {
		return service().importCoordinates(body.get("csv"));
	}
	
	@RequestMapping(value = "/reports/geography", method = RequestMethod.GET)
	@ResponseBody
	public GeographicReport geography(@RequestParam("event") String event, @RequestParam("from") String from,
	        @RequestParam("to") String to,
	        @RequestParam(value = "zoneLevel", defaultValue = "DEPARTAMENTO") String zoneLevel,
	        @RequestParam(value = "address", required = false) String address,
	        @RequestParam(value = "diagnosisType", defaultValue = "CONFIRMADO") String diagnosisType,
	        @RequestParam(value = "column", defaultValue = "origin") String column,
	        @RequestParam(value = "period", defaultValue = "mes") String period) {
		return service().geography(event, from, to, zoneLevel, address, diagnosisType, column, period);
	}
	
	@RequestMapping(value = "/configuration/age-groups", method = RequestMethod.GET)
	@ResponseBody
	public List<AgeGroup> ageGroups() {
		return service().getAgeGroups();
	}
	
	@RequestMapping(value = "/configuration/age-groups", method = RequestMethod.PUT, consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public List<AgeGroup> saveAgeGroups(@RequestBody List<AgeGroup> groups) {
		return service().saveAgeGroups(groups);
	}
	
	@RequestMapping(value = "/reports/demographics", method = RequestMethod.GET)
	@ResponseBody
	public DemographicReport demographics(@RequestParam("from") String from, @RequestParam("to") String to,
	        @RequestParam("row") String row, @RequestParam("column") String column,
	        @RequestParam(value = "period", defaultValue = "mes") String period,
	        @RequestParam(value = "event", required = false) String event,
	        @RequestParam(value = "compareEvents", required = false) String compareEvents,
	        @RequestParam(value = "diagnosisType", defaultValue = "CONFIRMADO") String diagnosisType,
	        @RequestParam(value = "zoneLevel", defaultValue = "CENTRO_POBLADO") String zoneLevel,
	        @RequestParam(value = "address", required = false) String address) {
		return service().demographics(from, to, row, column, period, event, compareEvents, diagnosisType, zoneLevel,
		    address);
	}
	
	private SurveillanceService service;
	
	@RequestMapping(value = "/weekly-records", method = RequestMethod.GET)
	@ResponseBody
	public Map<String, Object> weeklyRecords(@RequestParam("year") int year, @RequestParam("week") int week) {
		return service().weeklyRecords(year, week);
	}
	
	public void setService(SurveillanceService value) {
		service = value;
	}
	
	private SurveillanceService service() {
		return service == null ? Context.getService(SurveillanceService.class) : service;
	}
	
	@RequestMapping(value = "/catalog", method = RequestMethod.GET)
	@ResponseBody
	public Map<String, Object> catalog() {
		return service().getCatalog();
	}
	
	@RequestMapping(value = "/healthcheck", method = RequestMethod.GET)
	@ResponseBody
	public Map<String, Object> healthcheck() {
		return service().healthcheck();
	}
	
	@RequestMapping(value = "/events", method = RequestMethod.GET)
	@ResponseBody
	public Map<String, Object> events(
	        @RequestParam(value = "includeRetired", defaultValue = "false") boolean includeRetired) {
		return Collections.<String, Object> singletonMap("results", service().getEvents(includeRetired));
	}
	
	@RequestMapping(value = "/events/{uuid}", method = RequestMethod.GET)
	@ResponseBody
	public Map<String, Object> event(@PathVariable("uuid") String uuid) {
		return service().getEvent(uuid);
	}
	
	@RequestMapping(value = { "/events/{uuid}",
	        "/events/{uuid}/valid-to" }, method = RequestMethod.PUT, consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> updateEvent(@PathVariable("uuid") String uuid, @RequestBody Map<String, Object> body) {
		return service().updateEvent(uuid, body);
	}
	
	@RequestMapping(value = "/cases", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<SurveillanceCaseResponse> createDraft(@RequestBody SurveillanceCaseRequest body) {
		return new ResponseEntity<SurveillanceCaseResponse>(service().createDraft(body), HttpStatus.CREATED);
	}
	
	@RequestMapping(value = "/cases", method = RequestMethod.GET)
	@ResponseBody
	public Map<String, Object> cases(@RequestParam(value = "patient", required = false) String patient,
	        @RequestParam(value = "diagnosisConcept", required = false) String diagnosisConcept,
	        @RequestParam(value = "onsetFrom", required = false) String onsetFrom,
	        @RequestParam(value = "onsetTo", required = false) String onsetTo,
	        @RequestParam(value = "notificationFrom", required = false) String notificationFrom,
	        @RequestParam(value = "notificationTo", required = false) String notificationTo,
	        @RequestParam(value = "fatal", required = false) Boolean fatal) {
		return Collections.<String, Object> singletonMap("results",
		    service().listCases(patient, diagnosisConcept, onsetFrom, onsetTo, notificationFrom, notificationTo, fatal));
	}
	
	@RequestMapping(value = "/cases/{uuid}", method = RequestMethod.PUT, consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public SurveillanceCaseResponse updateDraft(@PathVariable("uuid") String uuid,
	        @RequestBody SurveillanceCaseRequest body) {
		return service().updateDraft(uuid, body);
	}
	
	@RequestMapping(value = "/cases/{uuid}/close", method = RequestMethod.POST)
	@ResponseBody
	public SurveillanceCaseResponse closeDraft(@PathVariable("uuid") String uuid) {
		return service().closeDraft(uuid);
	}
	
	@RequestMapping(value = "/cases/{uuid}", method = RequestMethod.GET)
	@ResponseBody
	public SurveillanceCaseResponse get(@PathVariable("uuid") String uuid) {
		return service().getDraft(uuid);
	}
	
	@RequestMapping(value = "/addresses/{level}", method = RequestMethod.GET)
	@ResponseBody
	public Map<String, Object> addresses(@PathVariable("level") String level,
	        @RequestParam(value = "parent", required = false) String parent) {
		return Collections.<String, Object> singletonMap("results", service().addressChildren(level, parent));
	}
	
	@RequestMapping(value = "/reports", method = RequestMethod.GET)
	@ResponseBody
	public SurveillanceReport report(@RequestParam("event") String event, @RequestParam("from") String from,
	        @RequestParam("to") String to, @RequestParam(value = "period", defaultValue = "semana") String period,
	        @RequestParam(value = "zoneLevel", defaultValue = "CENTRO_POBLADO") String zoneLevel,
	        @RequestParam(value = "address", required = false) String address,
	        @RequestParam(value = "diagnosisType", defaultValue = "CONFIRMADO") String diagnosisType) {
		return service().report(event, from, to, period, zoneLevel, address, diagnosisType);
	}
	
	@RequestMapping(value = "/counts/refresh", method = RequestMethod.POST)
	@ResponseBody
	public Map<String, Object> refresh() {
		service().refreshCounts();
		return Collections.<String, Object> singletonMap("refreshed", true);
	}
	
	@RequestMapping(value = "/events", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> event(@RequestBody Map<String, Object> body) {
		return service().saveEvent(body);
	}
	
	@ExceptionHandler(SurveillanceException.class)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> error(SurveillanceException error) {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("code", error.getCode());
		body.put("fields", error.getFields());
		body.put("fieldErrors", error.getFieldErrors());
		return new ResponseEntity<Map<String, Object>>(body, HttpStatus.valueOf(error.getStatus()));
	}
	
	@ExceptionHandler({ org.springframework.http.converter.HttpMessageNotReadableException.class,
	        org.springframework.web.bind.MissingServletRequestParameterException.class })
	@ResponseBody
	public ResponseEntity<Map<String, Object>> malformed(Exception error) {
		return new ResponseEntity<Map<String, Object>>(Collections.<String, Object> singletonMap("code", "INVALID_REQUEST"),
		        HttpStatus.BAD_REQUEST);
	}
	
	@ExceptionHandler(Exception.class)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> unexpected(Exception error) {
		return new ResponseEntity<Map<String, Object>>(
		        Collections.<String, Object> singletonMap("code", "SERVICE_UNAVAILABLE"), HttpStatus.INTERNAL_SERVER_ERROR);
	}
}
