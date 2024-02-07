package com.prtech.perun.services.ws;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchExpression;
import com.prtech.svarog_common.ResponseHandler;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;
import com.prtech.svarog_common.ResponseHandler.MessageType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Path("/ElementBuilder")

public class ElementBuilder {

	static final Logger log4j = LogManager.getLogger(ElementBuilder.class.getName());

	/**
	 * 
	 * @param token
	 *            - token is param used for initiating a svarog db session
	 * @param codeListName
	 *            - name of the codelist for which we are trying to fetch db
	 *            data
	 * @param codeListTitle
	 *            - free text label (be sure it is installed) because it will be
	 *            represened as initial code value - title of the dropdown
	 * @param sortByField
	 *            - column name of the table which you want to use in order to
	 *            sort the fetched code list item by it
	 * @param httpRequest
	 * @return
	 */
	@Path("/fetchCodeListItems/{token}/{codeListName}/{codeListTitle}/{sortByField}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response fetchCodeListItems(@PathParam("token") String token, @PathParam("codeListName") String codeListName,
			@PathParam("codeListTitle") String codeListTitle, @PathParam("sortByField") String sortByField,
			@Context HttpServletRequest httpRequest) {
		JsonObject jObj;
		JsonArray jArray = new JsonArray();
		DbReader rdr = null;
		SvReader svr = null;
		try {
			svr = new SvReader(token);
			rdr = new DbReader();
			DbDataArray allCodeListItems = rdr.fetchDataPerSingleFilter(Rc.PARENT_CODE_VALUE, codeListName,
					DbCompareOperand.EQUAL, svCONST.OBJECT_TYPE_CODE, svr);

			if (allCodeListItems.size() > 0) {
				ArrayList<DbDataObject> sortedCodeListItems = allCodeListItems.getSortedItems(Rc.SORT_ORDER);

				for (DbDataObject dbo : sortedCodeListItems) {
					jObj = new JsonObject();
					jObj.addProperty("text", I18n.getText(dbo.getVal(Rc.LABEL_CODE).toString()));
					jObj.addProperty("value", dbo.getVal(Rc.CODE_VALUE).toString());
					if (dbo.getVal(Rc.LABEL_CODE).toString().contains("choose")) {
						jObj.addProperty("selected", true);
						jObj.addProperty("disabled", true);
					}
					jArray.add(jObj);
				}
			}
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			e.printStackTrace();
		} finally {
			if (svr != null)
				svr.release();
		}
		return Response.status(200).entity(jArray.toString()).build();
	}

