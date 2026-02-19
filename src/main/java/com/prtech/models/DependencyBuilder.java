package com.prtech.models;

import java.time.Instant;
import java.time.LocalDate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.prtech.form.CC;
import com.prtech.form.FormFactory;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvReader;
import com.prtech.svarog_common.DbDataObject;

public class DependencyBuilder {
	static final Logger log4j = LogManager.getLogger(FormFactory.class.getName());
	static final Gson GSON = new Gson();

	private BaseObjectModel objectModel;

	public DependencyBuilder(BaseObjectModel objectModel) {
		super();
		this.objectModel = objectModel;
	}

	public BaseObjectModel getObjectModel() {
		return objectModel;
	}

	public void setObjectModel(BaseObjectModel objectModel) {
		this.objectModel = objectModel;
	}

	public JsonElement build(JsonObject jsonSchema, String localeId, SvReader svr) {
		JsonArray allOf = new JsonArray();

		for (FieldDependency entry : this.objectModel.getFieldDependencies()) {
			JsonObject conditionalSchema = buildConditionalSchema(entry, jsonSchema, localeId, svr);

			if (conditionalSchema != null) {
				allOf.add(conditionalSchema);
			}
		}

		return allOf;
	}

	/**
	 * Builds a single conditional schema (if-then structure)
	 * 
	 * @param section        the form section
	 * @param dependentField the field that depends on another field's value
	 * @param dependency     the dependency definition
	 * @param localeId       user locale identifier
	 * @param svr            SvReader instance
	 * @return JsonObject containing if-then conditional schema
	 */
	private JsonObject buildConditionalSchema(FieldDependency dependency, JsonObject jsonSchema, String localeId,
			SvReader svr) {

		JsonObject conditional = new JsonObject();

		try {
			DbDataObject dependentFieldDbo = this.objectModel.getTableFields().get(dependency.getFieldName());
			DbDataObject sourceFieldDbo = this.objectModel.getTableFields().get(dependency.getDependsOn());

			if (dependentFieldDbo == null || sourceFieldDbo == null) {
				log4j.warn("Field not found for dependency: " + dependency.getFieldName() + " depends on "
						+ dependency.getDependsOn());
				return null;
			}

			JsonObject ifCondition = buildIfCondition(dependency, sourceFieldDbo);
			conditional.add("if", ifCondition);

			JsonObject thenSchema = buildThenSchema(dependency.getFieldName(), dependentFieldDbo, localeId);
			conditional.add("then", thenSchema);

			if (ifCondition.size() > 0 && thenSchema.size() > 0) {
				String groupPath = getFieldGroupPath(dependentFieldDbo);
				if (groupPath != null) {
					JsonObject groupObj = jsonSchema.getAsJsonObject(CC.PROPERTIES).getAsJsonObject(groupPath);
					JsonObject groupProp = groupObj != null ? groupObj.getAsJsonObject(CC.PROPERTIES) : null;
					if (groupProp != null && groupProp.has(dependency.getFieldName())) {
						groupProp.remove(dependency.getFieldName());
					}
				} else {
					JsonObject propObj = jsonSchema.getAsJsonObject(CC.PROPERTIES);
					if (propObj != null && propObj.has(dependency.getFieldName())) {
						propObj.remove(dependency.getFieldName());
					}
				}
			}
		} catch (Exception e) {
			log4j.error("Error building conditional schema for field: " + dependency.getFieldName(), e);
			return null;
		}

		return conditional;
	}

	/**
	 * Builds the IF condition part of the conditional schema.
	 * 
	 * @param section        the form section
	 * @param dependency     the field dependency
	 * @param sourceFieldDbo the source field metadata
	 * @return JsonObject representing the if condition
	 */
	private static JsonObject buildIfCondition(FieldDependency dependency, DbDataObject sourceFieldDbo) {

		JsonObject ifCondition = new JsonObject();
		JsonObject properties = new JsonObject();

		String groupPath = getFieldGroupPath(sourceFieldDbo);
		if (groupPath != null) {
			JsonObject groupObj = new JsonObject();
			JsonObject groupProperties = new JsonObject();
			JsonObject fieldCondition = new JsonObject();
			if (dependency.getExpectedValue().size() > 1) {
				fieldCondition.add("enum", GSON.toJsonTree(dependency.getExpectedValue()));
			} else if (dependency.getExpectedValue().size() == 1) {
				fieldCondition.add("const", GSON.toJsonTree(dependency.getExpectedValue().get(0)));
			}
			groupProperties.add(dependency.getDependsOn(), fieldCondition);
			groupObj.add("properties", groupProperties);
			properties.add(groupPath, groupObj);
		} else {
			JsonObject fieldCondition = new JsonObject();
			fieldCondition.addProperty("const", dependency.getExpectedValue().toString());
			properties.add(dependency.getDependsOn(), fieldCondition);
		}

		ifCondition.add("properties", properties);
		return ifCondition;
	}

