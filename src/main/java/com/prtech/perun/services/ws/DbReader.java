package com.prtech.perun.services.ws;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbQueryExpression;
import com.prtech.svarog_common.DbQueryObject;
import com.prtech.svarog_common.DbQueryObject.DbJoinType;
import com.prtech.svarog_common.DbQueryObject.LinkType;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;
import com.prtech.svarog_common.DbSearchExpression;

public class DbReader {

	static final Logger log4j = LogManager.getLogger(DbReader.class.getName());

	public DbCompareOperand getDbCompareOperand(String operand) {
		switch (operand) {
		case Rc.EQUAL:
			return DbCompareOperand.EQUAL;
		case Rc.LIKE:
			return DbCompareOperand.LIKE;
		case Rc.ILIKE:
			return DbCompareOperand.ILIKE;
		case Rc.BETWEEN:
			return DbCompareOperand.BETWEEN;
		default:
			return null;
		}
	}

	public static DbDataObject findField(String tableName, String fieldName, SvReader svr) throws SvException {
		DbDataObject dbField = null;
		DbDataObject tableObject = SvCore.getDbtByName(tableName);
		DbDataArray dboFieldsPerTable = SvCore.getFields(tableObject.getObjectId());
		for (DbDataObject dbo : dboFieldsPerTable.getItems()) {
			if (dbo.getVal(Rc.FIELD_NAME).toString().equals(fieldName)) {
				dbField = dbo;
				break;
			}
		}
		return dbField;
	}

	/**
	 * Simple help method for fetching DB object by single filter
	 * 
	 * @param objectType  The id of the type/table
	 * @param columnName  The column name to search by
	 * @param columnValue The value to be searched for
	 * @param svr         Standard SvCore instance
	 * @return
	 */
	public DbDataObject searchDbObjectBySingleFilter(Long objectType, String columnName, Object columnValue,
			SvReader svr) {
		return searchDbObjectBySingleFilter(DbCompareOperand.EQUAL, objectType, columnName, columnValue, svr);
	}

	/**
	 * Simple method for searching object by single filter
	 * 
	 * @param objectType
	 * @param columnName
	 * @param value
	 * @param svr
	 * @return
	 */
	public DbDataObject searchDbObjectBySingleFilter(DbCompareOperand operand, Long objectType, String columnName,
			Object value, SvReader svr) {
		DbDataObject dbo = null;
		try {
			DbSearchCriterion cr1 = new DbSearchCriterion(columnName, operand, value);
			DbDataArray arrFoundDbObjects = svr.getObjects(cr1, objectType, null, 1, 0);
			if (!arrFoundDbObjects.isEmpty()) {
				dbo = arrFoundDbObjects.get(0);
			}
		} catch (SvException e) {
			log4j.error(e);
		}
		return dbo;
	}

	/**
	 * Simple method for searching objects by single filter
	 * 
	 * @param objectType
	 * @param columnName
	 * @param value
	 * @param svr
	 * @return
	 */
	public DbDataArray searchObjectsBySingleFilter(DbCompareOperand operand, Long objectType, String columnName,
			Object value, SvReader svr) {
		DbDataArray dba = null;
		try {
			DbSearchCriterion cr1 = new DbSearchCriterion(columnName, operand, value);
			dba = svr.getObjects(cr1, objectType, null, 0, 0);
		} catch (SvException e) {
			log4j.error(e);
		}
		return dba;
	}

	/**
	 * Find 'link_type' for given linkName
	 * 
	 * @param linkName name of the link
	 * @param SvReader
	 * @return String
	 */
	private static long findLinkTypeId(String linkName, SvReader svr) throws SvException {
		Long result = 0L;
		if (linkName != null && linkName.trim().length() > 2) {
			DbSearchExpression expr = new DbSearchExpression();
			DbDataObject dbtLinkType = SvReader.getDbt(svCONST.OBJECT_TYPE_LINK_TYPE);
			DbSearchCriterion dbc = new DbSearchCriterion("LINK_TYPE", DbCompareOperand.EQUAL, linkName);
			expr.addDbSearchItem(dbc);
			DbQueryObject dqo = new DbQueryObject(dbtLinkType, expr, null, null);
			DbDataArray objects = svr.getObjects(dqo, 0, 0);
			if (objects.size() == 1) {
				result = objects.get(0).getObjectId();
			}
		}
		return result;
	}

