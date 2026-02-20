package com.prtech.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.prtech.perun.PerunUtil;
import com.prtech.perun.services.ws.DbReader;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;
import com.prtech.svarog_common.ResponseHandler;
import com.prtech.svarog_common.ResponseHandler.MessageType;

@Path("/WsModel")
public class WsModel {
	static final Logger log4j = LogManager.getLogger(WsModel.class.getName());

	/**
	 * Save objects using the BaseObjectModel architecture.
	 *
	 * @param sessionId
	 * @param formVals
	 * @param httpRequest
	 * @return
	 */
	@Path("/saveObject/{sessionId}")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response saveObject(@PathParam("sessionId") String sessionId, String entity,
			@Context HttpServletRequest httpRequest) {
		JsonObject requestData = new JsonObject();
		String locale = SvConf.getDefaultLocale();
		ResponseHandler jrh = new ResponseHandler();
		Long objectId = 0L;
		Long parentId = 0L;
		String tableName = CC.EMPTY_STRING;
		Boolean checkParentBusinessPeriod = null;
		BaseObjectModel obj = null;
		List<String> errors = new ArrayList<String>(0);
		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			setAutoCommit(Arrays.asList(svr, svw), false);
			locale = svr.getUserLocaleId(svr.getInstanceUser());
			try {
				requestData = new Gson().fromJson(entity, JsonObject.class);
			} catch (JsonSyntaxException e) {
				jrh.create(MessageType.ERROR, I18n.getText(locale, "error.bad_json"), CC.EMPTY_STRING,
						new JsonObject());
				return Response.ok(jrh.getAll().toString()).build();
			}
			if (requestData.has(CC.OBJECT_ID)) {
				objectId = requestData.get(CC.OBJECT_ID).getAsLong();
			}
			if (requestData.has(CC.PARENT_ID)) {
				parentId = requestData.get(CC.PARENT_ID).getAsLong();
			}
			if (requestData.has(CC.CHECK_BUSINESS_PERIOD)
					&& requestData.get(CC.CHECK_BUSINESS_PERIOD).getAsJsonPrimitive().isBoolean()) {
				checkParentBusinessPeriod = requestData.get(CC.CHECK_BUSINESS_PERIOD).getAsBoolean();
			}
			if (requestData.has(CC.TABLE_NAME)) {
				tableName = requestData.get(CC.TABLE_NAME).getAsString();
				obj = ModelFactoryRegistry.createObject(tableName, requestData);
				if (obj != null) {
					obj.setSkipCheck(false);
					if (checkParentBusinessPeriod != null) {
						obj.setCheckBusinessPeriod(checkParentBusinessPeriod);
					}
					errors = obj.saveObject(parentId, objectId, locale, svr, svw);
					if (errors.isEmpty()) {
						svw.dbCommit();
						jrh.create(MessageType.SUCCESS, I18n.getText("perun.success.saveObject"), CC.EMPTY_STRING,
								obj.getJsonRepresentation());
					} else {
						jrh.create(MessageType.ERROR, I18n.getText("perun.error.saveObject"), errors.toString(),
								new JsonObject());
					}
				} else {
					jrh.create(MessageType.ERROR, I18n.getText("perun.error.objectTableNotFound"), CC.EMPTY_STRING,
							new JsonObject());
				}
			}
		} catch (Exception e) {
			log4j.error(e);
			return PerunUtil.handleException(e, jrh, "perun.error.generalError");
		}
		return Response.ok(jrh.getAll().toString()).build();
	}

	/**
	 * General search endpoint to search for objects
	 * 
	 * @param sessionId
	 * @param httpRequest
	 * @return
	 */
	@Path("/search/{sessionId}")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response search(@PathParam("sessionId") String sessionId, String entity,
			@Context HttpServletRequest httpRequest) {
		JsonArray result = new JsonArray();
		JsonObject requestData = null;
		ResponseHandler jrh = new ResponseHandler();
		DbDataArray records = null;
		BaseObjectModel obj;
		String localeId = SvConf.getDefaultLocale();

		try {
			requestData = new Gson().fromJson(entity, JsonObject.class);
			requestData = ModelFactoryRegistry.unnest(requestData, null, null);
		} catch (JsonSyntaxException e) {
			jrh.create(MessageType.ERROR, I18n.getText(localeId, "error.bad_json"), CC.EMPTY_STRING, new JsonObject());
			return Response.ok(jrh.getAll().toString()).build();
		}

		try (SvReader svr = new SvReader(sessionId)) {
			localeId = svr.getUserLocaleId(svr.getInstanceUser());

			if (!requestData.has(CC.TABLE_NAME)) {
				jrh.create(MessageType.ERROR, I18n.getText(localeId, "error.missing_arguments"));
				return Response.ok(jrh.getAll().toString()).build();
			}

			String tableName = requestData.get(CC.TABLE_NAME).getAsString();
			obj = ModelFactoryRegistry.createModel(tableName);

			records = obj.searchObjects(requestData, svr);
			result = convertDbDataArrayToJsonArray(records, tableName, false, svr);
		} catch (Exception e) {
			log4j.error("General error in search:", e);
			return PerunUtil.handleException(e, jrh, "perun.error.generalError");
		}
		if (records == null || records.isEmpty()) {
			jrh.create(MessageType.INFO, I18n.getText("info.no_records_found"), I18n.getText("info.no_records_found"),
					new JsonArray());
		} else {
			jrh.create(MessageType.SUCCESS, I18n.getText("success.fetch_records"),
					I18n.getText("success.fetch_records"), result);
		}
		return Response.ok(jrh.getAll().toString()).build();
	}

	/**
	 * Get summary for a table object. Need to provide the sessionId, table name and
	 * the objectId as path parameters.
	 * 
	 * @param sessionId User's session id
	 * @param tableName Name of the table
	 * @param objectId  The object id in the DB
	 * @return Response object
	 */
	@Path("/getObjectSummary/{sessionId}/{tableName}/{objectId}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getObjectSummary(@PathParam("sessionId") String sessionId, @PathParam("tableName") String tableName,
			@PathParam("objectId") Long objectId, @Context HttpServletRequest httpRequest) {
		ResponseHandler jrh = new ResponseHandler();
		JsonObject data = new JsonObject();
		if (Objects.isNull(tableName) || Objects.isNull(objectId)) {
			jrh.create(MessageType.ERROR, I18n.getText("perun.error.missingParameters"), CC.EMPTY_STRING,
					new JsonObject());
			return Response.status(400).entity(jrh.getAll().toString()).build();
		}

		Boolean found = false;
		String localeId = SvConf.getDefaultLocale();
		BaseObjectModel obj = null;
		LinkedHashMap<String, String> objData = null;
		JsonArray jsonArray;
		try (SvReader svr = new SvReader(sessionId)) {
			localeId = svr.getUserLocaleId(svr.getInstanceUser());
			obj = ModelFactoryRegistry.createModel(tableName);
			if (obj != null) {
				found = obj.from(objectId, svr);
				if (found) {
					objData = obj.getSummary(localeId, svr);
					jsonArray = convertSummaryMapToJsonArray(objData);
					data.add(CC.SHORT, jsonArray);

					objData = obj.getDetails(localeId, svr);
					jsonArray = convertSummaryMapToJsonArray(objData);
					data.add(CC.DETAILED, jsonArray);
				}
			}
			if (!found) {
				jrh.create(MessageType.ERROR, I18n.getText("perun.error.objectTableNotFound"), CC.EMPTY_STRING,
						new JsonObject());
				return Response.ok(jrh.getAll().toString()).build();
			}
		} catch (Exception e) {
			log4j.error("General error in getObjectSummary:", e);
			return PerunUtil.handleException(e, jrh, "perun.error.generalError");
		}

		jrh.create(MessageType.SUCCESS, I18n.getText(localeId, "data.read"), null, data);
		return Response.ok(jrh.getAll().toString()).build();
	}

	@Path("/getModelJsonSchema/{tableName}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getModelJsonSchema(@HeaderParam("sessionId") String sessionId,
			@PathParam("tableName") String tableName, @Context HttpServletRequest httpRequest) {
		try {
			JsonSchemaServiceImpl schemaService = new JsonSchemaServiceImpl();
			JsonObject jsonSchema = schemaService.buildSchema(tableName, sessionId);
			return Response.ok(jsonSchema.toString()).build();
		} catch (Exception e) {
			log4j.error(e);
			return PerunUtil.handleException(e, null, "perun.error.generalError");
		}
	}

	/**
	 * Set the autoCommit flag of all SvCore elements in the list
	 * 
	 * @param svCoreList - List of SvCore instances
	 * @param autoCommit
	 * @throws SvException
	 */
	public static void setAutoCommit(List<SvCore> svCoreList, Boolean autoCommit) throws SvException {
		for (SvCore core : svCoreList) {
			core.setAutoCommit(autoCommit);
			core.dbSetAutoCommit(autoCommit);
		}
	}

	public static JsonArray convertDbDataArrayToJsonArray(DbDataArray dbArray, String tableName, Boolean skipRepoFields,
			SvReader svr) throws SvException {
		JsonArray resultJsonArray = new JsonArray();
		Gson gson = new Gson();
		for (DbDataObject dbo : dbArray.getItems()) {
			LinkedHashMap<String, JsonElement> lhmObj = getDbDataObjectsAsLinkedHashMap(dbo, tableName, skipRepoFields,
					svr);
			JsonObject currJsonObj = gson.toJsonTree(lhmObj).getAsJsonObject();
			resultJsonArray.add(currJsonObj);
		}
		return resultJsonArray;
	}

	public static LinkedHashMap<String, JsonElement> getDbDataObjectsAsLinkedHashMap(DbDataObject dbo, String tableName,
			boolean skipRepoFields, SvReader svr) throws SvException {
		DbDataObject dboField = null;
		String localeId = CC.EMPTY_STRING;
		JsonObject convertedJObj = dbo.toJson().getAsJsonObject(dbo.getClass().getCanonicalName());
		LinkedHashMap<String, JsonElement> lhmObj = new LinkedHashMap<>();
		Gson gson = new Gson();
		if (svr != null)
			localeId = svr.getUserLocaleId(svr.getInstanceUser());
		for (Entry<String, JsonElement> tempConverted : convertedJObj.entrySet()) {
			if (!tempConverted.getKey().equals("values")) {
				if (!skipRepoFields) {
					lhmObj.put(tableName + "." + tempConverted.getKey().toUpperCase(), tempConverted.getValue());
				}
			} else {
				JsonArray jsonArray = tempConverted.getValue().getAsJsonArray();
				for (JsonElement je : jsonArray) {
					for (Entry<String, JsonElement> value : je.getAsJsonObject().entrySet()) {
						if (svr != null) {
							dboField = SvReader.getFieldByName(tableName, value.getKey().toUpperCase());
							if (dboField != null && dboField.getVal(CC.SV_ISLABEL) != null
									&& dboField.getVal(CC.SV_ISLABEL).equals(true)) {
								lhmObj.put(tableName + "." + value.getKey().toUpperCase() + "_CODE", value.getValue());
								StringBuilder sb = new StringBuilder(
										"\"" + (I18n.getText(localeId, value.getValue().toString().replace("\"", "")))
												+ "\"");
								lhmObj.put(tableName + "." + value.getKey().toUpperCase(),
										JsonParser.parseString(sb.toString()));
							} else {
								lhmObj.put(tableName + "." + value.getKey().toUpperCase(), value.getValue());
							}

							if (dboField != null && dboField.getVal(CC.REFERENTIAL_FIELD) != null
									&& dboField.getVal(CC.REFERENTIAL_TABLE) != null
									&& dboField.getVal(CC.GUI_METADATA) != null) {
								JsonObject guiMetadata = gson.fromJson(dboField.getVal(CC.GUI_METADATA).toString(),
										JsonObject.class);
								if (guiMetadata != null && guiMetadata.has(CC.REACT)) {
									JsonObject jsonreactGUI = guiMetadata.get(CC.REACT).getAsJsonObject();
									if (jsonreactGUI != null && jsonreactGUI.has(CC.DENORMALIZED_MNEMONIC)) {
										DbDataObject denormalizedField = DbReader.findField(
												dboField.getVal(CC.REFERENTIAL_TABLE).toString(),
												jsonreactGUI.get(CC.DENORMALIZED_MNEMONIC).getAsString(), svr);
										DbDataObject denormalizedData = getDbDataObjectFromDenormalizedField(
												dboField.getVal(CC.REFERENTIAL_TABLE).toString(),
												dboField.getVal(CC.REFERENTIAL_FIELD).toString(),
												dbo.getVal(value.getKey().toUpperCase()), svr);
										if (denormalizedField != null && denormalizedData != null) {
											lhmObj.put(
													dboField.getVal(CC.REFERENTIAL_TABLE).toString() + "."
															+ jsonreactGUI.get(CC.DENORMALIZED_MNEMONIC).getAsString(),
													gson.toJsonTree(denormalizedData.getVal(
															jsonreactGUI.get(CC.DENORMALIZED_MNEMONIC).getAsString())));
										}
									}
								}

							}
						} else {
							lhmObj.put(tableName + "." + value.getKey().toUpperCase(), value.getValue());
						}
					}
				}
			}
		}
		return lhmObj;
	}

	public static DbDataObject getDbDataObjectFromDenormalizedField(String tableName, String fieldName,
			Object denormalizedId, SvReader svr) throws SvException {
		DbDataObject dbo = null;

		if (denormalizedId != null) {
			if (fieldName.equals(CC.OBJECT_ID)) {
				dbo = svr.getObjectById(Long.valueOf(denormalizedId.toString()),
						SvReader.getDbtByName(tableName.toUpperCase()), null);
			} else {
				DbSearchCriterion crit = new DbSearchCriterion(fieldName.toUpperCase(), DbCompareOperand.EQUAL,
						denormalizedId);
				DbDataArray dba = svr.getObjects(crit, SvReader.getTypeIdByName(tableName), null, 0, 0);
				if (!dba.isEmpty()) {
					dbo = dba.get(0);
				}
			}
		}
		return dbo;
	}

	public static JsonArray convertSummaryMapToJsonArray(LinkedHashMap<String, String> objData) {
		JsonArray result = new JsonArray();
		for (Map.Entry<String, String> entry : objData.entrySet()) {
			JsonObject obj = new JsonObject();
			obj.addProperty(CC.LABEL_LC, entry.getKey());
			obj.addProperty(CC.VALUE_LC, entry.getValue());
			result.add(obj);
		}
		return result;
	}
}
