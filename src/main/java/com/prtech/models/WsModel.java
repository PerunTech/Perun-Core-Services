package com.prtech.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
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
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.prtech.perun.PerunUtil;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvWriter;
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
}