	/**
	 * Builds the THEN part of the conditional schema.
	 * 
	 * @param section           the form section
	 * @param dependentField    the dependent field name
	 * @param dependentFieldDbo the dependent field metadata
	 * @param localeId          user locale identifier
	 * @return JsonObject representing the then schema
	 */
	private static JsonObject buildThenSchema(String dependentField, DbDataObject dependentFieldDbo, String localeId) {

		JsonObject thenSchema = new JsonObject();
		JsonObject properties = new JsonObject();

		JsonObject fieldSchema = new JsonObject();
		fieldSchema = addFieldTypeToJsonObject(dependentFieldDbo, fieldSchema);
		fieldSchema.addProperty(CC.TITLE_LC,
				I18n.getText(localeId, dependentFieldDbo.getVal(CC.LABEL_CODE).toString()));

		if (CC.NVARCHAR.equals(dependentFieldDbo.getVal(CC.FIELD_TYPE).toString())
				&& ((Long) dependentFieldDbo.getVal(CC.FIELD_SIZE)) != null
				&& ((Long) dependentFieldDbo.getVal(CC.FIELD_SIZE)) > 0) {
			fieldSchema.addProperty("maxLength", (Long) dependentFieldDbo.getVal(CC.FIELD_SIZE));
		}

		String groupPath = getFieldGroupPath(dependentFieldDbo);
		if (groupPath != null) {
			JsonObject groupObj = new JsonObject();
			JsonObject groupProperties = new JsonObject();
			groupProperties.add(dependentField, fieldSchema);
			groupObj.add("properties", groupProperties);
			properties.add(groupPath, groupObj);
		} else {
			properties.add(dependentField, fieldSchema);
		}

		thenSchema.add("properties", properties);
		return thenSchema;
	}

	/**
	 * Extracts the groupPath from a field's GUI metadata.
	 * 
	 * @param fieldDbo the field metadata
	 * @return groupPath string or null if not found
	 */
	private static String getFieldGroupPath(DbDataObject fieldDbo) {
		if (fieldDbo.getVal(CC.GUI_METADATA) == null) {
			return null;
		}

		try {
			JsonObject guiMetadata = GSON.fromJson(fieldDbo.getVal(CC.GUI_METADATA).toString(), JsonObject.class);
			if (guiMetadata.has(CC.REACT)) {
				JsonObject reactJson = guiMetadata.getAsJsonObject(CC.REACT);
				if (reactJson.has(CC.GROUPPATH)) {
					return reactJson.get(CC.GROUPPATH).getAsString();
				}
			}
		} catch (Exception e) {
			log4j.debug("Error parsing GUI_METADATA for field: " + fieldDbo.getVal(CC.FIELD_NAME), e);
		}

		return null;
	}

	/**
	 * Method to add JSONSchema values into the JSON schema object
	 * 
	 * @param fieldType DbDataObject one field that we like to add to the JSON
	 *                  Schema
	 * @param jLeaf     JsonObject Object that already has some of the fields that
	 *                  are in same table/form
	 * 
	 * @return JsonObject with new type of field added
	 */
	private static JsonObject addFieldTypeToJsonObject(DbDataObject fieldType, JsonObject jLeaf) {
		JsonObject jsonreactGUI = null;
		JsonObject guiMetadata = null;
		switch (fieldType.getVal(CC.FIELD_TYPE).toString()) {
		case CC.NVARCHAR:
			jLeaf.addProperty(CC.TYPE_LC, CC.STRING_LC);
			break;
		case "TEXT":
			jLeaf.addProperty(CC.TYPE_LC, CC.STRING_LC);
			jLeaf.addProperty("format", "file");
			break;
		case CC.NUMERIC:
			Long tmpL = (Long) fieldType.getVal(CC.FIELD_SCALE);
			if (tmpL != null && tmpL > 0) {
				jLeaf.addProperty(CC.TYPE_LC, "number");
			} else {
				jLeaf.addProperty(CC.TYPE_LC, "integer");
			}
			break;
		case CC.DATE:
			jLeaf.addProperty(CC.TYPE_LC, CC.STRING_LC);
			jLeaf.addProperty("format", "date");
			jLeaf.addProperty("datetype", "shortdate");
			break;
		case CC.TIMESTAMP:
		case CC.DATETIME:
			jLeaf.addProperty(CC.TYPE_LC, CC.STRING_LC);
			jLeaf.addProperty("format", "date-time");
			jLeaf.addProperty("datetype", "longdate");
			break;
		case CC.BOOLEAN:
			jLeaf.addProperty(CC.TYPE_LC, "boolean");
			break;
		default:
		}
		try {
			if (fieldType.getVal(CC.GUI_METADATA) != null)
				guiMetadata = GSON.fromJson(fieldType.getVal(CC.GUI_METADATA).toString(), JsonObject.class);
		} catch (Exception e) {
			log4j.debug(e);
		}
		if (guiMetadata != null && guiMetadata.has(CC.REACT)) {
			jsonreactGUI = (JsonObject) guiMetadata.get(CC.REACT);
		}
		String fieldTypeStr = fieldType.getVal(CC.FIELD_TYPE).toString();
		if (jsonreactGUI != null && jsonreactGUI.has("default"))
			switch (fieldTypeStr) {
			case CC.NUMERIC:
				Long tmpL = (Long) fieldType.getVal(CC.FIELD_SCALE);
				if (tmpL != null && tmpL > 0) {
					jLeaf.addProperty("default", jsonreactGUI.get("default").getAsNumber());
				} else {
					jLeaf.addProperty("default", jsonreactGUI.get("default").getAsInt());
				}
				break;
			case CC.NVARCHAR:
				jLeaf.addProperty("default", jsonreactGUI.get("default").getAsString());
				break;
			case CC.DATE:
			case CC.TIMESTAMP:
			case CC.DATETIME:
				String defaultValue = jsonreactGUI.get("default").getAsString();
				if (defaultValue.equals("{TODAY}")) {
					defaultValue = fieldTypeStr.equals(CC.DATE) ? LocalDate.now().toString() : Instant.now().toString();
				}
				jLeaf.addProperty("default", defaultValue);
				break;
			case CC.BOOLEAN:
				jLeaf.addProperty("default", jsonreactGUI.get("default").getAsBoolean());
				break;
			default:
				jLeaf.addProperty("default", jsonreactGUI.get("default").getAsString());
			}
		return jLeaf;
	}
}
