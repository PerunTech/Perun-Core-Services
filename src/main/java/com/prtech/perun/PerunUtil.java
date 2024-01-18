package com.prtech.perun;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Map.Entry;

import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.prtech.perun_core.ws.CC;
import com.prtech.svarog.I18n;
import com.prtech.svarog.Sv;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvExecManager;
import com.prtech.svarog.SvParameter;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog.svCONST;
import com.prtech.svarog.SvConf.SvDbType;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.ResponseHandler;
import com.prtech.svarog_common.ResponseHandler.MessageType;

/**
 * The Util class is superceeded by PerunUtil
 * 
 * @author ristepejov
 *
 */
public class PerunUtil extends SvUtil {
	public static Response handleException(Exception e, ResponseHandler jrh, String message) {
		if (jrh == null)
			jrh = new ResponseHandler();
		int responseCode = 500;
		if (e instanceof SvException) {
			SvException sve = (SvException) e;

			if (sve.getLabelCode().equals(Sv.INVALID_SESSION)) {
				jrh.create(MessageType.ERROR, I18n.getText(Sv.INVALID_SESSION), I18n.getLongText(Sv.INVALID_SESSION),
						new JsonObject());
				responseCode = 401;
			} else if (sve.getLabelCode().equals(Sv.Exceptions.NOT_AUTHORISED)) {
				jrh.create(MessageType.ERROR, I18n.getText(Sv.Exceptions.NOT_AUTHORISED),
						I18n.getLongText(Sv.Exceptions.NOT_AUTHORISED), new JsonObject());
				responseCode = 403;
			} else
				jrh.create(MessageType.ERROR, sve.getLabelCode(), sve.getLabelCode(), sve.getLabelCode());
		} else {
			log4j.error(e.getMessage(), e);
			jrh.create(MessageType.ERROR, I18n.getText(message), I18n.getText(message), new JsonObject());
		}
		return Response.status(responseCode).entity(jrh.getAll().toString()).build();
	}

	public static Response handleException(Exception e, String message) {
		return handleException(e, new ResponseHandler(), message);
	}

	/**
	 * method to generate frontEnd host-name so we can make web address/link to our
	 * web service, if there is parameter "frontend.gui_host" in system param we use
	 * that, if not we generate the value from the address that was called
	 * 
	 * @param dboUser DbDataObject user that we are processing
	 * @param feHost  String front end host-name generated from calling the web
	 *                service
	 * 
	 * @return String generated host-name variable
	 * 
	 * @throws SvException
	 */
	public static String getFrontEndHost(HttpServletRequest httpRequest) throws SvException {
		String feHost = SvParameter.getSysParam("frontend.gui_host", CC.NOT_CONFIGURED);
		if (feHost.equals(CC.NOT_CONFIGURED))
			feHost = httpRequest.getScheme() + "://" + httpRequest.getServerName() + ":" + // ":"
					httpRequest.getServerPort();
		return feHost;
	}

	public static String getClientIp(HttpServletRequest request) {
		String ipAddress = request.getHeader("X-FORWARDED-FOR");
		if (ipAddress == null) {
			ipAddress = request.getRemoteAddr();
		}
		return ipAddress;
	}

	public static void sendMail(String recipientAddress, String mailSubject, String mailBody,
			HashMap<String, String> extParams) throws SvException {

		for (Entry<String, String> ent : extParams.entrySet()) {
			mailBody = mailBody.replace(ent.getKey(), ent.getValue());
		}

		// Sender's email ID needs to be mentioned
		String from = SvParameter.getSysParam("mail.from", "NOT_CONFIGURED");
		String username = SvParameter.getSysParam("mail.username", "NOT_CONFIGURED");
		String password = SvParameter.getSysParam("mail.password", "NOT_CONFIGURED");
		String mailFormat = SvParameter.getSysParam("mail.format", "text/html; charset=UTF-8");
		String host = SvParameter.getSysParam("mail.smtp.host", "NOT_CONFIGURED");
		String port = SvParameter.getSysParam("mail.smtp.port", "465");
		String auth = SvParameter.getSysParam("mail.smtp.auth", "true");
		String tls = SvParameter.getSysParam("mail.smtp.starttls.enable", "true");
		String tls_required = SvParameter.getSysParam("mail.smtp.starttls.required", "true");
		String protocols = SvParameter.getSysParam("mail.smtp.ssl.protocols", "TLSv1.2");
		String sockeFactory = SvParameter.getSysParam("mail.smtp.socketFactory.class",
				"javax.net.ssl.SSLSocketFactory");

		// Assuming you are sending email through relay.jangosmtp.net

		Properties props = new Properties();
		props.put("mail.smtp.host", host);
		props.put("mail.smtp.port", port);
		props.put("mail.smtp.auth", auth);
		props.put("mail.smtp.starttls.enable", tls);
		props.put("mail.smtp.ssl.trust", host);
		props.put("mail.smtp.starttls.required", tls_required);
		props.put("mail.smtp.ssl.protocols", protocols);
		props.put("mail.smtp.socketFactory.class", sockeFactory);

		// Get the Session object.
		Session session = Session.getInstance(props, new javax.mail.Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password);
			}
		});

		// Create a default MimeMessage object.
		MimeMessage message = new MimeMessage(session);

		// Set From: header field of the header.
		try {
			message.setFrom(new InternetAddress(from));

			// Set To: header field of the header.
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientAddress));

			message.setHeader("Content-Type", mailFormat);

			// Set Subject: header field
			message.setSubject(mailSubject, "UTF-8");

			// Now set the actual message
			message.setContent(mailBody, mailFormat);
			Transport.send(message);

		} catch (Exception e) {
			throw (new SvException("mail.send.error", svCONST.systemUser, null, recipientAddress));
		}

		if (log4j.isDebugEnabled())
			log4j.debug("Sent message successfully to " + recipientAddress);

	}