	/**
	 * Find the link for given objects ids and link name
	 * 
	 * @param linkObjId1 entity/village object id
	 * @param linkObjId2 disaster claim object id
	 * @param linkName   name of the link between the two objects
	 * @param svr        SvReader instance
	 * @return DbDataObject
	 */
	public static DbDataObject findLink(Long linkObjId1, Long linkObjId2, String linkName, SvReader svr)
			throws SvException {
		DbDataObject result = null;
		DbSearchExpression expr = new DbSearchExpression();
		DbDataObject dbtLink = SvReader.getDbt(svCONST.OBJECT_TYPE_LINK);
		if (linkName != null && linkName.trim().length() > 2) {
			long linkTypeId = findLinkTypeId(linkName, svr);
			if (linkObjId1 != 0L && linkObjId2 != 0L && linkTypeId != 0L) {
				DbSearchCriterion dbc1 = new DbSearchCriterion("LINK_OBJ_ID_1", DbCompareOperand.EQUAL, linkObjId1);
				DbSearchCriterion dbc2 = new DbSearchCriterion("LINK_OBJ_ID_2", DbCompareOperand.EQUAL, linkObjId2);
				DbSearchCriterion dbc3 = new DbSearchCriterion("LINK_TYPE_ID", DbCompareOperand.EQUAL, linkTypeId);
				expr.addDbSearchItem(dbc1).addDbSearchItem(dbc2).addDbSearchItem(dbc3);
				DbQueryObject dqo = new DbQueryObject(dbtLink, expr, null, null);
				DbDataArray objects = svr.getObjects(dqo, 0, 0);
				if (objects.size() == 1) {
					result = objects.get(0);
				}
			}
		}
		return result;
	}

	/**
	 * Find the link for given objects ids and link type
	 * 
	 * @param linkObjId1 entity/village object id
	 * @param linkObjId2 disaster claim object id
	 * @param linkTypeId object id of the link type
	 * @param svr        The SvCore to be used for connectivity
	 * @return DbDataObject The link object
	 * @throws SvException Any underlying exception
	 */
	public static DbDataObject findLink(Long linkObjId1, Long linkObjId2, Long linkTypeId, SvReader svr)
			throws SvException {
		DbDataObject result = null;
		DbSearchExpression expr = new DbSearchExpression();
		DbDataObject dbtLink = SvReader.getDbt(svCONST.OBJECT_TYPE_LINK);
		if (linkObjId1 != 0L && linkObjId2 != 0L && linkTypeId != 0L) {
			DbSearchCriterion dbc1 = new DbSearchCriterion("LINK_OBJ_ID_1", DbCompareOperand.EQUAL, linkObjId1);
			DbSearchCriterion dbc2 = new DbSearchCriterion("LINK_OBJ_ID_2", DbCompareOperand.EQUAL, linkObjId2);
			DbSearchCriterion dbc3 = new DbSearchCriterion("LINK_TYPE_ID", DbCompareOperand.EQUAL, linkTypeId);
			expr.addDbSearchItem(dbc1).addDbSearchItem(dbc2).addDbSearchItem(dbc3);
			DbQueryObject dqo = new DbQueryObject(dbtLink, expr, null, null);
			DbDataArray objects = svr.getObjects(dqo, 0, 0);
			if (objects.size() == 1) {
				result = objects.get(0);
			}
		}
		return result;
	}

	public static DbDataObject getPermissionFromDbArray(DbDataArray dbArrayPermissions, String permission) {
		DbDataObject permDbo = null;
		for (DbDataObject dbo : dbArrayPermissions.getItems()) {
			if (dbo.getVal(Rc.LABEL_CODE).equals(permission)) {
				permDbo = dbo;
				break;
			}
		}
		return permDbo;
	}

