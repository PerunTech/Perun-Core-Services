package com.prtech.perun.services.ws;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.prtech.perun.PerunUtil;
import com.prtech.svarog.CodeList;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvNotification;
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
import com.prtech.svarog_common.DbSearchExpression;
import com.prtech.svarog_common.ResponseHandler;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;
import com.prtech.svarog_common.ResponseHandler.MessageType;

@Path("/PublicWs")
public class PublicWs {
	static final Logger log4j = SvConf.getLogger(PublicWs.class);

	@Path("/getSiteKey")
	@GET
	public String getSiteKey() {
		String site_key = "";
		site_key = SvConf.getParam("site.key");

		if (site_key == null)
			site_key = "not define site.key param";

		return site_key;
	}

	/**
	 * Public notifications/announcements on login screen
	 * 
	 * @param token       - Session token
	 * @param httpRequest
	 * @return f.r
	 */
	@Path("/getNotifications/sid/{token}")
	@GET
	@Produces("application/json;charset=UTF-8")
	public Response getNotificationsInternal(@PathParam("token") String token,
			@Context HttpServletRequest httpRequest) {
		ResponseHandler jrh = new ResponseHandler();
		JsonArray jArray = new JsonArray();
		String ip = PerunUtil.getClientIpAddress(httpRequest);
		try (SvReader svr = new SvReader(ip);) {
			// ((SvCore) svs).switchUser(svCONST.serviceUser);
			try (SvNotification svnf = new SvNotification(svr);) {
				DbDataArray publicNotifications = svr.getObjectsByTypeId(svCONST.OBJECT_TYPE_NOTIFICATION, null, 0, 0);
				for (DbDataObject dbo : publicNotifications.getItems()) {
					if (dbo.getVal("TYPE") != null && !dbo.getVal("TYPE").equals("PUBLIC")) {
						JsonObject jsonSubObj = new JsonObject();
						if (dbo.getVal("TITLE") != null)
							jsonSubObj.addProperty("TITLE", dbo.getVal("TITLE").toString());
						if (dbo.getVal("SENDER") != null)
							jsonSubObj.addProperty("SENDER", dbo.getVal("SENDER").toString());
						if (dbo.getVal("MESSAGE") != null)
							jsonSubObj.addProperty("MESSAGE", dbo.getVal("MESSAGE").toString());
						if (dbo.getVal("TYPE") != null)
							jsonSubObj.addProperty("TYPE", dbo.getVal("TYPE").toString());
						jArray.add(jsonSubObj);
					}
				}
			}
		} catch (SvException e) {
			log4j.error("Exception in fetching notifications", e);
		}
		jrh.create(MessageType.SUCCESS, I18n.getText("data.read"), I18n.getText("data.read"), jArray);
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * Public notifications/announcements on login screen
	 * 
	 * @param token_ip    - ip address to prevent ddos
	 * @param httpRequest
	 * @return f.r
	 */
	@Path("/getNotifications/{token_ip}")
	@GET
	@Produces("application/json;charset=UTF-8")
	public Response getNotifications(@PathParam("token_ip") String token_ip, @Context HttpServletRequest httpRequest) {
		ResponseHandler jrh = new ResponseHandler();
		JsonArray jArray = new JsonArray();
		try (SvSecurity svs = new SvSecurity(token_ip);) {
			((SvCore) svs).switchUser(svCONST.serviceUser);
			try (SvNotification svnf = new SvNotification(svs); SvReader svr = new SvReader(svnf);) {
				DbDataArray publicNotifications = svr.getObjectsByTypeId(svCONST.OBJECT_TYPE_NOTIFICATION, null, 0, 0);
				for (DbDataObject dbo : publicNotifications.getItems()) {
					if (dbo.getVal("TYPE").equals("PUBLIC")) {
						JsonObject jsonSubObj = new JsonObject();
						if (dbo.getVal("TITLE") != null)
							jsonSubObj.addProperty("TITLE", dbo.getVal("TITLE").toString());
						if (dbo.getVal("SENDER") != null)
							jsonSubObj.addProperty("SENDER", dbo.getVal("SENDER").toString());
						if (dbo.getVal("MESSAGE") != null)
							jsonSubObj.addProperty("MESSAGE", dbo.getVal("MESSAGE").toString());
						if (dbo.getVal("TYPE") != null)
							jsonSubObj.addProperty("TYPE", dbo.getVal("TYPE").toString());
						jArray.add(jsonSubObj);
					}
				}
			}
		} catch (SvException e) {
			log4j.error("Exception in fetching notifications", e);
		}
		jrh.create(MessageType.SUCCESS, I18n.getText("data.read"), I18n.getText("data.read"), jArray);
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * Show detail info for application. Paid, debt etc.
	 * 
	 * @param token_ip    - ip address to prevent ddos
	 * @param idNo        - idNo embg/edb of farmer
	 * @param appNo       - application number
	 * @param pinType     - ID_NO or TAX_NO
	 * @param httpRequest
	 * @return f.r
	 */
	@Path("/getApplications/{token_ip}/{idNo}/{appNo}/{pinType}")
	@GET
	@Produces("application/json")
	public Response getApplications(@PathParam("token_ip") String token_ip, @PathParam("idNo") String idNo,
			@PathParam("appNo") String appNo, @PathParam("pinType") String pinType,
			@Context HttpServletRequest httpRequest) {

		SvReader svr = null;
		CodeList cl = null;
		SvSecurity svs = null;
		JsonObject jObj = new JsonObject();
		JsonArray jArrMeasures = new JsonArray();
		ResponseHandler jrh = new ResponseHandler();

		try {
			svs = new SvSecurity(token_ip);
			((SvCore) svs).switchUser(svCONST.serviceUser);
			svr = new SvReader(svs);
			cl = new CodeList(svr);

			DbSearchExpression expr = new DbSearchExpression();
			DbSearchCriterion critU = new DbSearchCriterion(pinType, DbCompareOperand.EQUAL, idNo);
			expr.addDbSearchItem(critU);

			DbDataArray AllApps = new DbDataArray();
			DbDataObject appType = new DbDataObject();

			DbDataArray vDataFarmer = svr.getObjects(expr, SvCore.getTypeIdByName("FARMER"), null, 1000, null);

			if (vDataFarmer != null && !vDataFarmer.isEmpty()) {
				AllApps = svr.getObjectsByParentId(vDataFarmer.get(0).getObjectId(),
						SvCore.getTypeIdByName("APPLICATION"), null, 0, 0, Rc.SORT_ORDER);
			} else {
				jrh.create(MessageType.INFO, I18n.getText("info.farmer_notFound"), I18n.getText("info.farmer_notFound"),
						"");
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}

			if (AllApps != null && !AllApps.isEmpty()) {
				for (DbDataObject dboApp : AllApps.getItems()) {
					if (dboApp.getVal("APP_ID").toString().contains(appNo)) {
						appType = svr.getObjectById((Long) dboApp.getVal("APP_TYPE_ID"),
								SvCore.getTypeIdByName("APPLICATION_TYPE"), null);
						JsonObject program = getProgramMeasureByYear(Long.valueOf(appType.getVal("YEAR").toString()),
								svr);
						jObj.addProperty("objectId", dboApp.getObjectId());
						jObj.addProperty("appStatus",
								cl.getCodeList(svCONST.CODES_STATUS, true).get(dboApp.getStatus()));
						jObj.addProperty("appNumber", dboApp.getVal("APP_ID").toString());
						jObj.addProperty("аppType", appType.getVal("DESCRIPTION").toString());
						jObj.addProperty("year", appType.getVal("YEAR").toString());
						JsonArray owe = getDetailsForObligator(idNo, svr);
						jObj.add("debt", owe);
						/* get measures info f.r */
						jArrMeasures = getMeasures(dboApp.getObjectId(), idNo, program, cl, svs);
						jObj.add("measures", jArrMeasures);
						return Response.status(200).entity(jObj.toString()).build();
					}
				}
			} else {
				jrh.create(MessageType.INFO, I18n.getText("info.applicationNotFound"),
						I18n.getText("info.applicationNotFound"), "");
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}

		} catch (SvException ex) {
			if (ex.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText(ex.getLabelCode()), I18n.getText(ex.getLabelCode()),
						new JsonObject());
				log4j.error(ex.getFormattedMessage());
			} else {
				log4j.error(ex.getLabelCode(), ex);
				if (ex.getLabelCode().startsWith("sys")) {
					return Response.status(500).entity(jrh.getAll()).build();
				}
			}
		} finally {
			if (cl != null)
				cl.release();

			if (svr != null) {
				svr.release();
				svr.close();
			}

			if (svs != null) {
				svs.release();
				svs.close();
			}
		}
		return Response.status(200).entity(jObj.toString()).build();
	}

	private JsonArray getMeasures(Long appObj, String embgEdb, JsonObject program, CodeList cl, SvSecurity svs) {
		SvReader svr = null;
		JsonArray jArrMeasure = new JsonArray();
		JsonArray clearingDebt = new JsonArray();
		JsonArray financeDetails = new JsonArray();
		JsonArray clearingDebtRural = new JsonArray();
		JsonArray financeDetailsRural = new JsonArray();
		String strProgram = "";
		String ruralProgram = "";
		try {
			if (!svs.getInstanceUser().equals(svCONST.serviceUser))
				((SvCore) svs).switchUser(svCONST.serviceUser);
			svr = new SvReader(svs);

			if (program.has("program")) {
				strProgram = program.get("program").getAsString();
				clearingDebt = getDetailsForClearingDebt(embgEdb, strProgram, svr);

				financeDetails = getFinanceDetails(embgEdb, strProgram, svr);

			}
			if (program.has("program_rural")) {
				ruralProgram = program.get("program_rural").getAsString();
				clearingDebtRural = getDetailsForClearingDebt(embgEdb, ruralProgram, svr);

				financeDetailsRural = getFinanceDetails(embgEdb, ruralProgram, svr);
			}
			DbSearchCriterion crit1 = new DbSearchCriterion(Rc.PARENT_ID, DbCompareOperand.EQUAL, appObj);
			DbQueryObject supportTypeStatus = new DbQueryObject(SvReader.getDbtByName("SUPPORT_TYPE_STATUS"), crit1,
					DbJoinType.INNER, null, LinkType.CUSTOM, null, null);
			supportTypeStatus.addCustomJoinLeft("SUPPORT_TYPE_ID");
			supportTypeStatus.addCustomJoinRight("OBJECT_ID");

			DbQueryObject measures = new DbQueryObject(SvReader.getDbtByName("PAYMENT_MEASURE_TYPE"), null,
					DbJoinType.INNER, null, LinkType.CUSTOM, null, null);
			measures.addCustomJoinLeft("LABEL_CODE");
			measures.addCustomJoinRight("LABEL_CODE");

			DbQueryObject labels = new DbQueryObject(SvReader.getDbtByName("SVAROG_LABELS"), null, null, null);

			DbQueryExpression q = new DbQueryExpression();
			q.addItem(supportTypeStatus);
			q.addItem(measures);
			q.addItem(labels);
			DbDataArray res = svr.getObjects(q, 0, 0);

			if (!res.isEmpty()) {
				LinkedHashMap<String, JsonObject> results = new LinkedHashMap<>();
				matchPaymentAndFinance(res, clearingDebt, financeDetails, "num_solution", strProgram, ruralProgram,
						results, svr, cl);
				matchPaymentAndFinance(res, clearingDebt, financeDetails, "measure", strProgram, ruralProgram, results,
						svr, cl);
				matchPaymentAndFinance(res, clearingDebtRural, financeDetailsRural, "num_solution", strProgram,
						ruralProgram, results, svr, cl);
				matchPaymentAndFinance(res, clearingDebtRural, financeDetailsRural, "measure", strProgram, ruralProgram,
						results, svr, cl);
				for (Entry<String, JsonObject> entry : results.entrySet()) {
					jArrMeasure.add(entry.getValue());
				}
			}
		} catch (SvException e) {
			if (log4j.isDebugEnabled())
				log4j.debug(e.getFormattedMessage(), e);
		} finally {
			if (svr != null)
				svr.release();

			if (svs != null)
				svs.release();
		}
		return jArrMeasure;
	}

	private JsonArray getDetailsForObligator(String embgEdb, SvReader svr) {
		JsonObject jObj = null;
		JsonArray result = new JsonArray();
		PreparedStatement selectStatement = null;
		ResultSet rs = null;
		try {
			String selectQuery = "Select DI_VID_OBVRSKA, DI_IZNOS_DOLG, DI_GODINA from AFPZRR.AKTIVNI_DOLZNICI@GORD where di_embg= ?";
			selectStatement = svr.dbGetConn().prepareStatement(selectQuery, ResultSet.TYPE_FORWARD_ONLY,
					ResultSet.CONCUR_READ_ONLY);
			selectStatement.setString(1, embgEdb);

			rs = selectStatement.executeQuery();

			while (rs.next()) {
				jObj = new JsonObject();
				jObj.addProperty("institution", rs.getString("DI_VID_OBVRSKA"));
				jObj.addProperty("amount", rs.getLong("DI_IZNOS_DOLG"));
				jObj.addProperty("year", rs.getString("DI_GODINA"));
				result.add(jObj);
			}

		} catch (SvException | SQLException e) {
			if (log4j.isDebugEnabled())
				log4j.debug(e);
		} finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException e) {
					if (log4j.isDebugEnabled())
						log4j.debug(e);
				}
			}
			try {
				SvCore.closeResource((AutoCloseable) selectStatement, svr.getInstanceUser());
			} catch (SvException e) {
				if (log4j.isDebugEnabled())
					log4j.debug(e);
			}
		}
		return result;
	}

	private JsonArray getDetailsForClearingDebt(String embgEdb, String program, SvReader svr) {

		JsonObject jObj = null;
		JsonArray clearingDebts = new JsonArray();
		PreparedStatement selectStatement = null;

		ResultSet rs = null;
		try {
			String selectQuery = "Select IZNOS_ODOBRUVANJE, INSTITUCIJA, MERKA, PROGRAMA, ODOBRUVANJE from AFPZRR.PREBIVANJE_ZA_INSTITUCII_VIEW@GORD where embg_baratel= ?"
					+ " and merka like '" + program + ".%' order by merka";
			selectStatement = svr.dbGetConn().prepareStatement(selectQuery, ResultSet.TYPE_FORWARD_ONLY,
					ResultSet.CONCUR_READ_ONLY);
			selectStatement.setString(1, embgEdb);

			rs = selectStatement.executeQuery();
			while (rs.next()) {
				jObj = new JsonObject();
				jObj.addProperty("amount", rs.getLong("IZNOS_ODOBRUVANJE"));
				jObj.addProperty("institution", rs.getString("INSTITUCIJA"));
				jObj.addProperty("measure", rs.getString("MERKA"));
				jObj.addProperty("program", rs.getString("PROGRAMA"));
				jObj.addProperty("num_solution", rs.getString("ODOBRUVANJE"));

				clearingDebts.add(jObj);

			}

		} catch (SvException | SQLException e) {
			if (log4j.isDebugEnabled())
				log4j.debug(e);
		} finally {
			if (rs != null)
				try {
					rs.close();
				} catch (SQLException e) {
					if (log4j.isDebugEnabled())
						log4j.debug(e);
				}
			try {
				SvCore.closeResource((AutoCloseable) selectStatement, svr.getInstanceUser());
			} catch (SvException e) {
				if (log4j.isDebugEnabled())
					log4j.debug(e);
			}

		}
		return clearingDebts;
	}

	private JsonArray getFinanceDetails(String embgEdb, String program, SvReader svr) {

		JsonObject jObj = null;
		JsonArray financeDetails = new JsonArray();
		PreparedStatement selectStatement = null;

		ResultSet rs = null;
		try {
			String selectQuery = "select distinct * from (Select MERKA, IZNOS, POVIKUVANJE from AFPZRR.PREBIVANJE_ZA_BARATELI_VIEW@GORD where embg= ?"
					+ " and merka like '" + program + ".%' "
					+ "union select MERKA, IZNOS, decisionnum from WS_AFZPRMETHODSA_DETAIL@FINANCE_PROD where embs_embg= ? "
					+ "and merka like '" + program + ".%' )" + "order by merka";
			selectStatement = svr.dbGetConn().prepareStatement(selectQuery, ResultSet.TYPE_FORWARD_ONLY,
					ResultSet.CONCUR_READ_ONLY);
			selectStatement.setString(1, embgEdb);
			selectStatement.setString(2, embgEdb);

			rs = selectStatement.executeQuery();

			while (rs.next()) {
				jObj = new JsonObject();
				jObj.addProperty("amount", rs.getLong("IZNOS"));
				jObj.addProperty("measure", rs.getString("MERKA"));
				jObj.addProperty("num_solution", rs.getString("POVIKUVANJE"));
				financeDetails.add(jObj);

			}

		} catch (SvException | SQLException e) {
			if (log4j.isDebugEnabled())
				log4j.debug(e);
		} finally {
			if (rs != null)
				try {
					rs.close();
				} catch (SQLException e) {
					if (log4j.isDebugEnabled())
						log4j.debug(e);
				}
			try {
				SvCore.closeResource((AutoCloseable) selectStatement, svr.getInstanceUser());
			} catch (SvException e) {
				if (log4j.isDebugEnabled())
					log4j.debug(e);
			}

		}
		return financeDetails;
	}

	private JsonObject getProgramMeasureByYear(Long year, SvReader svr) {

		JsonObject result = new JsonObject();

		PreparedStatement selectStatement = null;

		ResultSet rs = null;
		try {
			String selectQuery = "Select PROGRAM, PROGRAM_RURAL from " + SvConf.getDefaultSchema()
					+ ".CAMPAIGN_PROGRAM where year= ?";
			selectStatement = svr.dbGetConn().prepareStatement(selectQuery, ResultSet.TYPE_FORWARD_ONLY,
					ResultSet.CONCUR_READ_ONLY);
			selectStatement.setLong(1, year);

			rs = selectStatement.executeQuery();
			while (rs.next()) {
				result = new JsonObject();
				result.addProperty("program", rs.getString("PROGRAM"));
				result.addProperty("program_rural", rs.getString("PROGRAM_RURAL"));
			}

		} catch (SvException | SQLException e) {
			if (log4j.isDebugEnabled())
				log4j.debug(e);
		} finally {
			if (rs != null)
				try {
					rs.close();
				} catch (SQLException e) {
					if (log4j.isDebugEnabled())
						log4j.debug(e);
				}
			try {
				SvCore.closeResource((AutoCloseable) selectStatement, svr.getInstanceUser());
			} catch (SvException e) {
				if (log4j.isDebugEnabled())
					log4j.debug(e);
			}

		}
		return result;
	}

	private void matchPaymentAndFinance(DbDataArray payment, JsonArray clearingDebt, JsonArray financeDetails,
			String matchKey, String program, String ruralProgram, HashMap<String, JsonObject> results, SvReader svr,
			CodeList cl) throws NumberFormatException, SvException {

		JsonArray paymentsByMatchKey = new JsonArray();
		JsonArray clearingDebtByMatchKey = new JsonArray();
		DbDataArray paymentClaims = null;
		DbDataObject paymentClaim = null;
		DbSearchCriterion crit = null;
		JsonObject jObj = null;
		JsonObject jPayment = null;
		String matchKeyValue = null;
		String organicProgram = ".215";
		String mesure = "";

		for (DbDataObject dbo : payment.getItems()) {
			mesure = dbo.getVal("TBL1_MEASURE_CODE").toString();
			jObj = null;
			if (dbo.getVal("TBL0_STATUS").toString().equalsIgnoreCase("AUTHORIZED")) {

				if (matchKey.equals("num_solution")) {
					crit = new DbSearchCriterion("PAYMENT_TYPE_STATUS_ID", DbCompareOperand.EQUAL,
							Long.valueOf(dbo.getVal("TBL0_OBJECT_ID").toString()));
					paymentClaims = svr.getObjects(crit, SvCore.getTypeIdByName("PAYMENT_CLAIM"), null, 0, 0);
					if (!paymentClaims.isEmpty()) {
						paymentClaim = paymentClaims.get(0);
						if (!paymentClaim.getStatus().equalsIgnoreCase("PAID"))
							continue;
						matchKeyValue = paymentClaim.getVal("ARCH_NO").toString() + "/"
								+ paymentClaim.getVal("ARCH_NO_SEQ").toString();
					}
				} else if (dbo.getVal("TBL1_MEASURE_CODE").toString().endsWith("O")) {
					matchKeyValue = ruralProgram + organicProgram + "." + mesure;
				} else {
					matchKeyValue = ruralProgram + "." + mesure;
				}

				paymentsByMatchKey = new JsonArray();
				clearingDebtByMatchKey = new JsonArray();
				if (results.containsKey(mesure)) {
					jObj = results.get(mesure);

					if (jObj.has("payment")) {
						paymentsByMatchKey = jObj.get("payment").getAsJsonArray();
					}

					if (jObj.has("clearing_debt")) {
						clearingDebtByMatchKey = jObj.get("clearing_debt").getAsJsonArray();
					}
				}

				for (int i = 0; i < financeDetails.size(); i++) {
					jPayment = financeDetails.get(i).getAsJsonObject();

					if (jPayment.get(matchKey).getAsString().equals(matchKeyValue)) {
						paymentsByMatchKey.add(jPayment);
						financeDetails.remove(i);
					}
					if (!matchKey.equals("num_solution")
							&& jPayment.get(matchKey).getAsString().equals(program + "." + mesure)) {
						paymentsByMatchKey.add(jPayment);
						financeDetails.remove(i);
					}
				}

				for (int i = 0; i < clearingDebt.size(); i++) {
					jPayment = clearingDebt.get(i).getAsJsonObject();

					if (jPayment.get(matchKey).getAsString().equals(matchKeyValue)) {
						clearingDebtByMatchKey.add(jPayment);
						clearingDebt.remove(i);
					}

					if (!matchKey.equals("num_solution")
							&& jPayment.get(matchKey).getAsString().equals(program + "." + mesure)) {
						clearingDebtByMatchKey.add(jPayment);
						clearingDebt.remove(i);
					}
				}

				if (jObj == null) {
					jObj = new JsonObject();
					jObj.addProperty("label_text", dbo.getVal("TBL2_LABEL_TEXT").toString());
					jObj.addProperty("label_desc", dbo.getVal("TBL2_LABEL_DESCR").toString());
					jObj.addProperty("status",
							cl.getCodeList(svCONST.CODES_STATUS, true).get(dbo.getVal("TBL0_STATUS").toString()));
					jObj.add("payment", paymentsByMatchKey);
					if (paymentClaim != null && paymentClaim.getVal("PAYMENT_VALUE") != null)
						jObj.addProperty("payment_value", paymentClaim.getVal("PAYMENT_VALUE").toString());
					if (dbo.getVal("TBL0_DT_INSERT") != null)
						jObj.addProperty("date", dbo.getVal("TBL0_DT_INSERT").toString());
					jObj.add("clearing_debt", clearingDebtByMatchKey);
				}

				results.put(dbo.getVal("TBL1_MEASURE_CODE").toString(), jObj);
			}
		}
	}

	@Path("/getGuides/{token}/{fileName}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response getGuides(@PathParam("token") String token, @PathParam("fileName") String fileName) {
		final String confPath = "www" + File.separator + "assets" + File.separator + "guide" + File.separator
				+ "internal";
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(token)) {
			InputStream inputStream = getClass().getClassLoader()
					.getResourceAsStream(confPath + File.separator + fileName + ".json");

			JsonObject jGuide = new JsonObject();
			if (inputStream != null) {
				Gson gson = new Gson();
				ByteArrayOutputStream buffer = null;
				String strData = "";
				buffer = new ByteArrayOutputStream();
				int nRead;
				byte[] data = new byte[1024];
				while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
					buffer.write(data, 0, nRead);
				}

				byte[] byteArray = buffer.toByteArray();

				strData = new String(byteArray, StandardCharsets.UTF_8);

				jGuide = gson.fromJson(strData, JsonObject.class);

				jrh.create(MessageType.SUCCESS, I18n.getText("guides.sucess.read_data_file"),
						I18n.getText("guides.sucess.read_data_file"), jGuide);
				inputStream.close();
				buffer.close();

			} else {
				jrh.create(MessageType.WARNING, I18n.getText("guides.sucess.not_found_file"),
						I18n.getText("guides.sucess.not_found_file"), jGuide);
			}
			return Response.status(200).entity(jrh.getAll().toString()).build();

		} catch (SvException | IOException e) {
			if (e instanceof SvException) {
				SvException ex = (SvException) e;
				if (ex.getLabelCode().equals("error.invalid_session")) {
					log4j.error(ex.getFormattedMessage());
					jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
							I18n.getText("error.invalid_session"), new JsonObject());
					return Response.status(401).entity(jrh.getAll().toString()).build();
				} else {
					log4j.error(ex.getFormattedMessage(), e);
				}
			} else {
				log4j.error(e);
			}
			jrh.create(MessageType.ERROR, I18n.getText("guides.error.read_data_file"),
					I18n.getText("guides.error.read_data_file"), new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		}
	}

	@Path("/getAnnouncement/{token_ip}/{fileName}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response getAnnouncement(@PathParam("token_ip") String token_ip, @PathParam("fileName") String fileName) {
		final String confPath = "www" + File.separator + "assets" + File.separator + "guide" + File.separator
				+ "external";
		ResponseHandler jrh = new ResponseHandler();
		SvSecurity svs = null;
		InputStream inputStream = null;
		ByteArrayOutputStream buffer = null;
		try {
			svs = new SvSecurity(token_ip);
			inputStream = getClass().getClassLoader()
					.getResourceAsStream(confPath + File.separator + fileName + ".json");

			JsonArray jGuide = new JsonArray();
			if (inputStream != null) {
				Gson gson = new Gson();
				String strData = "";
				buffer = new ByteArrayOutputStream();
				int nRead;
				byte[] data = new byte[1024];
				while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
					buffer.write(data, 0, nRead);
				}

				byte[] byteArray = buffer.toByteArray();

				strData = new String(byteArray, StandardCharsets.UTF_8);

				jGuide = gson.fromJson(strData, JsonArray.class);

				jrh.create(MessageType.SUCCESS, I18n.getText("guides.sucess.read_data_file"),
						I18n.getText("guides.sucess.read_data_file"), jGuide);
				inputStream.close();
				buffer.close();
			} else {
				jrh.create(MessageType.WARNING, I18n.getText("guides.sucess.not_found_file"),
						I18n.getText("guides.sucess.not_found_file"), jGuide);
			}
		} catch (SvException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			log4j.error(e);
			jrh.create(MessageType.ERROR, I18n.getText("guides.error.read_data_file"),
					I18n.getText("guides.error.read_data_file"), new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		} finally {
			if (svs != null) {
				svs.release();
				svs.close();
			}
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}
}