	@Path("/getForms/{sessionId}/{multistepFormKey}")
	@GET
	@Produces("application/json")
	public Response getForms(@PathParam("sessionId") String token,
			@PathParam("multistepFormKey") String multistepFormKey, @Context HttpServletRequest httpRequest) {
		ResponseHandler jrh = new ResponseHandler();
		JsonObject jso = new JsonObject();
		SvReader svr = null;
		try {
			svr = new SvReader(token);
			jso = getForms(token, multistepFormKey);
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				log4j.error(e.getFormattedMessage());
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(401).entity(jrh.getAll().toString()).build();
			}
		} finally {
			if (svr != null) {
				svr.release();
			}
		}
		return Response.status(200).entity(jso.toString()).build();
	}

	public JsonObject getForms(String token, String multistepFormKey) {
		ResponseHandler jrh = new ResponseHandler();
		JsonArray jArray = new JsonArray();
		JsonObject jObj = new JsonObject();
		switch (multistepFormKey) {
		case Rc.LEGAL_ENTITY:
			jObj.addProperty("formId", Rc.LEGAL_ENTITY);
			jArray.add(jObj);

			jObj = new JsonObject();
			jObj.addProperty("formId", Rc.BANKACC);
			jArray.add(jObj);

			jObj = new JsonObject();
			jObj.addProperty("formId", Rc.SVAROG_CONTACT_DATA);
			jArray.add(jObj);
			break;
		case Rc.PHYSICAL_ENTITY:
			jObj.addProperty("formId", Rc.PHYSICAL_ENTITY);
			jArray.add(jObj);

			jObj = new JsonObject();
			jObj.addProperty("formId", Rc.BANKACC);
			jArray.add(jObj);

			jObj = new JsonObject();
			jObj.addProperty("formId", Rc.SVAROG_CONTACT_DATA);
			jArray.add(jObj);
			break;
		case Rc.DOCUMENT_MANAGER:
			jObj.addProperty("formId", Rc.SVAROG_FORM_TYPE);
			jArray.add(jObj);

			jObj = new JsonObject();
			jObj.addProperty("formId", Rc.SVAROG_FORM_FIELD_TYPE);
			jArray.add(jObj);

			jObj = new JsonObject();
			jObj.addProperty("formId", Rc.SVAROG_USER_GROUPS);
			jArray.add(jObj);
			break;
		default:
			break;
		}

		jrh.create(MessageType.SUCCESS, I18n.getText("plugins.loaded") + " PerunPligins", "", jArray);
		return jrh.getAll();
	}

	/**
	 * 
	 * @param token
	 *            - token is param used for initiating a svarog db session
	 * @param tableName
	 *            - name of the DB TABLE for which we are trying to fetch db
	 *            data
	 * @param filterName
	 *            column of the table which we want to search in
	 * @param filterValue
	 *            value of the searched column in order to search for it
	 * @param sortField
	 *            column name of the table which you want to use in order to
	 *            sort the fetched code list item by it
	 * @param httpRequest
	 * @return
	 */
	@Path("/fetchCodeListItemsFromTable/{token}/{fetchName}/{tableName}/{filterName}/{filterValue}/{sortField}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response fetchCodeListItemsFromTable(@PathParam("token") String token,
			@PathParam("fetchName") String fetchName, @PathParam("tableName") String tableName,
			@PathParam("filterName") String filterName, @PathParam("filterValue") String filterValue,
			@PathParam("sortField") String sortField, @Context HttpServletRequest httpRequest) {
		JsonObject jObj;
		JsonArray jArray = new JsonArray();
		ResponseHandler jrh = new ResponseHandler();
		DbReader rdr = null;
		SvReader svr = null;
		try {
			svr = new SvReader(token);
			rdr = new DbReader();
			if (filterName == null || filterName.equals("null") || filterName.equals("0") || filterName.equals("")) {
				filterName = null;
			}
			if (filterValue == null || filterValue.equals("null") || filterValue.equals("0")
					|| filterValue.equals("")) {
				filterValue = null;
			}
			DbDataArray allCodeListItems = rdr.fetchDataFromTableByFilter(tableName, filterName, filterValue, 0, svr);

			jObj = new JsonObject();
			jObj.addProperty("text", I18n.getText("choose"));
			jObj.addProperty("value", "0");
			jObj.addProperty("selected", true);
			jObj.addProperty("disabled", true);

			jArray.add(jObj);

			if (allCodeListItems.size() > 0) {
				ArrayList<DbDataObject> sortedCodeListItems = null;
				if (sortField != null && !sortField.equals("null") && !sortField.equals("0") && !sortField.equals("")) {
					sortedCodeListItems = allCodeListItems.getSortedItems(sortField);
				} else {
					sortedCodeListItems = allCodeListItems.getItems();
				}
				for (DbDataObject dbo : sortedCodeListItems) {
					jObj = new JsonObject();
					jObj.addProperty("text", I18n.getText(dbo.getVal(fetchName).toString()));
					jObj.addProperty("value", dbo.getObjectId());

					jArray.add(jObj);
				}
			}
			jrh.create(MessageType.SUCCESS, I18n.getText("fetch_code_list.success.from_table"),
					I18n.getText("fetch_code_list.success.from_table"), jArray);
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				log4j.error(e.getFormattedMessage());
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(401).entity(jrh.getAll().toString()).build();
			}
			log4j.error(e.getFormattedMessage(), e);
			jrh.create(MessageType.ERROR, I18n.getText("fetch_code_list.error.from_table"),
					I18n.getText("fetch_code_list.error.from_table"), new JsonObject());
		} finally {
			if (svr != null)
				svr.release();
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}
	
	/**
	 * Web service to return any table using one filter "like" for one field
	 * only , it will also return svarog repo fields
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param tableName
	 *            String table from which we want to get data
	 * @param fieldName
	 *            String name of the field that we try to filter
	 * @param fieldValue
	 *            String value that we are trying to find, will be cast to
	 *            Integer for numeric values
	 * @param recordNumber
	 *            Integer how many records we want to pull from the table
	 * 
	 * @return Json with all objects found
	 */
	@Path("/getTableWithLike/{session_id}/{table_name}/{fieldNAme}/{fieldValue}/{no_rec}")
	@GET
	@Produces("application/json")
	public Response getTableWithLike(@PathParam("session_id") String sessionId,
			@PathParam("table_name") String tableName, @PathParam("fieldNAme") String fieldName,
			@PathParam("fieldValue") String fieldValue, @PathParam("no_rec") Integer recordNumber,
			@Context HttpServletRequest httpRequest) {
		SvReader svr = null;
		JsonArray retJson = new JsonArray();
		ResponseHandler jrh = new ResponseHandler();
		String fieldWithSpecialCharacter = fieldValue;
		String[] tablesUsedArray = new String[1];
		Boolean[] tableShowArray = new Boolean[1];
		int tablesusedCount = 1;
		try {
			svr = new SvReader(sessionId);
			Long tableID = WsReactElements.findTableType(tableName);
			if (fieldValue != null && fieldValue.contains("%2F")) {
				fieldWithSpecialCharacter = java.net.URLDecoder.decode(fieldValue, StandardCharsets.UTF_8.name());
			}
			DbSearchExpression expr = new DbSearchExpression();
			DbSearchCriterion critU = new DbSearchCriterion(fieldName.toUpperCase(), DbCompareOperand.LIKE,
					'%' + fieldWithSpecialCharacter + '%');
			expr.addDbSearchItem(critU);
			DbDataArray vData = svr.getObjects(expr, tableID, null, recordNumber, 0);
			tablesUsedArray[0] =SvReader.getDbt(tableID).getVal(Rc.TABLE_NAME).toString();
			tableShowArray[0] = true;
			retJson =WsReactElements.prapareTableQueryData(vData, tablesUsedArray, tableShowArray, tablesusedCount, true, svr, false, null); 
			jrh.create(MessageType.SUCCESS, I18n.getText("success.get_data"),
					I18n.getText("success.get_data"), retJson);	
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				log4j.error(e.getFormattedMessage());
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(401).entity(jrh.getAll().toString()).build();
			}
			log4j.error(e.getFormattedMessage(), e);
			jrh.create(MessageType.ERROR, I18n.getText("error.get_data"),
					I18n.getText("error.get_data"), new JsonObject());
		} catch (UnsupportedEncodingException e) {
			log4j.error(e.toString(), e);
			jrh.create(MessageType.ERROR, I18n.getText("error.get_data"),
					I18n.getText("error.get_data"), new JsonObject());
		} finally {
			if(svr!=null) {
				svr.release();
			}
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}
}