	public boolean canAccess(String permissionCode, List<String> accessTypes, SvReader svr) throws SvException {
		Boolean access = true;
		if (!svr.isAdmin()) {
			try (SvSecurity svs = new SvSecurity(svr)) {
				DbDataArray dbArrayACLPermissions = svs.getPermissions(svr.getInstanceUser(), svr);
				DbDataObject permDbo = getPermissionFromDbArray(dbArrayACLPermissions, permissionCode);
				if (permDbo != null && accessTypes.contains(permDbo.getVal("ACCESS_TYPE").toString())) {
					access = true;
				} else {
					access = false;
				}
			}
		}
		return access;
	}
	
	public boolean canAccess(String permissionCode, List<String> accessTypes, DbDataObject dboUser,SvReader svr) throws SvException {
		Boolean access = true;
		if (!svr.isAdmin()) {
			try (SvSecurity svs = new SvSecurity(svr)) {
				DbDataArray dbArrayACLPermissions = svs.getPermissions(dboUser, svr);
				DbDataObject permDbo = getPermissionFromDbArray(dbArrayACLPermissions, permissionCode);
				if (permDbo != null && accessTypes.contains(permDbo.getVal("ACCESS_TYPE").toString())) {
					access = true;
				} else {
					access = false;
				}
			}
		}
		return access;
	}

	public boolean canAccess(DbDataObject dboUserGroup, String permissionCode, SvReader svr) throws SvException {
		Boolean access = false;
		DbDataObject dboAcl = getPermissionPerUserOrUserGroup(dboUserGroup, permissionCode, svr);
		if (dboAcl != null && !dboAcl.getVal("ACCESS_TYPE").equals("NONE")) {
			access = true;
		}
		return access;
	}

	public DbDataObject getPermissionPerUserOrUserGroup(DbDataObject dboUserGroup, String permissionCode, SvReader svr)
			throws SvException {
		DbDataObject dboAcl = null;
		DbDataObject dbtAcl = SvCore.getDbt(svCONST.OBJECT_TYPE_ACL);
		DbDataObject dbtSidAcl = SvCore.getDbt(svCONST.OBJECT_TYPE_SID_ACL);
		DbDataObject dbtSvarogUserOrUserGroup = SvCore.getDbt(svCONST.OBJECT_TYPE_GROUP);
		DbSearchCriterion dsc1 = new DbSearchCriterion("LABEL_CODE", DbCompareOperand.EQUAL, permissionCode);
		DbSearchCriterion dsc2 = new DbSearchCriterion("GROUP_NAME", DbCompareOperand.EQUAL,
				dboUserGroup.getVal("GROUP_NAME"));
		DbQueryObject dqoAcl = new DbQueryObject(dbtAcl, dsc1, DbJoinType.INNER, null, LinkType.CUSTOM_FREETEXT, null,
				null);
		dqoAcl.setCustomFreeTextJoin(" on tbl0.OBJECT_ID = tbl1.ACL_OBJECT_ID ");
		DbQueryObject dqoSidAcl = new DbQueryObject(dbtSidAcl, null, DbJoinType.INNER, null, LinkType.CUSTOM_FREETEXT,
				null, null);
		dqoSidAcl.setCustomFreeTextJoin(" on tbl2.OBJECT_ID = tbl1.SID_OBJECT_ID ");
		DbQueryObject dqoSvarogUserOrUserGroup = new DbQueryObject(dbtSvarogUserOrUserGroup, dsc2, null, null);
		dqoAcl.setIsReturnType(true);
		DbQueryExpression dqe = new DbQueryExpression();
		dqe.addItem(dqoAcl);
		dqe.addItem(dqoSidAcl);
		dqe.addItem(dqoSvarogUserOrUserGroup);
		DbDataArray result = svr.getObjects(dqe, null, null);
		if (!result.isEmpty()) {
			dboAcl = result.get(0);
		}
		return dboAcl;
	}

