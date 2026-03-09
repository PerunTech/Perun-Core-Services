package com.prtech.form;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.prtech.models.BaseObjectModel;
import com.prtech.models.ModelFactory;
import com.prtech.svarog.CodeList;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvExecManager;
import com.prtech.svarog.SvReader;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;
import com.prtech.svarog_common.DbSearchExpression;

/**
 * Utility class for building the React JSON Schema Form.
 */
public class FormBuilder {
	static final Logger log4j = LogManager.getLogger(FormBuilder.class.getName());
	private static final Gson GSON = new Gson();
	private ModelFactory factory;

	public FormBuilder(ModelFactory factory) {
		this.factory = factory;
	}

	/**
	 * Initialize the base schema with title and type
	 */
	private static JsonObject initializeSchema(DbDataObject tableDbo, String localeId) {
		JsonObject schema = new JsonObject();

		String labelCode = tableDbo.getVal(CC.LABEL_CODE) != null ? tableDbo.getAsString(CC.LABEL_CODE)
				: "perun.generic_title";

		schema.addProperty(CC.TITLE_LC, I18n.getText(localeId, labelCode));
		schema.addProperty(CC.TYPE_LC, CC.OBJECT_LC);

		return schema;
	}

	/**
	 * Prepares the complete JSON Schema for a form section, including field
	 * definitions, validation rules, and required field handling.
	 *
	 * @param section  the FormSection to generate schema for
	 * @param localeId user locale identifier
	 * @param svr      SvReader instance for database operations
	 * @return a JsonObject containing the section's JSON Schema
	 * @throws Exception if schema generation fails
	 */
	public JsonObject prepareSectionSchema(FormSection section, String localeId, SvReader svr) throws Exception {
		BaseObjectModel obj = factory.createObject(section.getTableName());
		DbDataObject tableDbo = SvReader.getDbtByName(section.getTableName());

		ArrayList<String> listRequired = new ArrayList<>();
		HashMap<String, String> listRequiredWithLink = new HashMap<>();
		Set<String> set1 = new LinkedHashSet<>();
		Set<String> set2 = new LinkedHashSet<>();

		JsonObject schema = initializeSchema(tableDbo, localeId);
		JsonObject jFields = prepareFields(obj, section, listRequired, listRequiredWithLink, set1, set2, localeId, svr);

		schema.add(CC.PROPERTIES, jFields);

		if (!listRequired.isEmpty()) {
			Iterator<String> itr = set1.iterator();
			while (itr.hasNext()) {
				ArrayList<String> listPathRequired = new ArrayList<>();
				String vPath = itr.next();
				itr.remove();
				Iterator<Entry<String, String>> it = listRequiredWithLink.entrySet().iterator();
				while (it.hasNext()) {
					Entry<String, String> pair = it.next();
					String tmpStr1 = pair.getValue();
					if (vPath.equals(tmpStr1)) {
						it.remove();
						listPathRequired.add(pair.getKey());
						String tmpStr2 = pair.getKey();
						for (int k = 0; k < listRequired.size(); k++)
							if (tmpStr2.equals(listRequired.get(k)))
								listRequired.remove(k);
					}
				}
				JsonElement element1 = GSON.toJsonTree(listPathRequired, new TypeToken<List<String>>() {
				}.getType());
				JsonObject tmpData = (JsonObject) schema.get(CC.PROPERTIES);
				JsonObject tmpData1 = (JsonObject) tmpData.get(vPath);
				tmpData1.add(CC.REQUIRED_LC, element1);
				tmpData.add(vPath, tmpData1);
				schema.add(CC.PROPERTIES, tmpData);
			}
			JsonElement element = GSON.toJsonTree(listRequired, new TypeToken<List<String>>() {
			}.getType());
			if (element.isJsonArray()) {
				schema.add(CC.REQUIRED_LC, element);
			}
		}

		if (section.isArray())

		{
			String propertyKey = section.getTableName().toLowerCase();

			JsonObject arraySchema = new JsonObject();
			arraySchema.addProperty(CC.TYPE_LC, "array");
			arraySchema.addProperty(CC.TITLE_LC, schema.get(CC.TITLE_LC).getAsString());

			JsonObject items = new JsonObject();
			items.addProperty(CC.TYPE_LC, CC.OBJECT_LC);
			items.add(CC.PROPERTIES, schema.get(CC.PROPERTIES));

			if (schema.has(CC.REQUIRED_LC)) {
				items.add(CC.REQUIRED_LC, schema.get(CC.REQUIRED_LC));
			}

			arraySchema.add("items", items);

			JsonArray defaultArray = new JsonArray();
			arraySchema.add("default", defaultArray);

			JsonObject wrapper = new JsonObject();
			JsonObject wrapperProps = new JsonObject();
			wrapperProps.add(propertyKey, arraySchema);
			wrapper.add(CC.PROPERTIES, wrapperProps);

			return wrapper;
		}

		return schema;
	}

