package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.util.Date;

import org.openmrs.Person;
import org.openmrs.User;
import org.openmrs.annotation.Handler;
import org.openmrs.api.context.Context;
import org.openmrs.api.handler.SaveHandler;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db.SurveillanceDao;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.SurveillanceCase;

/** Runs in the person/patient save lifecycle, including changes outside surveillance. */
@Handler(supports = { Person.class })
public class DeathAttributionSaveHandler implements SaveHandler<Person> {
	
	public void handle(Person person, User actor, Date date, String reason) {
		if (person.getId() == null || (Boolean.TRUE.equals(person.getDead()) && person.getDeathDate() != null))
			return;
		clear(person, actor, date, Context.getRegisteredComponent("surveillance.dao", SurveillanceDao.class));
	}
	
	public void clear(Person person, User actor, Date date, SurveillanceDao dao) {
		for (SurveillanceCase value : dao.casesForPerson(person.getId())) {
			if (value.getDeathAttribution() != null) {
				value.setDeathAttribution(null);
				value.setChangedBy(actor);
				value.setDateChanged(date);
				dao.save(value);
			}
		}
	}
}