//	/**
//	 * procedure to validate e-mail address, simple validation a@b.c will return
//	 * true
//	 * 
//	 * @param email
//	 *            String e-mail address to be validated
//	 * 
//	 * @return Boolean true if e-mail is in valid format
//	 */
	public static Boolean isValidEmailAddress(String email) {
		boolean result = true;
		try {
			InternetAddress emailAddr = new InternetAddress(email);
			emailAddr.validate();
		} catch (AddressException ex) {
			result = false;
		}
		return result;
	}

	public static String getClientIpAddress(HttpServletRequest request) {
		String xForwardedForHeader = request.getHeader("X-Forwarded-For");
		if (xForwardedForHeader == null) {
			return request.getRemoteAddr();
		} else {
			// As of https://en.wikipedia.org/wiki/X-Forwarded-For
			// The general format of the field is: X-Forwarded-For: client, proxy1, proxy2
			// ...
			// we only want the client
			return new StringTokenizer(xForwardedForHeader, ",").nextToken().trim();
		}
	}

	public static DbDataObject getGeometryField(Long objetType) {
		DbDataArray a = SvCore.getFields(objetType);
		DbDataObject geomField = null;
		for (DbDataObject f : a.getItems()) {
			String type = f.getAsString("FIELD_TYPE");
			String name = f.getAsString("FIELD_NAME");
			if (type.equals("GEOMETRY") && !name.equals("CENTROID")) {
				geomField = f;
				break;
			}
		}

		return geomField;

	}

	/**
	 * Method to wrap a web service call to external executor
	 * 
	 * @param formVals        The web service post values
	 * @param httpRequest     The https request
	 * @param ParamName       The system parameter name which holds the executor
	 *                        configuration
	 * @param defaultExecutor The default executor to be called if there's no
	 *                        configuration
	 * @param successLabel    The label code to be returned if successful
	 * @param errorText       The error name
	 * @return
	 */
	public static Response callExternalExecutor(MultivaluedMap<String, String> formVals,
			@Context HttpServletRequest httpRequest, String ParamName, String defaultExecutor, String successLabel,
			String errorText) {

		JsonObject jsonParams = PerunUtil.dataToJson(formVals);
		return callExternalExecutor(jsonParams, httpRequest, ParamName, defaultExecutor, successLabel, errorText);
	}

	/**
	 * Method to wrap a web service call to external executor
	 * 
	 * @param params          The Json object containing all external parameters
	 * @param httpRequest     The https request
	 * @param ParamName       The system parameter name which holds the executor
	 *                        configuration
	 * @param defaultExecutor The default executor to be called if there's no
	 *                        configuration
	 * @param successLabel    The label code to be returned if successful
	 * @param errorText       The error name
	 * @return
	 */
	public static Response callExternalExecutor(JsonObject jsonParams, @Context HttpServletRequest httpRequest,
			String ParamName, String defaultExecutor, String successLabel, String errorText) {
		ResponseHandler jrh = new ResponseHandler();
		JsonObject jso = new JsonObject();
		// check for system parameter PERUN_REGISTER_USER_EXECUTOR, default is
		// PERUN_CORE_EXEC.REGISTER_USER so we always end
		// up with that, if we want to change ways of user registration we change the
		// parameter PERUN_REGISTER_USER_EXECUTOR in DB and create executor with that
		// name, then we call that executor from the enviorment

		String clientIp = PerunUtil.getClientIp(httpRequest);
		try (SvSecurity svs = new SvSecurity(clientIp);) {
			String registerEXE = SvParameter.getSysParam(ParamName, defaultExecutor);
			String feHost = PerunUtil.getFrontEndHost(httpRequest);
			try (SvExecManager svx = new SvExecManager(svs)) {
				// call executor for creating user from the project/enviorment
				Map<String, Object> params = new HashMap<String, Object>();
				jsonParams.addProperty("feHost", feHost);
				params.put("json_params", jsonParams);
				JsonObject configuration = (JsonObject) svx.execute(registerEXE, params, null);
				jrh.create(MessageType.SUCCESS, I18n.getText(successLabel), I18n.getText(successLabel), configuration);
				jso = jrh.getAllv1();
			}
		} catch (SvException e) {
			return PerunUtil.handleException(e, errorText);
		}

		return Response.status(200).entity(jso.toString()).build();
	}

	/**
	 * Method to ensure the geometry type is consistent.
	 * 
	 * @param g      The geometry
	 * @param typeId The type id
	 * @return
	 */
	public static Geometry verifyGeometryType(Geometry g, Long typeId) {
		// TODO Auto-generated method stub
		DbDataObject geomField = getGeometryField(typeId);
		String geomType = geomField.getAsString("GEOMETRY_TYPE");

		if (g.getGeometryType().equalsIgnoreCase(geomType))
			return g;
		else if (g.getGeometryType().equals(Geometry.TYPENAME_POLYGON)
				&& geomType.equalsIgnoreCase(Geometry.TYPENAME_MULTIPOLYGON))
			return sdiFactory.createMultiPolygon(new Polygon[] { (Polygon) g });
		else if (g.getGeometryType().equals(Geometry.TYPENAME_MULTIPOLYGON)
				&& geomType.equalsIgnoreCase(Geometry.TYPENAME_POLYGON))
			return g.getGeometryN(0);
		return null;
	}

	/**
	 * Method to fetch the parent object and update the current data with a specific
	 * field from the parent. Not the most optimal process but it works.
	 * 
	 * @param data                  The DbDataArray holding the objects to be
	 *                              updated
	 * @param svr                   The SvCore to be used for transaction handling
	 * @param objectName            The object name which is the parent type
	 * @param fieldName             The field from the parent object used for
	 *                              denormalisation
	 * @param defaultNoParentString Default string which will be assigned if no
	 *                              parent is found (Where parent id equals to 0)
	 * @throws SvException Passthrough of underlying exceptions.
	 */
	public static void denormaliseFieldFromParent(DbDataArray data, SvReader svr, String objectName, String fieldName,
			String defaultNoParentString) throws SvException {
		Long typeId = SvCore.getTypeIdByName(objectName);
		String denormalisedField = null;
		for (DbDataObject prc : data.getItems()) {
			if (prc.getParentId() > 0L) {
				DbDataObject parent = svr.getObjectById(prc.getParentId(), typeId, null);
				denormalisedField = parent != null ? (String) parent.getVal(fieldName) : null;
			} else
				denormalisedField = defaultNoParentString;
			prc.setVal(fieldName, denormalisedField);
			prc.setIsDirty(false);

		}

	}

	public static JsonObject dataToJson(MultivaluedMap<String, String> data) {
		String formData = "";
		JsonObject json = null;

		try {
			Gson gson = new Gson();
			// handle empty, prep json, create data
			for (Entry<String, List<String>> entry : data.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isEmpty()) {
					String key = entry.getKey();
					formData = key;
				}
			}
			if (formData != null && !formData.isEmpty())
				json = gson.fromJson(formData, JsonObject.class);
			else
				json = new JsonObject();
		} catch (Exception e) {
			// TODO: handle exception
		}

		return json;
	}

	public static Boolean executeDbScript(String script, HashMap<String, String> params, Connection conn,
			Boolean returnsResultSet, ResultSet[] resultSet, PreparedStatement[] prepStatement) {
		Boolean retval = false;
		if (returnsResultSet && (resultSet == null || prepStatement == null)) {
			log4j.error("Can't store resultsets in null object!");
			return false;
		}
		if (log4j.isDebugEnabled())
			log4j.trace("executeDbScript() of:" + script);

		PreparedStatement ps = null;
		try {

			Set<String> paramKeys = (Set<String>) params.keySet();

			String[] items = null;
			items = script.split(SvConf.getDbHandler().getDbScriptDelimiter());
			if (returnsResultSet && resultSet != null
					&& (resultSet.length < items.length || prepStatement.length < items.length)) {
				log4j.info("Can't execute " + Integer.toString(items.length)
						+ " statements and store in result set array with length "
						+ Integer.toString(resultSet.length));
				return false;
			}
			for (int i = 0; i < items.length; i++) {
				String stmt = items[i];
				if (stmt.trim().length() <= 0)
					continue;
				Iterator<String> it = paramKeys.iterator();
				while (it.hasNext()) {
					String currentKey = it.next();
					if (params.get(currentKey) != null)
						stmt = stmt.replace("{" + currentKey + "}", params.get(currentKey));
				}

				log4j.trace(stmt.toUpperCase());

				if (returnsResultSet) {
					prepStatement[i] = conn.prepareStatement(stmt.toUpperCase());
					resultSet[i] = prepStatement[i].executeQuery();
				} else {
					ps = conn.prepareStatement(stmt.toUpperCase());
					ps.execute();
				}

			}
			retval = true;
		} catch (Exception ex) {

			if (SvConf.getDbType().equals(SvDbType.ORACLE) && ((SQLException) ex).getErrorCode() == 1408) {
				retval = true;
				log4j.warn("Duplicate ORACLE index. Warning executing " + script + ". " + ex.getMessage()
						+ ". Parameters maps:" + params.toString());
			} else {
				log4j.fatal("Error executing " + script + ". " + ex.getMessage() + ". Parameters maps:"
						+ params.toString());
				retval = false;
			}

		} finally {
			if (ps != null)
				try {
					ps.close();
				} catch (Exception e) {
					log4j.error("PreparedStatement can't be released!", e);
				}
			;

		}
		if (log4j.isDebugEnabled())
			log4j.debug("executeDbScript() for " + script + " finished.");
		return retval;
	}

	public static JsonArray getListObjectsFromDb(SvCore svc, String tableFilterName, String schemaName,
			String objectType) throws SvException {

		String script = svc.getDbHandler().getSQLScript("db_object_list.sql");
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("OBJECT_FILTER", tableFilterName);
		params.put("SCHEMA_NAME", schemaName);

		ResultSet[] rs = new ResultSet[1];
		PreparedStatement[] ps = new PreparedStatement[1];
		Connection conn = svc.dbGetConn();
		PerunUtil.executeDbScript(script, params, conn, true, rs, ps);

		JsonArray arr = new JsonArray();
		try {
			while (rs[0].next()) {
				String type = rs[0].getString("OBJECT_TYPE");
				if (type.equals(objectType)) {
					JsonObject jo = new JsonObject();
					String name = rs[0].getString("OBJECT_NAME");
					Long id = rs[0].getLong("OBJECT_ID");
					jo.addProperty("OBJECT_NAME", name);
					jo.addProperty("OBJECT_ID", id);
					jo.addProperty("PARENT_ID", -1L);
					arr.add(jo);
				}
			}
		} catch (SQLException e) {
			log4j.error("Can't get list of tables", e);
		} finally {
			try {
				if (rs[0] != null)
					rs[0].close();
				if (ps[0] != null)
					ps[0].close();
			} catch (SQLException e) {
				log4j.error("Can't close result set", e);
			}

		}

		return arr;
	}

	public static JsonArray getTableFieldsFromDb(SvCore svc, String tableName, String schemaName) throws SvException {

		String script = svc.getDbHandler().getSQLScript("table_column_list.sql");
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("SCHEMA_NAME", schemaName);
		params.put("TABLE_NAME", tableName);
		params.put("SCHEMA_NAME", schemaName);

		ResultSet[] rs = new ResultSet[1];
		PreparedStatement[] ps = new PreparedStatement[1];
		Connection conn = svc.dbGetConn();
		PerunUtil.executeDbScript(script, params, conn, true, rs, ps);

		JsonArray arr = new JsonArray();
		try {
			while (rs[0].next()) {
				JsonObject jo = new JsonObject();
				String name = rs[0].getString("FIELD_NAME");
				String stype = rs[0].getString("FIELD_TYPE");
				String isNull = rs[0].getString("IS_NULL");
				int fieldSize = rs[0].getInt("FIELD_SIZE");
				int fieldScale = rs[0].getInt("FIELD_SCALE");
				jo.addProperty("KEY", tableName + "." + name);
				jo.addProperty("FIELD_NAME", name);
				jo.addProperty("FIELD_TYPE", stype);
				jo.addProperty("IS_NULL", isNull);
				jo.addProperty("FIELD_SIZE", fieldSize);
				jo.addProperty("FIELD_SCALE", fieldScale);
				arr.add(jo);

			}
		} catch (SQLException e) {
			log4j.error("Can't get list of indexes", e);
		} finally {
			try {
				if (rs[0] != null)
					rs[0].close();
				if (ps[0] != null)
					ps[0].close();
			} catch (SQLException e) {
				log4j.error("Can't close result set", e);
			}

		}

		return arr;
	}

}
