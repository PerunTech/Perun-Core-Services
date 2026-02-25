package com.prtech.models;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.prtech.form.CC;
import com.prtech.svarog.CodeList;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvExecManager;
import com.prtech.svarog.SvReader;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;
import com.prtech.svarog_common.DbSearchExpression;

public class JsonSchemaUtils {
	static final Logger log4j = LogManager.getLogger(JsonSchemaUtils.class.getName());
	static final Gson GSON = new Gson();

	public static JsonObject prepareFormJsonCodeList1(DbDataObject tmpField, JsonObject jsonObj,
			List<String> filterItems, String localeId, SvReader svr) {
		if (tmpField.getVal(CC.CODE_LIST_ID) != null && (long) tmpField.getVal(CC.CODE_LIST_ID) > 0)
			return prepareFormJsonCodeListByID(tmpField, jsonObj, filterItems, localeId, svr);
		else if (tmpField.getVal(CC.GUI_METADATA) != null)
			return prepareFormJsonCodeListByMetadata(tmpField.getVal(CC.GUI_METADATA).toString(), jsonObj, localeId,
					svr);
		return jsonObj;
	}

	public static JsonObject prepareFormJsonCodeListDenormalized(DbDataObject tmpField, String tableName,
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

	private static JsonObject prepareFormJsonCodeListByID(DbDataObject fieldDbo, JsonObject jsonObj,
			List<String> filterItems, String locale, SvReader svr) {
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
				if (filterItems != null && !filterItems.contains(entry.getKey())) {
					continue;
				}

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
							switch (jsonObj.get(CC.TYPE_LC).getAsString()) {
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

	/**
	 * Extracts the groupPath from a field's GUI metadata.
	 * 
	 * @param fieldDbo the field metadata
	 * @return groupPath string or null if not found
	 */
	public static String getFieldGroupPath(DbDataObject fieldDbo) {
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
	public static JsonObject addFieldTypeToJsonObject(DbDataObject fieldType, JsonObject jLeaf) {
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