	/**
	 * Method that casts JsonElement object into appropriate type according field
	 * type. This method is useful if we get the value in JsonObject (ex.
	 * {@link #fetchDbaObjectsByMultipleFilters(JsonObject, JsonArray, SvReader)}
	 * 
	 * @param dboField DbDataObject instance of type SVAROG_FIELD
	 * @param jValue   JsonElement instance as value
	 * @return Object with appropriate instance type
	 */
	public Object castJsonElementValueAccordingFieldType(DbDataObject dboField, JsonElement jValue) {
		Object castedValue = null;
		if (!Objects.isNull(dboField)) {
			switch (dboField.getVal(Rc.FIELD_TYPE).toString()) {
			case Rc.NVARCHAR:
				castedValue = jValue.getAsString();
				break;
			case Rc.NUMERIC:
				castedValue = jValue.getAsLong();
				break;
			case Rc.DATE:
				Date tempDate = Date.valueOf(jValue.getAsString());
				castedValue = tempDate;
				break;
			case Rc.DATETIME:
			case Rc.TIMESTAMP:
				DateTime tempDateTime = DateTime.parse(jValue.getAsString());
				castedValue = tempDateTime;
				break;
			case Rc.BOOLEAN:
				Boolean tempBoolean = Boolean.valueOf(jValue.toString());
				castedValue = tempBoolean;
				break;
			default:
				castedValue = jValue;
			}
		} else {
			castedValue = jValue.getAsString();
		}
		return castedValue;
	}

	public static JsonArray getStringAsJsonArray(String strigifiedJsonArray) {
		JsonArray jArray = new JsonArray();
		try {
			jArray = (new Gson()).fromJson(strigifiedJsonArray, JsonArray.class);
		} catch (Exception e) {
			jArray = (new Gson()).fromJson("[" + strigifiedJsonArray + "]", JsonArray.class);
		}
		return jArray;
	}

	protected DbDataArray fetchDataPerSingleFilter(String columnName, String columnValue,
			DbCompareOperand compareOperand, Long objTypeId, SvReader svr) throws SvException {
		return fetchDataPerSingleFilter(columnName, columnValue, compareOperand, objTypeId, 0, svr);
	}

	protected DbDataArray fetchDataPerSingleFilter(String columnName, String columnValue,
			DbCompareOperand compareOperand, Long objTypeId, Integer rowLimit, SvReader svr) throws SvException {
		DbDataArray result = null;
		DbSearchCriterion cr1 = null;
		if (compareOperand.equals(DbCompareOperand.LIKE)) {
			cr1 = new DbSearchCriterion(columnName, compareOperand, columnValue + "%");
		} else {
			cr1 = new DbSearchCriterion(columnName, compareOperand, columnValue);
		}
		result = svr.getObjects(new DbSearchExpression().addDbSearchItem(cr1), objTypeId, null, rowLimit, 0);
		return result;
	}

	protected DbDataArray fetchDataFromTableByFilter(String tableName, String columnName, String columnValue,
			Integer rowLimit, SvReader svr) throws SvException {
		DbDataArray result = null;
		DbSearchCriterion cr1 = null;
		DbSearchCriterion cr2 = null;
		DbSearchExpression exp = null;
		if (columnName != null && columnValue != null) {
			cr1 = new DbSearchCriterion(columnName, DbCompareOperand.EQUAL, columnValue);
		}
		if (tableName.equals(Rc.SVAROG_TABLE)) {
			cr2 = new DbSearchCriterion(Rc.SYSTEM_TABLE, DbCompareOperand.EQUAL, false);
		}

		if (cr1 != null || cr2 != null) {
			exp = new DbSearchExpression();
			if (cr1 != null)
				exp.addDbSearchItem(cr1);
			if (cr2 != null)
				exp.addDbSearchItem(cr2);
		}

		result = svr.getObjects(exp, SvReader.getTypeIdByName(tableName), null, rowLimit, 0);
		return result;
	}

	public DbDataArray getDbaNotesAccordingParentIdAndNoteName(Long parentId, String noteName, SvReader svr)
			throws SvException {
		DbSearchCriterion cr1 = new DbSearchCriterion(Rc.PARENT_ID, DbCompareOperand.EQUAL, parentId);
		DbSearchCriterion cr2 = new DbSearchCriterion(Rc.NOTE_NAME, DbCompareOperand.EQUAL, noteName);
		return svr.getObjects(new DbSearchExpression().addDbSearchItem(cr1).addDbSearchItem(cr2),
				svCONST.OBJECT_TYPE_NOTES, new DateTime(), 0, 0);
	}

