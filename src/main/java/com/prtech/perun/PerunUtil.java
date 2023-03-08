package com.prtech.perun;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.prtech.svarog.I18n;
import com.prtech.svarog.Sv;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvUtil;
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

			json = gson.fromJson(formData, JsonObject.class);
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
	public static JsonArray getListObjectsFromDb(SvCore svc, String tableFilterName, String schemaName, String objectType)
			throws SvException {

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

	public static JsonArray getTableFieldsFromDb(SvCore svc, String tableName, String schemaName)
			throws SvException {

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
