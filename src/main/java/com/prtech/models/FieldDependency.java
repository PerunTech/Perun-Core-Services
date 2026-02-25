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
	private final String codelistName;
	private final List<String> codelistValues;

	/**
	 * Constructs a field dependency.
	 * 
	 * @param fieldName     depended field name
	 * @param dependsOn     the field name this field depends on
	 * @param expectedValue values that the dependsOn field name should have in
	 *                      order for fieldName to be visible
	 */
	public FieldDependency(String fieldName, String dependsOn, List<Object> expectedValue) {
		this.fieldName = fieldName;
		this.dependsOn = dependsOn;
		this.expectedValue = expectedValue;
		this.codelistName = null;
		this.codelistValues = null;
	}

	public FieldDependency(String fieldName, String dependsOn, List<Object> expectedValue, String codelistName,
			List<String> codelistValue) {
		super();
		this.fieldName = fieldName;
		this.dependsOn = dependsOn;
		this.expectedValue = expectedValue;
		this.codelistName = codelistName;
		this.codelistValues = codelistValue;
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

	public String getCodelistName() {
		return codelistName;
	}

	public List<String> getCodelistValues() {
		return codelistValues;
	}
}
