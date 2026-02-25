package com.prtech.models;

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
	final private String localeId;
	final private SvReader svr;

	public DependencyBuilder(BaseObjectModel objectModel, String localeId, SvReader svr) {
		super();
		this.objectModel = objectModel;
		this.localeId = localeId;
		this.svr = svr;
	}

	public BaseObjectModel getObjectModel() {
		return objectModel;
	}

	public void setObjectModel(BaseObjectModel objectModel) {
		this.objectModel = objectModel;
	}

	public JsonElement build(JsonObject jsonSchema) {
		JsonArray allOf = new JsonArray();

		for (FieldDependency entry : this.objectModel.getFieldDependencies()) {
			JsonObject conditionalSchema = buildConditionalSchema(entry, jsonSchema);

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
	private JsonObject buildConditionalSchema(FieldDependency dependency, JsonObject jsonSchema) {

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

			JsonObject thenSchema = buildThenSchema(dependency, dependentFieldDbo);
			conditional.add("then", thenSchema);

			if (ifCondition.size() > 0 && thenSchema.size() > 0) {
				String groupPath = JsonSchemaUtils.getFieldGroupPath(dependentFieldDbo);
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
	private JsonObject buildIfCondition(FieldDependency dependency, DbDataObject sourceFieldDbo) {

		JsonObject ifCondition = new JsonObject();
		JsonObject properties = new JsonObject();

		String groupPath = JsonSchemaUtils.getFieldGroupPath(sourceFieldDbo);
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
	private JsonObject buildThenSchema(FieldDependency dependency, DbDataObject dependentFieldDbo) {

		JsonObject thenSchema = new JsonObject();
		JsonObject properties = new JsonObject();

		JsonObject fieldSchema = new JsonObject();
		fieldSchema = JsonSchemaUtils.addFieldTypeToJsonObject(dependentFieldDbo, fieldSchema);
		fieldSchema.addProperty(CC.TITLE_LC,
				I18n.getText(localeId, dependentFieldDbo.getVal(CC.LABEL_CODE).toString()));

		if (CC.NVARCHAR.equals(dependentFieldDbo.getVal(CC.FIELD_TYPE).toString())
				&& ((Long) dependentFieldDbo.getVal(CC.FIELD_SIZE)) != null
				&& ((Long) dependentFieldDbo.getVal(CC.FIELD_SIZE)) > 0) {
			fieldSchema.addProperty("maxLength", (Long) dependentFieldDbo.getVal(CC.FIELD_SIZE));
		}

		JsonSchemaUtils.prepareFormJsonCodeList1(dependentFieldDbo, fieldSchema, dependency.getCodelistValues(),
				localeId, svr);

		String groupPath = JsonSchemaUtils.getFieldGroupPath(dependentFieldDbo);
		if (groupPath != null) {
			JsonObject groupObj = new JsonObject();
			JsonObject groupProperties = new JsonObject();
			groupProperties.add(dependency.getFieldName(), fieldSchema);
			groupObj.add("properties", groupProperties);
			properties.add(groupPath, groupObj);
		} else {
			properties.add(dependency.getFieldName(), fieldSchema);
		}

		thenSchema.add("properties", properties);
		return thenSchema;
	}
}
