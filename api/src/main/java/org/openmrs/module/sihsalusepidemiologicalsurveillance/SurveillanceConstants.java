package org.openmrs.module.sihsalusepidemiologicalsurveillance;

public final class SurveillanceConstants {
	
	public static final String MODULE_ID = "sihsalusepidemiologicalsurveillance";
	
	public static final String ACCESS = "app:home.epidemiologicalSurveillance";
	
	public static final String VIEW = "Vigilancia Epidemiologica: Ver Casos";
	
	public static final String REGISTER = "Vigilancia Epidemiologica: Registrar Casos";
	
	/** Separate from REGISTER: epidemiology staff edit cases they did not register (RF-05). */
	public static final String EDIT = "Vigilancia Epidemiologica: Editar Casos";

	public static final String REPORT = "Vigilancia Epidemiologica: Ver Indicadores";
	
	public static final String MANAGE = "Vigilancia Epidemiologica: Configurar";
	
	private SurveillanceConstants() {
	}
}
