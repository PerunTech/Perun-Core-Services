package com.prtech.models;

import java.util.List;

/**
 * Class representing a field dependency relationship. Used to control field
 * visibility based on the value of another field.
 */
public final class FieldDependency {
	private final String fieldName;
	private final String dependsOn;
	private final List<Object> expectedValue;

	/**
	 * Constructs a field dependency.
	 *
	 * @param dependsOn     the field name this field depends on
	 * @param expectedValue the expected value of the dependent field to show this
	 *                      field
	 */
	public FieldDependency(String fieldName, String dependsOn, List<Object> expectedValue) {
		this.fieldName = fieldName;
		this.dependsOn = dependsOn;
		this.expectedValue = expectedValue;
	}

	public String getFieldName() {
		return fieldName;
	}

	public String getDependsOn() {
		return dependsOn;
	}

	public List<Object> getExpectedValue() {
		return expectedValue;
	}
}
