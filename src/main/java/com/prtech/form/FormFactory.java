package com.prtech.form;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Factory class for creating JsonForm instances based on form name. Maintains a
 * registry of available form classes and provides instantiation methods.
 */
public abstract class FormFactory {
	static final Logger log4j = LogManager.getLogger(FormFactory.class.getName());

	/*
	 * Registry of all forms. If a new form is created it should be added to this
	 * map.
	 */
	public abstract Map<String, Class<? extends JsonForm>> getFormClasses();

	/**
	 * Retrieves the form class associated with the given form name.
	 *
	 * @param formName the name of the form to retrieve
	 * @return the Class object for the form, or null if not found
	 */
	public Class<? extends JsonForm> getFormClass(String formName) {
		return getFormClasses().get(formName);
	}

	/**
	 * Creates a new form instance.
	 *
	 * @param formName   the name of the form to create
	 * @param formParams map of form parameters for configuration
	 * @param localeId   user locale identifier
	 * @return a new JsonForm instance, or null if creation fails
	 */
	public JsonForm createObject(String formName, Map<String, String> formParams, String localeId) {
		JsonForm obj = null;
		if (getFormClasses().containsKey(formName)) {
			try {
				obj = getFormClass(formName).getDeclaredConstructor(Map.class, String.class).newInstance(formParams,
						localeId);
			} catch (Exception e) {
				log4j.error(e.getMessage(), e);
			}
		}
		return obj;
	}
}