	/**
	 * Prepares field definitions for a form section, including type mapping,
	 * validation rules, code lists, and denormalized fields.
	 */
	private static JsonObject prepareFields(BaseObjectModel obj, FormSection section, ArrayList<String> listRequired,
			HashMap<String, String> listRequiredWithLink, Set<String> set1, Set<String> set2, String localeId,
			SvReader svr) {
		JsonObject jFields = new JsonObject();

		Boolean shouldGroupFields = true;
		if (section.getExtendedParams() != null && section.getExtendedParams().has("shouldGroupFields")) {
			shouldGroupFields = section.getExtendedParams().get("shouldGroupFields").getAsBoolean();
		}

		for (String fieldName : section.getFieldNames()) {
			try {
				DbDataObject dboField = obj.getTableFields().get(fieldName);
				JsonObject jLeaf = new JsonObject();
				JsonObject guiMetadataJson = new JsonObject();
				JsonObject reactJson = null;
				if (dboField.getVal(CC.GUI_METADATA) != null) {
					guiMetadataJson = GSON.fromJson(dboField.getVal(CC.GUI_METADATA).toString(), JsonObject.class);
				}

				if (section.extendedParams != null && section.extendedParams.has(fieldName)) {
					JsonObject extFieldParams = section.extendedParams.getAsJsonObject(fieldName);
					if (extFieldParams.has(CC.REPLACE_LC) && extFieldParams.has(CC.GUI_METADATA_CC)) {
						String replace = extFieldParams.get(CC.REPLACE_LC).getAsString();
						if (replace.equals("FULL")) {
							guiMetadataJson = extFieldParams.getAsJsonObject(CC.GUI_METADATA_CC);
						} else if (replace.equals("REPLACE")) {
							Utils.deepMerge(extFieldParams.getAsJsonObject(CC.GUI_METADATA_CC), guiMetadataJson);
						}
					}
				}

				if (guiMetadataJson != null && guiMetadataJson.has(CC.REACT)) {
					reactJson = guiMetadataJson.getAsJsonObject(CC.REACT);
				}

				if ((("false").equalsIgnoreCase(dboField.getVal(CC.ISNULL).toString()))) {
					if (dboField.getVal(CC.REFERENTIAL_TABLE) == null) {
						listRequired.add(fieldName);
						set2.add(fieldName);
						if (reactJson != null && reactJson.has(CC.GROUPPATH) && shouldGroupFields) {
							String pathString = reactJson.get(CC.GROUPPATH).getAsString();
							set1.add(pathString);
							listRequiredWithLink.put(fieldName, pathString);
						}
					}
				}

				jLeaf = addFieldTypeToJsonObject(dboField, jLeaf);
				jLeaf.addProperty(CC.TITLE_LC, I18n.getText(localeId, dboField.getVal(CC.LABEL_CODE).toString()));
				if (CC.NVARCHAR.equals(dboField.getVal(CC.FIELD_TYPE).toString())
						&& ((Long) dboField.getVal(CC.FIELD_SIZE)) != null
						&& ((Long) dboField.getVal(CC.FIELD_SIZE)) > 0)
					jLeaf.addProperty("maxLength", (Long) dboField.getVal(CC.FIELD_SIZE));
				if (reactJson != null && reactJson.has("minLength"))
					jLeaf.addProperty("minLength", reactJson.get("minLength").getAsNumber());
				if (reactJson != null && reactJson.has("maxLength"))
					jLeaf.addProperty("maxLength", reactJson.get("maxLength").getAsLong());
				if (CC.NUMERIC.equals(dboField.getVal(CC.FIELD_TYPE).toString())) {
					if (reactJson != null && reactJson.has("maximum")) {
						if (reactJson.get("maximum") != null
								&& !reactJson.get("maximum").getAsString().trim().equals("")) {
							jLeaf.addProperty("maximum", reactJson.get("maximum").getAsLong());
						}
					} else {
						jLeaf.addProperty("maximum", 999999999999999L);
					}
					if (reactJson != null && reactJson.has("minimum")) {
						if (reactJson.get("minimum") != null
								&& !reactJson.get("minimum").getAsString().trim().equals("")) {
							jLeaf.addProperty("minimum", reactJson.get("minimum").getAsLong());
						}
					}
				}
				if (reactJson != null && reactJson.has("inputDescValue"))
					jLeaf.addProperty("inputDescValue", reactJson.get("inputDescValue").getAsString());
				if (reactJson != null && reactJson.has("descriptionValue"))
					jLeaf.addProperty("descriptionValue", reactJson.get("descriptionValue").getAsString());
				if (reactJson != null && reactJson.has("searchTable"))
					jLeaf.addProperty("searchTable", reactJson.get("searchTable").getAsString());
				if (reactJson != null && reactJson.has("format") && !reactJson.get("format").getAsBoolean())
					jLeaf.remove("format");
				jLeaf = prepareFormJsonCodeList1(dboField, jLeaf, localeId, svr);

				if (section.isReadOnly() && (section.getReadOnlyExclude() == null
						|| !section.getReadOnlyExclude().contains(fieldName))) {
					jLeaf.addProperty("readOnly", true);
				}

				jFields = prepareFormJsonGroup(dboField, jFields, jLeaf, shouldGroupFields);

				if (dboField.getVal(CC.REFERENTIAL_TABLE) != null && dboField.getVal(CC.REFERENTIAL_FIELD) != null
						&& reactJson != null && reactJson.has(CC.DENORMALIZED_MNEMONIC)) {
					DbDataObject denormalizedField = Utils.findField(dboField.getVal(CC.REFERENTIAL_TABLE).toString(),
							reactJson.get(CC.DENORMALIZED_MNEMONIC).getAsString());

					if (denormalizedField != null && !dboField.getVal(CC.FIELD_NAME).toString()
							.equals(denormalizedField.getVal(CC.FIELD_NAME).toString())) {
						DbDataObject tmpDenormalizedField = new DbDataObject();
						tmpDenormalizedField.fromJson(dboField.toJson());
						tmpDenormalizedField.setVal(CC.FIELD_NAME, dboField.getAsString(CC.FIELD_NAME) + "."
								+ denormalizedField.getVal(CC.FIELD_NAME).toString());
						tmpDenormalizedField.setVal(CC.FIELD_TYPE, denormalizedField.getVal(CC.FIELD_TYPE).toString());
						jLeaf = new JsonObject();
						jLeaf = addFieldTypeToJsonObject(tmpDenormalizedField, jLeaf);
						jLeaf.addProperty(CC.TITLE_LC,
								I18n.getText(localeId, denormalizedField.getVal(CC.LABEL_CODE).toString()));
						jLeaf = prepareFormJsonCodeListDenormalized(denormalizedField,
								dboField.getVal(CC.REFERENTIAL_TABLE).toString(), jLeaf, localeId, svr);

						if (section.isReadOnly() && (section.getReadOnlyExclude() == null
								|| !section.getReadOnlyExclude().contains(fieldName))) {
							jLeaf.addProperty("readOnly", true);
						}

						jFields = prepareFormJsonGroup(tmpDenormalizedField, jFields, jLeaf, shouldGroupFields);
					}

				}

			} catch (Exception e) {
				log4j.error(e.getMessage(), e);
			}
		}

		return jFields;
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

	private static JsonObject prepareFormJsonCodeList1(DbDataObject tmpField, JsonObject jsonObj, String localeId,
			SvReader svr) {
		// prepare the list from LIST_ID on the field, or from GUI_METADATA
		if (tmpField.getVal(CC.CODE_LIST_ID) != null && (long) tmpField.getVal(CC.CODE_LIST_ID) > 0)
			return prepareFormJsonCodeListByID(tmpField, jsonObj, localeId, svr);
		else if (tmpField.getVal(CC.GUI_METADATA) != null)
			return prepareFormJsonCodeListByMetadata(tmpField.getVal(CC.GUI_METADATA).toString(), jsonObj, localeId,
					svr);
		return jsonObj;
	}

	private static JsonObject prepareFormJsonCodeListDenormalized(DbDataObject tmpField, String tableName,
			JsonObject jsonObj, String localeId, SvReader svr) throws SvException {
		if (tmpField.getVal(CC.CODE_LIST_ID) != null && (long) tmpField.getVal(CC.CODE_LIST_ID) > 0) {
			DbDataArray dbArr = svr.getObjectsByTypeId(SvReader.getTypeIdByName(tableName), null, 0, 0);
			Map<String, String> objectIdMap = dbArr.getItems().stream()
					.collect(Collectors.toMap(dbo -> dbo.getAsString(tmpField.getVal(CC.FIELD_NAME).toString()),
							dbo -> dbo.getObjectId().toString(), (existing, replacement) -> replacement));

			List<Object> values = new ArrayList<>();
			List<String> labels = new ArrayList<>();

			HashMap<String, String> listMap = null;

			try (CodeList cl = new CodeList(svr)) {
				Long codeListId = (Long) tmpField.getVal(CC.CODE_LIST_ID);
				listMap = cl.getCodeList(localeId, codeListId, true);

				List<Map.Entry<String, String>> sortedEntries = listMap.entrySet().stream()
						.sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

				for (Map.Entry<String, String> entry : sortedEntries) {
					if (objectIdMap.containsKey(entry.getKey())) {
						values.add(objectIdMap.get(entry.getKey()));
						labels.add(entry.getValue());
					}
				}

				if (!values.isEmpty()) {
					jsonObj.add("enum", GSON.toJsonTree(values));
					jsonObj.add("enumNames", GSON.toJsonTree(labels));
				}
				jsonObj.addProperty(CC.TYPE_LC, CC.STRING_LC);
			} catch (SvException e) {
				log4j.error(e);
			}
		}
		return jsonObj;
	}

	public static JsonObject prepareFormJsonCodeListByID(DbDataObject fieldDbo, JsonObject jsonObj, String locale,
			SvReader svr) {
		String fieldType = fieldDbo.getVal(CC.FIELD_TYPE).toString();

		List<Object> values = new ArrayList<>();
		List<String> labels = new ArrayList<>();

		HashMap<String, String> listMap = null;

		try (CodeList cl = new CodeList(svr)) {
			Long codeListId = (Long) fieldDbo.getVal(CC.CODE_LIST_ID);
			listMap = cl.getCodeList(locale, codeListId, true);

			List<Map.Entry<String, String>> sortedEntries = listMap.entrySet().stream()
					.sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

			for (Map.Entry<String, String> entry : sortedEntries) {
				labels.add(entry.getValue());

				if ("true".equalsIgnoreCase(entry.getKey())) {
					values.add(true);
				} else if ("false".equalsIgnoreCase(entry.getKey())) {
					values.add(false);
				} else if (CC.NUMERIC.equals(fieldType)) {
					try {
						values.add(Long.parseLong(entry.getKey()));
					} catch (NumberFormatException e) {
						values.add(entry.getKey());
					}
				} else {
					values.add(entry.getKey());
				}
			}

			if (!values.isEmpty()) {
				jsonObj.add("enum", GSON.toJsonTree(values));
				jsonObj.add("enumNames", GSON.toJsonTree(labels));
			}
		} catch (SvException e) {
			log4j.error(e);
		}

		return jsonObj;
	}

	private static JsonObject prepareFormJsonCodeListByMetadata(String guiMetadata, JsonObject jsonObj, String localeId,
			SvReader svr) {
		JsonObject jsonGUI = null;
		JsonObject jsonreactGUI = null;
		DbSearchExpression expr = new DbSearchExpression();
		ArrayList<Object> listIDs = new ArrayList<>();
		ArrayList<String> listNames = new ArrayList<>();
		JsonObject jsonObjRet = jsonObj;
		String idSetFiels = "";
		String enumName = "";
		try {
			jsonGUI = GSON.fromJson(guiMetadata, JsonObject.class);
		} catch (Exception e) {
			log4j.debug(e);
		}
		if (jsonGUI != null && jsonGUI.has(CC.REACT))
			jsonreactGUI = (JsonObject) jsonGUI.get(CC.REACT);
		if ((jsonreactGUI != null) && (jsonreactGUI.has(CC.IDTABLE) || jsonreactGUI.has("executor"))
				&& (jsonreactGUI.has(CC.IDGETFIELD))) {
			DbDataObject tableObject = jsonreactGUI.has(CC.IDTABLE)
					? SvCore.getDbtByName(jsonreactGUI.get(CC.IDTABLE).getAsString())
					: null;
			if (jsonreactGUI.has("idfield") && jsonreactGUI.has("idvalue")) {
				try {
					DbSearchCriterion critU = new DbSearchCriterion(jsonreactGUI.get("idfield").getAsString(),
							DbCompareOperand.EQUAL, jsonreactGUI.get("idvalue").getAsString());
					expr.addDbSearchItem(critU);
				} catch (SvException e) {
					log4j.debug(e);
				}
			} else {
				expr = null;
			}
			DbDataArray vData = null;
			try (SvExecManager svsec = new SvExecManager(svr)) {
				if (tableObject != null) {
					vData = svr.getObjects(expr, tableObject.getObjectId(), null, 0, 0);
				} else {
					vData = (DbDataArray) svsec.execute(jsonreactGUI.get("executor").getAsString(), null, null,
							DbDataArray.class);
				}
			} catch (SvException e) {
				log4j.debug(e);
			}
			if (jsonreactGUI.has(CC.IDSETFIELD)) {
				idSetFiels = jsonreactGUI.get(CC.IDSETFIELD).getAsString();
			}
			if (vData != null && !vData.getItems().isEmpty()) {
				for (DbDataObject item : vData.getItems()) {
					if (idSetFiels.equals("")) {
						listIDs.add(item.getObjectId());
						listNames.add(I18n.getText(localeId,
								item.getVal(jsonreactGUI.get(CC.IDGETFIELD).getAsString()).toString()));
					} else {
						if (jsonreactGUI.get(CC.IDGETFIELD).getAsString().equalsIgnoreCase(CC.LABEL_CODE)) {
							enumName = I18n.getText(localeId,
									item.getVal(jsonreactGUI.get(CC.IDGETFIELD).getAsString()).toString());
						} else {
							enumName = item.getVal(jsonreactGUI.get(CC.IDGETFIELD).getAsString()).toString();
						}
						if (!listNames.contains(enumName)) {
							listNames.add(enumName);
							switch (jsonObj.get(CC.TYPE).getAsString()) {
							case "integer":
							case "number":
								if ("OBJECT_ID".equals(idSetFiels))
									listIDs.add(item.getObjectId());
								else
									listIDs.add(Long.valueOf(item.getVal(idSetFiels).toString()));
								break;
							default:
								listIDs.add(item.getVal(idSetFiels).toString());
							}
						}
					}
				}
				JsonArray arrLongJson = new JsonArray();
				JsonElement listIDsJson = GSON.toJsonTree(listIDs);
				arrLongJson.add(listIDsJson);
				JsonElement listNamesJson = GSON.toJsonTree(listNames);
				jsonObjRet.add("enum", listIDsJson);
				jsonObjRet.add("enumNames", listNamesJson);
			}
		}
		return jsonObjRet;
	}

	private static JsonObject prepareFormJsonGroup(DbDataObject tmpObject, JsonObject jFields, JsonObject jLeaf,
			Boolean shouldGroupFields) {
		String tmpField = tmpObject.getVal(CC.FIELD_NAME).toString();
		Boolean grouppathfound = false;
		JsonObject jsonreactGUI = null;
		String groupPath = null;
		JsonObject groupValues = new JsonObject();
		JsonObject groupProperties;
		JsonObject guiMetadata = null;

		if (!shouldGroupFields) {
			jFields.add(tmpField, jLeaf);
			return jFields;
		}

		try {
			if (tmpObject.getVal(CC.GUI_METADATA) != null)
				guiMetadata = GSON.fromJson(tmpObject.getVal(CC.GUI_METADATA).toString(), JsonObject.class);
		} catch (Exception e) {
			log4j.debug(e);
		}
		if (guiMetadata != null && guiMetadata.has(CC.REACT)) {
			jsonreactGUI = (JsonObject) guiMetadata.get(CC.REACT);
			if (jsonreactGUI != null && jsonreactGUI.has(CC.GROUPPATH))
				groupPath = jsonreactGUI.get(CC.GROUPPATH).getAsString();
			if (groupPath != null) {
				if (jFields != null && jFields.has(groupPath))
					groupValues = (JsonObject) jFields.get(groupPath);
				if ((groupValues != null) && (groupValues.has(CC.PROPERTIES)))
					groupProperties = (JsonObject) groupValues.get(CC.PROPERTIES);
				else {
					groupValues = new JsonObject();
					groupProperties = new JsonObject();
				}
				grouppathfound = true;
				groupValues.addProperty(CC.TYPE_LC, CC.OBJECT_LC);
				groupValues.addProperty(CC.TITLE_LC, I18n.getText(SvConf.getDefaultLocale(), groupPath));
				groupProperties.add(tmpField, jLeaf);
				groupValues.add(CC.PROPERTIES, groupProperties);
			}
		}
		if (jFields != null)
			if (grouppathfound)
				jFields.add(groupPath, groupValues);
			else
				jFields.add(tmpField, jLeaf);
		return jFields;
	}

	public JsonObject prepareSectionUiSchema(FormSection section, String localeId) throws Exception {
		BaseObjectModel obj = factory.createObject(section.getTableName());
		JsonObject uiSchema = new JsonObject();
		for (String fieldName : section.getFieldNames()) {
			DbDataObject dboField = obj.getTableFields().get(fieldName);
			JsonObject jsonGuiMetadata = null;
			JsonObject jsonReact = null;
			JsonObject jsonUISchema = null;

			if (dboField.getVal(CC.GUI_METADATA) != null)
				jsonGuiMetadata = GSON.fromJson(dboField.getVal(CC.GUI_METADATA).toString(), JsonObject.class);
			if (jsonGuiMetadata != null && jsonGuiMetadata.has(CC.REACT))
				jsonReact = (JsonObject) jsonGuiMetadata.get(CC.REACT);
			if (jsonReact != null && jsonReact.has(CC.UISCHEMA))
				jsonUISchema = (JsonObject) jsonReact.get(CC.UISCHEMA);
			if (jsonUISchema != null) {
				fillTableUiSchemaData(uiSchema, jsonReact, jsonUISchema, dboField, fieldName);
			}
		}

		if (section.isArray()) {
			String propertyKey = section.getTableName().toLowerCase();
			JsonObject arrayUiSchema = new JsonObject();
			JsonObject uiOptionsJson = new JsonObject();

			uiOptionsJson.addProperty("orderable", false);
			arrayUiSchema.add("items", uiSchema);
			arrayUiSchema.add("ui:options", uiOptionsJson);

			JsonObject wrapper = new JsonObject();
			wrapper.add(propertyKey, arrayUiSchema);

			return wrapper;
		}

		return uiSchema;
	}

	private static void fillTableUiSchemaData(JsonObject uiSchema, JsonObject jsonreactGUI, JsonObject jsonUISchema,
			DbDataObject tempDboField, String fieldName) throws SvException {
		Boolean visible = false;
		Boolean readonly = false;
		addUISchemaToGroupPath(uiSchema, jsonreactGUI, jsonUISchema, fieldName);

		if (tempDboField.getVal(CC.REFERENTIAL_TABLE) != null && tempDboField.getVal(CC.REFERENTIAL_FIELD) != null
				&& jsonreactGUI != null && jsonreactGUI.has(CC.DENORMALIZED_MNEMONIC)) {
			boolean added = false;
			DbDataObject denormalizedField = Utils.findField(tempDboField.getVal(CC.REFERENTIAL_TABLE).toString(),
					jsonreactGUI.get(CC.DENORMALIZED_MNEMONIC).getAsString());

			if (denormalizedField != null && !tempDboField.getVal(CC.FIELD_NAME).toString()
					.equals(denormalizedField.getVal(CC.FIELD_NAME).toString())) {
				JsonObject denormalizedJsonUiSchema = jsonUISchema.deepCopy();
				if (!jsonreactGUI.has("denormalizeUiVisible")
						|| jsonreactGUI.get("denormalizeUiVisible").getAsBoolean()) {
					visible = true;
				}
				if (!jsonreactGUI.has("denormalizeUiReadonly")
						|| jsonreactGUI.get("denormalizeUiReadonly").getAsBoolean()) {
					readonly = true;
				}
				if (denormalizedField.getVal(CC.GUI_METADATA) != null) {
					JsonObject denormalizedGuiMetadata = GSON
							.fromJson(denormalizedField.getVal(CC.GUI_METADATA).toString(), JsonObject.class);
					if (denormalizedGuiMetadata.has(CC.REACT)) {
						JsonObject denormalizedJsonReactGui = denormalizedGuiMetadata.getAsJsonObject(CC.REACT);

						if (denormalizedJsonReactGui.has(CC.UISCHEMA)) {
							denormalizedJsonUiSchema = denormalizedJsonReactGui.getAsJsonObject(CC.UISCHEMA);
						}
						if (visible && denormalizedJsonUiSchema.has("ui:widget")
								&& denormalizedJsonUiSchema.get("ui:widget").getAsString().equals("hidden")) {
							denormalizedJsonUiSchema.remove("ui:widget");
						} else if (!visible) {
							denormalizedJsonUiSchema.addProperty("ui:widget", "hidden");
						}
						if (!readonly && denormalizedJsonUiSchema.has("ui:readonly")
								&& denormalizedJsonUiSchema.get("ui:readonly").getAsString().equals("true")) {
							denormalizedJsonUiSchema.remove("ui:readonly");
						} else if (readonly) {
							denormalizedJsonUiSchema.addProperty("ui:readonly", true);
						}
						addUISchemaToGroupPath(uiSchema, jsonreactGUI, denormalizedJsonUiSchema,
								tempDboField.getVal(CC.FIELD_NAME).toString() + '.'
										+ denormalizedField.getVal(CC.FIELD_NAME).toString());
						added = true;
					}
				}
				if (!added) {
					uiSchema.add(tempDboField.getVal(CC.FIELD_NAME).toString() + '.'
							+ denormalizedField.getVal(CC.FIELD_NAME).toString(), denormalizedJsonUiSchema);
				}
			}
		}
	}

	public static void addUISchemaToGroupPath(JsonObject jsonData, JsonObject jsonreactGUI, JsonObject jsonUISchema,
			String tmpField) {
		String groupPath = null;
		if (jsonreactGUI != null && jsonreactGUI.has(CC.GROUPPATH)) {
			groupPath = jsonreactGUI.get(CC.GROUPPATH).getAsString();
			JsonObject groupValues = null;
			if (jsonData.has(groupPath))
				groupValues = (JsonObject) jsonData.get(groupPath);
			if (groupValues == null)
				groupValues = new JsonObject();
			groupValues.add(tmpField, jsonUISchema);
			jsonData.add(groupPath, groupValues);
		} else
			jsonData.add(tmpField, jsonUISchema);
	}

	/**
	 * Builds the JSON Schema allOf conditional dependencies for a form based on
	 * section field dependencies.
	 * 
	 * @param section  the form section with dependencies
	 * @param localeId user locale identifier
	 * @param svr      SvReader instance
	 * @return JsonArray containing allOf conditional schemas, or null if no
	 *         dependencies
	 */
	public JsonArray buildSchemaDependencies(FormSection section, JsonObject jsonSchema, String localeId,
			SvReader svr) {
		if (section == null || section.getDependencies() == null || section.getDependencies().isEmpty()) {
			return null;
		}

		JsonArray allOf = new JsonArray();

		for (Map.Entry<String, FormSection.FieldDependency> entry : section.getDependencies().entrySet()) {
			JsonObject conditionalSchema = buildConditionalSchema(section, entry.getKey(), entry.getValue(), jsonSchema,
					localeId, svr);

			if (conditionalSchema != null) {
				allOf.add(conditionalSchema);
			}
		}

		return allOf.size() > 0 ? allOf : null;
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
	private JsonObject buildConditionalSchema(FormSection section, String dependentField,
			FormSection.FieldDependency dependency, JsonObject jsonSchema, String localeId, SvReader svr) {

		JsonObject conditional = new JsonObject();

		try {
			BaseObjectModel obj = factory.createObject(section.getTableName());
			DbDataObject dependentFieldDbo = obj.getTableFields().get(dependentField);
			DbDataObject sourceFieldDbo = obj.getTableFields().get(dependency.getDependsOn());

			if (dependentFieldDbo == null || sourceFieldDbo == null) {
				log4j.warn("Field not found for dependency: " + dependentField + " depends on "
						+ dependency.getDependsOn());
				return null;
			}

			JsonObject ifCondition = buildIfCondition(section, dependency, sourceFieldDbo);
			conditional.add("if", ifCondition);

			JsonObject thenSchema = buildThenSchema(section, dependentField, dependentFieldDbo, localeId);
			conditional.add("then", thenSchema);

			if (ifCondition.size() > 0 && thenSchema.size() > 0) {
				String groupPath = getFieldGroupPath(dependentFieldDbo);
				if (section.isArray()) {
					JsonObject sectionProp = jsonSchema.getAsJsonObject(CC.PROPERTIES)
							.getAsJsonObject(section.getTableName().toLowerCase());
					JsonObject itemsObj = sectionProp != null ? sectionProp.getAsJsonObject(CC.ITEMS_LC) : null;
					JsonObject itemProp = itemsObj != null ? itemsObj.getAsJsonObject(CC.PROPERTIES) : null;
					if (groupPath != null) {
						JsonObject groupObj = itemProp != null ? itemProp.getAsJsonObject(groupPath) : null;
						JsonObject groupProp = groupObj != null ? groupObj.getAsJsonObject(CC.PROPERTIES) : null;
						if (groupProp != null && groupProp.has(dependentField)) {
							groupProp.remove(dependentField);
						}
					} else if (itemProp != null && itemProp.has(dependentField)) {
						itemProp.remove(dependentField);
					}
				} else {
					if (groupPath != null) {
						JsonObject groupObj = jsonSchema.getAsJsonObject(CC.PROPERTIES).getAsJsonObject(groupPath);
						JsonObject groupProp = groupObj != null ? groupObj.getAsJsonObject(CC.PROPERTIES) : null;
						if (groupProp != null && groupProp.has(dependentField)) {
							groupProp.remove(dependentField);
						}
					} else {
						JsonObject propObj = jsonSchema.getAsJsonObject(CC.PROPERTIES);
						if (propObj != null && propObj.has(dependentField)) {
							propObj.remove(dependentField);
						}
					}
				}
			}
		} catch (Exception e) {
			log4j.error("Error building conditional schema for field: " + dependentField, e);
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
	private static JsonObject buildIfCondition(FormSection section, FormSection.FieldDependency dependency,
			DbDataObject sourceFieldDbo) {

		JsonObject ifCondition = new JsonObject();
		JsonObject properties = new JsonObject();

		String groupPath = getFieldGroupPath(sourceFieldDbo);

		if (section.isArray()) {
			JsonObject sectionProp = new JsonObject();
			JsonObject items = new JsonObject();
			JsonObject itemProperties = new JsonObject();

			if (groupPath != null) {
				JsonObject groupObj = new JsonObject();
				JsonObject groupProperties = new JsonObject();
				JsonObject fieldCondition = new JsonObject();
				fieldCondition.addProperty("const", dependency.getExpectedValue().toString());
				groupProperties.add(dependency.getDependsOn(), fieldCondition);
				groupObj.add("properties", groupProperties);
				itemProperties.add(groupPath, groupObj);
			} else {
				JsonObject fieldCondition = new JsonObject();
				fieldCondition.addProperty("const", dependency.getExpectedValue().toString());
				itemProperties.add(dependency.getDependsOn(), fieldCondition);
			}

			items.add("properties", itemProperties);
			sectionProp.add("items", items);
			properties.add(section.getTableName().toLowerCase(), sectionProp);
		} else {
			if (groupPath != null) {
				JsonObject groupObj = new JsonObject();
				JsonObject groupProperties = new JsonObject();
				JsonObject fieldCondition = new JsonObject();
				fieldCondition.addProperty("const", dependency.getExpectedValue().toString());
				groupProperties.add(dependency.getDependsOn(), fieldCondition);
				groupObj.add("properties", groupProperties);
				properties.add(groupPath, groupObj);
			} else {
				JsonObject fieldCondition = new JsonObject();
				fieldCondition.addProperty("const", dependency.getExpectedValue().toString());
				properties.add(dependency.getDependsOn(), fieldCondition);
			}
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
	private static JsonObject buildThenSchema(FormSection section, String dependentField,
			DbDataObject dependentFieldDbo, String localeId) {

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

		if (section.isArray()) {
			JsonObject sectionProp = new JsonObject();
			JsonObject items = new JsonObject();
			JsonObject itemProperties = new JsonObject();

			if (groupPath != null) {
				JsonObject groupObj = new JsonObject();
				JsonObject groupProperties = new JsonObject();
				groupProperties.add(dependentField, fieldSchema);
				groupObj.add("properties", groupProperties);
				itemProperties.add(groupPath, groupObj);
			} else {
				itemProperties.add(dependentField, fieldSchema);
			}

			items.add("properties", itemProperties);
			sectionProp.add("items", items);
			properties.add(section.getTableName().toLowerCase(), sectionProp);

		} else {
			if (groupPath != null) {
				JsonObject groupObj = new JsonObject();
				JsonObject groupProperties = new JsonObject();
				groupProperties.add(dependentField, fieldSchema);
				groupObj.add("properties", groupProperties);
				properties.add(groupPath, groupObj);
			} else {
				properties.add(dependentField, fieldSchema);
			}
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
}
