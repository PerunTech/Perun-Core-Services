package com.prtech.form;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

/**
 * Represents a section within a form, containing field definitions, visibility
 * rules, and configuration options. Sections can be configured as read-only,
 * array-based, or with field dependencies.
 */
public class FormSection {
	String tableName;
	String title;
	List<String> fieldNames;
	JsonObject extendedParams;
	List<String> visibleFor;
	boolean readOnly;
	List<String> readOnlyExclude;
	boolean isArray;
	Map<String, FieldDependency> dependencies;

	/**
	 * Constructs a basic FormSection with minimal configuration.
	 *
	 * @param tableName  the database table name this section represents
	 * @param fieldNames list of field names to include in this section
	 */
	public FormSection(String tableName, List<String> fieldNames) {
		this(tableName, null, fieldNames, null, null, false, null);
	}

	/**
	 * Constructs a FormSection with full configuration options.
	 *
	 * @param tableName       the database table name this section represents
	 * @param title           the title of the section - label
	 * @param fieldNames      list of field names to include in this section
	 * @param extendedParams  additional JSON parameters for field customization
	 *                        (GUI Metadata)
	 * @param visibleFor      list of Svarog user group names that can view this
	 *                        section
	 * @param readOnly        whether all fields in this section should be read-only
	 * @param readOnlyExclude list of field names to exclude from read-only
	 *                        enforcement
	 */
	public FormSection(String tableName, String title, List<String> fieldNames, JsonObject extendedParams,
			List<String> visibleFor, boolean readOnly, List<String> readOnlyExclude) {
		this.tableName = tableName;
		this.title = title;
		this.fieldNames = fieldNames;
		this.extendedParams = extendedParams;
		this.visibleFor = visibleFor;
		this.readOnly = readOnly;
		this.readOnlyExclude = readOnlyExclude;
		this.isArray = false;
		this.dependencies = null;
	}

	/**
	 * Constructs a FormSection with array support and field dependencies.
	 *
	 * @param tableName       the database table name this section represents
	 * @param title           the title of the section - label
	 * @param fieldNames      list of field names to include in this section
	 * @param extendedParams  additional JSON parameters for field customization
	 *                        (GUI Metadata)
	 * @param visibleFor      list of Svarog user group names that can view this
	 *                        section
	 * @param readOnly        whether all fields in this section should be read-only
	 * @param readOnlyExclude list of field names to exclude from read-only
	 *                        enforcement
	 * @param isArray         whether this section represents an array of items
	 * @param dependencies    map of field dependencies for conditional field
	 *                        visibility
	 */
	public FormSection(String tableName, String title, List<String> fieldNames, JsonObject extendedParams,
			List<String> visibleFor, boolean readOnly, List<String> readOnlyExclude, boolean isArray,
			Map<String, FieldDependency> dependencies) {
		super();
		this.tableName = tableName;
		this.title = title;
		this.fieldNames = fieldNames;
		this.extendedParams = extendedParams;
		this.visibleFor = visibleFor;
		this.readOnly = readOnly;
		this.readOnlyExclude = readOnlyExclude;
		this.isArray = isArray;
		this.dependencies = dependencies;
	}

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public List<String> getFieldNames() {
		return fieldNames;
	}

	public void setFieldNames(List<String> fieldNames) {
		this.fieldNames = fieldNames;
	}

	public JsonObject getExtendedParams() {
		return extendedParams;
	}

	public void setExtendedParams(JsonObject extendedParams) {
		this.extendedParams = extendedParams;
	}

	public List<String> getVisibleFor() {
		return visibleFor;
	}

	public void setVisibleFor(List<String> visibleFor) {
		this.visibleFor = visibleFor;
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}

	public List<String> getReadOnlyExclude() {
		return readOnlyExclude;
	}

	public void setReadOnlyExclude(List<String> readOnlyExclude) {
		this.readOnlyExclude = readOnlyExclude;
	}

	public boolean isArray() {
		return isArray;
	}

	public void setArray(boolean isArray) {
		this.isArray = isArray;
	}

	public Map<String, FieldDependency> getDependencies() {
		return dependencies;
	}

	public void setDependencies(Map<String, FieldDependency> dependencies) {
		this.dependencies = dependencies;
	}

	/**
	 * Inner class representing a field dependency relationship. Used to control
	 * field visibility based on the value of another field.
	 */
	public static class FieldDependency {
		String dependsOn;
		Object expectedValue;

		/**
		 * Constructs a field dependency.
		 *
		 * @param dependsOn     the field name this field depends on
		 * @param expectedValue the expected value of the dependent field to show this
		 *                      field
		 */
		public FieldDependency(String dependsOn, Object expectedValue) {
			this.dependsOn = dependsOn;
			this.expectedValue = expectedValue;
		}

		public String getDependsOn() {
			return dependsOn;
		}

		public void setDependsOn(String dependsOn) {
			this.dependsOn = dependsOn;
		}

		public Object getExpectedValue() {
			return expectedValue;
		}

		public void setExpectedValue(Object expectedValue) {
			this.expectedValue = expectedValue;
		}
	}
}