	public JsonObject getDboUserDetailsAsJsonObject(DbDataObject dboUser) {
		JsonObject jsonObj = new JsonObject();
		String userName = dboUser.getVal("USER_NAME").toString();
		String eMail = dboUser.getVal("E_MAIL").toString();
		jsonObj.addProperty("userName", userName);
		jsonObj.addProperty("pin", dboUser.getVal("PIN") == null ? Rc.EMPTY_STRING : dboUser.getVal("PIN").toString());
		jsonObj.addProperty("eMail", eMail);
		return jsonObj;
	}

	/**
	 * This method create DbSearchExpression by using one JsonObject (in which we
	 * include the basic data: object_type, row_limit, offset, ref_date,
	 * sort_by_field and sort_order) and one JsonArray (which include multiple
	 * filters).
	 * 
	 * DbOperands: EQUAL, LIKE and ILIKE:
	 * 
	 * The basic format of the JsonArray is: "multipleFilterData": [{ "fieldName":
	 * "BID", "fieldValue": 123, "dbOperand": "EQUAL", "nextLogicOperand": "OR" }].
	 * 
	 * DbOperand: BETWEEN:
	 * 
	 * Using this method for searching in given date interval and with operand
	 * BETWEEN - for now, use this JsonArray format: [{ "fieldName": "DATE_FROM",
	 * "fieldName2": "DATE_TO", "fieldValue": "2020-10-13", "fieldValue2":
	 * "2021-10-30", "dbOperand": "BETWEEN", "nextLogicOperand": "AND" }]. The keys
	 * fieldName, fieldName2, fieldValue and fieldValue2 are required.
	 * 
	 */
	public JsonArray fetchDbaObjectsByMultipleFilters(JsonObject basicData, JsonArray jArrayMultipleFilters,
			SvReader svr) throws SvException {
		String[] tablesUsedArray = new String[1];
		Boolean[] tableShowArray = new Boolean[1];
		int tablesusedCount = 1;

		int offset = 0;
		int rowLimit = 0;
		Long objectType = 0L;
		String sortOrder = "ASC";
		String sortByField = null;
		DateTime refDate = null;
		DbDataObject dboTable = null;
		JsonArray jArrayResultSet = new JsonArray();
		if (basicData.has(Rc.OFFSET)) {
			offset = basicData.get(Rc.OFFSET).getAsInt();
		}
		if (basicData.has(Rc.ROW_LIMIT)) {
			rowLimit = basicData.get(Rc.ROW_LIMIT).getAsInt();
		}
		if (basicData.has(Rc.OBJECT_TYPE)) {
			objectType = basicData.get(Rc.OBJECT_TYPE).getAsLong();
			dboTable = SvReader.getDbt(objectType);
		}
		if (basicData.has(Rc.REF_DATE)) {
			refDate = new DateTime(basicData.get(Rc.REF_DATE).getAsString());
		}
		if (basicData.has("SORT_BY_FIELD")) {
			sortByField = basicData.get("SORT_BY_FIELD").getAsString();
			if (basicData.has("SORT_ORDER")) {
				sortOrder = basicData.get("SORT_ORDER").getAsString();
			}
		}

		DbSearchExpression dbse = new DbSearchExpression();
		for (JsonElement jTempElement : jArrayMultipleFilters) {
			JsonObject jSingleCriterion = jTempElement.getAsJsonObject();
			String fieldName = Rc.EMPTY_STRING;
			String fieldName2 = Rc.EMPTY_STRING;
			DbDataObject dboField = null;
			Object fieldValue = null;
			Object fieldValue2 = null;
			String dbCompareOperand = Rc.EMPTY_STRING;
			String nextLogicOperand = Rc.EMPTY_STRING;
			if (jSingleCriterion.has(Rc.PDP_FIELD_NAME)) {
				fieldName = jSingleCriterion.get(Rc.PDP_FIELD_NAME).getAsString();
				dboField = findField(dboTable.getVal(Rc.TABLE_NAME).toString(), fieldName, svr);
			}
			if (jSingleCriterion.has(Rc.PDP_FIELD_NAME_2)) {
				fieldName2 = jSingleCriterion.get(Rc.PDP_FIELD_NAME_2).getAsString();
			}
			if (jSingleCriterion.has(Rc.PDP_FIELD_VALUE)) {
				fieldValue = castJsonElementValueAccordingFieldType(dboField, jSingleCriterion.get(Rc.PDP_FIELD_VALUE));
			}
			if (jSingleCriterion.has(Rc.PDP_FIELD_VALUE_2)) {
				fieldValue2 = castJsonElementValueAccordingFieldType(dboField,
						jSingleCriterion.get(Rc.PDP_FIELD_VALUE_2));
			}
			if (jSingleCriterion.has(Rc.PDP_DB_OPERAND)) {
				dbCompareOperand = jSingleCriterion.get(Rc.PDP_DB_OPERAND).getAsString();
			}
			if (jSingleCriterion.has(Rc.PDP_NEXT_LOGIC_OPERAND)) {
				nextLogicOperand = jSingleCriterion.get(Rc.PDP_NEXT_LOGIC_OPERAND).getAsString();
			}

			switch (dbCompareOperand) {
			case Rc.LIKE:
			case Rc.ILIKE:
				fieldValue = "%" + fieldValue + "%";
				break;
			case Rc.BETWEEN:
				DbSearchCriterion cr2 = new DbSearchCriterion(fieldName2, getDbCompareOperand(dbCompareOperand),
						fieldValue, fieldValue2);
				dbse.addDbSearchItem(cr2);
				break;
			default:
				break;
			}
			DbSearchCriterion cr1 = new DbSearchCriterion(fieldName, getDbCompareOperand(dbCompareOperand), fieldValue,
					fieldValue2);
			if (!nextLogicOperand.equalsIgnoreCase(Rc.EMPTY_STRING)) {
				cr1.setNextCritOperand(nextLogicOperand);
			}
			dbse.addDbSearchItem(cr1);
		}
		tablesUsedArray[0] = dboTable.getVal(Rc.TABLE_NAME).toString();
		tableShowArray[0] = true;

		DbDataArray dbaResult = svr.getObjects(dbse, objectType, refDate, rowLimit, offset);
		ArrayList<DbDataObject> items = null;
		if (!Objects.isNull(sortByField)) {
			items = dbaResult.getSortedItems(sortByField, true);
			if (Rc.DESC.equals(sortOrder)) {
				Collections.reverse(items);
			}
		} else {
			items = dbaResult.getItems();
		}

		jArrayResultSet = WsReactElements.prapareTableQueryData(items, tablesUsedArray, tableShowArray, tablesusedCount,
				true, svr, false, null);
		return jArrayResultSet;
	}

