package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.util.Collections;
import java.util.List;

/** Safe, machine-readable error. Never transports native exception messages or clinical values. */
public class SurveillanceException extends RuntimeException {
	
	private final int status;
	
	private final String code;
	
	private final List<String> fields;
	
	private java.util.Map<String, String> fieldErrors = Collections.emptyMap();
	
	public SurveillanceException(int status, String code, String field, String reason) {
		this(status, code, Collections.singletonList(field));
		fieldErrors = Collections.singletonMap(field, reason);
	}
	
	public java.util.Map<String, String> getFieldErrors() {
		return fieldErrors;
	}
	
	public SurveillanceException(int status, String code) {
		this(status, code, Collections.<String> emptyList());
	}
	
	public SurveillanceException(int status, String code, List<String> fields) {
		super(code);
		this.status = status;
		this.code = code;
		this.fields = fields;
	}
	
	public int getStatus() {
		return status;
	}
	
	public String getCode() {
		return code;
	}
	
	public List<String> getFields() {
		return fields;
	}
}