	/**
	 * Method that return translated object type
	 * 
	 * @param objType
	 * @param svr
	 * @return
	 */
	public String getTableNameByType(Long objType, SvReader svr) {
		String result = Rc.EMPTY_STRING;
		try {
			DbDataObject dbo = svr.getObjectById(objType, svCONST.OBJECT_TYPE_TABLE, null);
			if (dbo != null) {
				result = dbo.getVal(Rc.TABLE_NAME).toString().toUpperCase();
			}
		} catch (SvException e) {
			log4j.trace("Failed to translate");
		}
		return result;
	}

	/**
	 * Used in
	 * {@link WsReactElements#getTableFieldList(String, String, javax.servlet.http.HttpServletRequest)}
	 * 
	 * @param tableName
	 * @param svr
	 * @return
	 * @throws SvException
	 */
	public JsonArray getTableFieldList(String tableName, SvReader svr) throws SvException {
		JsonArray jArray = new JsonArray();
		DbDataObject tableObject = SvCore.getDbtByName(tableName);
		DbDataArray typetoGet = svr.getObjectsByParentId(tableObject.getObjectId(), svCONST.OBJECT_TYPE_FIELD, null, 0,
				0, Rc.SORT_ORDER);
		if (!typetoGet.getItems().isEmpty()) {
			if (tableName.equalsIgnoreCase("sample") || tableName.equalsIgnoreCase("svarog_score")) {
				JsonArray tmpJArray = WsReactElements.prapareSvarogFieldsFull(tableName, true, svr);
				for (int i = 0; i < tmpJArray.size(); i++) {
					JsonObject tmpJObj = (JsonObject) tmpJArray.get(i);
					if (tmpJObj.get(Rc.FIELD_NAME).getAsString().equalsIgnoreCase("status")
							|| tmpJObj.get(Rc.FIELD_NAME).getAsString().equalsIgnoreCase("dt_insert"))
						jArray.add(tmpJObj);
				}
			} else {
				JsonArray tmpJArray = WsReactElements.prapareSvarogFields1(tableName, svr);
				for (int i = 0; i < tmpJArray.size(); i++) {
					jArray.add((JsonObject) tmpJArray.get(i));
				}
			}
		}
		DbDataObject tempDboField = null;
		for (int i = 0; i < typetoGet.getItems().size(); i++) {
			tempDboField = typetoGet.getItems().get(i);
			String tmpField = tempDboField.getVal(Rc.FIELD_NAME).toString();
			if (WsReactElements.processField(tmpField)) {
				JsonObject tryObject = WsReactElements.prapareObjectField1(tableName, tempDboField, svr);
				if (tryObject.toString().length() > 5)
					jArray.add(tryObject);
				if (tmpField.equals(Rc.LABEL_CODE)) {
					JsonObject tmpJsonObj = (new Gson()).fromJson(tryObject, JsonObject.class);
					tmpJsonObj.addProperty("key", "FIELD." + tryObject.get("key").getAsString());
					tmpJsonObj.addProperty(Rc.FIELD_NAME, "FIELD." + tryObject.get(Rc.FIELD_NAME).getAsString());
					tmpJsonObj.addProperty("name", I18n.getText(WsReactElements.getLocaleId(svr),
							"field." + tempDboField.getVal(Rc.LABEL_CODE).toString()));
					tmpJsonObj.addProperty("editable", false);
					jArray.add(tmpJsonObj);
				}
				if (tempDboField.getVal(Rc.REFERENTIAL_TABLE) != null) {
					JsonObject jsonObj = null;
					JsonObject jsonreactGUI = null;
					if (tempDboField.getVal(Rc.GUI_METADATA) != null)
						jsonObj = (new Gson()).fromJson(tempDboField.getVal(Rc.GUI_METADATA).toString(),
								JsonObject.class);
					if (jsonObj != null && jsonObj.has(Rc.REACT))
						jsonreactGUI = (JsonObject) jsonObj.get(Rc.REACT);
					if (jsonreactGUI.has(Rc.DENORMALIZED_MNEMONIC) && (!jsonreactGUI.has("denormalizeVisible")
							|| jsonreactGUI.get("denormalizeVisible").getAsBoolean())) {
						DbDataObject denormalizedField = findField(tempDboField.getVal(Rc.REFERENTIAL_TABLE).toString(),
								jsonreactGUI.get(Rc.DENORMALIZED_MNEMONIC).getAsString(), svr);
						tryObject = WsReactElements.prapareObjectField1(
								tempDboField.getVal(Rc.REFERENTIAL_TABLE).toString(), denormalizedField, svr);
						if (tryObject.toString().length() > 5)
							jArray.add(tryObject);
					}
				}
			}
		}
		return jArray;
	}

	/**
	 * Method that checks if some user is related to proper permission key thorugh
	 * its session
	 * 
	 * @param actionPermissionKey The ACL key
	 * @param svr                 The SvReader instance
	 * @throws SvException
	 */
	public void checkIfCurrentUserHasActionPermission(String actionPermissionKey, SvReader svr) throws SvException {
		if (!svr.isAdmin()) {
			try (SvSecurity svs = new SvSecurity(svr)) {
				DbDataArray dbArrayACLPermissions = svs.getPermissions(svr.getInstanceUser(), svr);
				if (!checkIfDbDataArrayContainsPermission(dbArrayACLPermissions, actionPermissionKey)) {
					log4j.debug("Missing permission code: {}", actionPermissionKey);
					throw new SvException("system.error.user_not_authorized_to_perform_action", svr.getInstanceUser());
				}
			}
		}
	}

	public static boolean checkIfDbDataArrayContainsPermission(DbDataArray dbArrayPermissions, String permission) {
		boolean result = false;
		for (DbDataObject dbo : dbArrayPermissions.getItems()) {
			if (dbo.getVal(Rc.LABEL_CODE).equals(permission)) {
				result = true;
				break;
			}
		}
		return result;
	}
}
