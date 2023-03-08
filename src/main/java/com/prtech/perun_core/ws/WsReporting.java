package com.prtech.perun_core.ws;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.joda.time.DateTime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.prtech.perun.PerunUtil;
import com.prtech.svarog.Sv;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbQuery;
import com.prtech.svarog_common.DbQueryExpression;
import com.prtech.svarog_common.DbQueryObject;
import com.prtech.svarog_common.DbSearch;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataField.DbFieldType;
import com.prtech.svarog_common.DbQueryObject.DbJoinType;
import com.prtech.svarog_common.DbQueryObject.LinkType;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;
import com.prtech.svarog_common.DbSearchExpression;

@Path("/svarog-reporting")
public class WsReporting {
	static final Logger log4j = SvConf.getLogger(WsReporting.class);

	@Path("/get/xls/{session_id}")
	@POST
	@Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	public Response getTableSampleData(@PathParam("session_id") String sessionId,
			MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) {
		JsonObject params = PerunUtil.dataToJson(formVals);
		HashMap<String, String> fieldNames = new LinkedHashMap<>();
		ArrayList<DbFieldType> types = new ArrayList<>();
		DbDataArray results = new DbDataArray();
		try (SvReader svr = new SvReader(sessionId)) {
			HashMap<String, String> tablePrefixMap = new LinkedHashMap<>();
			JsonArray paramArray = params.get("params").getAsJsonArray();
			DbQuery q = generateQuery(paramArray, tablePrefixMap);
			buildFieldList(paramArray, tablePrefixMap, types, fieldNames);
			DbDataArray res = svr.getObjects(q, null, null);
			results.setItems(res.getItems());
		} catch (SvException e) {
			PerunUtil.handleException(e, "Error generating report");
		}
		StreamingOutput pbfStream = new StreamingOutput() {
			public void write(OutputStream stream) throws IOException {
				generateXls(results, fieldNames, types, stream);
			};
		};
		return Response.ok(pbfStream, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").build();
	}

	@Path("/get/analytics/tables/{session_id}/")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTableList(@PathParam("session_id") String sessionId, MultivaluedMap<String, String> formVals,
			@Context HttpServletRequest httpRequest) {
		JsonArray el = new JsonArray();
		try (SvReader svr = new SvReader(sessionId)) {
			el = PerunUtil.getListObjectsFromDb(svr, "%ANALYTICS%", SvConf.getDefaultSchema(), Rc.MATERIALIZED_VIEW);
		} catch (SvException e) {
			PerunUtil.handleException(e, "Error generating list of tables");
		}

		return Response.ok(el.toString()).build();
		// return null;
	}

	@Path("/get/analytics/fields/{session_id}/{table_name}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTableList(@PathParam("session_id") String sessionId, @PathParam("table_name") String tableName,
			MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) {
		JsonArray el = new JsonArray();
		try (SvReader svr = new SvReader(sessionId)) {
			el = PerunUtil.getTableFieldsFromDb(svr, tableName, SvConf.getDefaultSchema());
		} catch (SvException e) {
			PerunUtil.handleException(e, "Error generating list of fields for table:" + tableName);
		}

		return Response.ok(el.toString()).build();
		// return null;
	}

	void buildFieldList(JsonArray paramArray, HashMap<String, String> tablePrefixMap, ArrayList<DbFieldType> types,
			HashMap<String, String> fieldNames) {

		for (Entry<String, String> e : tablePrefixMap.entrySet()) {
			String tableName = e.getKey();
			String tablePrefix = e.getValue();

			ArrayList<String> fields = extractFieldNames(paramArray, tableName);
			for (String field : fields) {
				fieldNames.put(tablePrefix + "_" + field, field);
				DbDataObject dboField = SvCore.getFieldByName(tableName, field);
				DbFieldType type = DbFieldType.valueOf((String) dboField.getVal(Sv.FIELD_TYPE));
				types.add(type);
			}

		}

	}

	DbQueryExpression generateQuery(JsonArray params, HashMap<String, String> tablePrefixMap) throws SvException {
		DbQueryExpression dqe = new DbQueryExpression();
		ArrayList<DbDataObject> tableDbt = extractTableNames(params);

		for (int i = 0; i < tableDbt.size(); i++) {
			DbDataObject dbt = tableDbt.get(i);
			DbDataObject dbtNext = null;
			if (tableDbt.size() > (i + 1))
				dbtNext = tableDbt.get(i + 1);

			DbSearch dbs = extractTableCriteria(params, (String) dbt.getVal(Sv.TABLE_NAME));
			DbQueryObject dqo = new DbQueryObject(dbt, dbs, null, DbJoinType.INNER);
			dqo.setSqlTablePrefix("EXTBL" + i);
			if (tablePrefixMap != null)
				tablePrefixMap.put((String) dbt.getVal("TABLE_NAME"), "EXTBL" + Integer.toString(i));
			ArrayList<DbDataObject> theLinkType = new ArrayList<>();
			if (dbtNext != null) {
				LinkType lt = getQueryLinkType(dbt, dbtNext, theLinkType);
				dqo.setLinkToNextType(lt);
				if (theLinkType.size() > 0)
					dqo.setLinkToNext(theLinkType.get(0));
			} else
				dqo.setJoinToNext(null);
			dqe.addItem(dqo);
		}
		return dqe;

	}

	DbDataArray getData(JsonArray params, SvReader svr) throws SvException {
		DbQuery q = generateQuery(params, null);
		return svr.getObjects(q, null, null);
	}

	private LinkType getQueryLinkType(DbDataObject dbt, DbDataObject dbtNext, ArrayList<DbDataObject> links)
			throws SvException {
		if (dbt.getObjectId().equals(dbtNext.getParentId()))
			return LinkType.CHILD;
		if (dbt.getParentId().equals(dbtNext.getObjectId()))
			return LinkType.PARENT;

		ArrayList<DbDataObject> tmpLinks = (ArrayList<DbDataObject>) (new SvSecurity()).getLinkTypes(dbt.getObjectId(),
				dbtNext.getObjectId(), true);

		for (DbDataObject link : tmpLinks) {
			links.add(link);
			if (link.getVal(Sv.Link.LINK_OBJ_TYPE_1).equals(dbt.getObjectId()))
				return LinkType.DBLINK;
			else if (link.getVal(Sv.Link.LINK_OBJ_TYPE_2).equals(dbt.getObjectId()))
				return LinkType.DBLINK_REVERSE;
		}
		// TODO Auto-generated method stub
		return null;
	}

	ArrayList<DbDataObject> extractTableNames(JsonArray params) {
		ArrayList<DbDataObject> tables = new ArrayList<>();
		for (JsonElement el : params) {
			JsonObject o = el.getAsJsonObject();
			String fieldname = o.get("field_name").getAsString();
			String[] table = fieldname.split("\\.");
			DbDataObject dbt = SvCore.getDbtByName(table[0]);
			if (!tables.contains(dbt))
				tables.add(dbt);
		}
		return tables;
	}

//	DbDataObject getField(DbDataObject parentTable, String fieldName) {

	DbSearchExpression extractTableCriteria(JsonArray params, String tableName) throws SvException {
		DbSearchExpression epxression = null;
		for (JsonElement el : params) {
			JsonObject o = el.getAsJsonObject();
			String fieldname = o.get("field_name").getAsString();
			String[] tblField = fieldname.split("\\.");
			DbDataObject field = SvCore.getFieldByName(tblField[0], tblField[1]);
			if (tableName.equals(tblField[0]) && field != null && o.has("value") && o.has("operator")) {
				Object value = null;
				DbFieldType type = DbFieldType.valueOf((String) field.getVal(Sv.FIELD_TYPE));
				Long scale = (Long) field.getVal(Sv.FIELD_SCALE);
				if (type.equals(DbFieldType.NUMERIC))
					value = scale == 0L ? o.get("value").getAsLong() : o.get("value").getAsBigDecimal();
				else
					value = o.get("value").getAsString();
				if (value instanceof String && value.equals(""))
					continue;

				String op = o.get("operator").getAsString();

				DbSearchCriterion dbs = null;
				switch (op) {
				case "equal":
					dbs = new DbSearchCriterion(tblField[1], DbCompareOperand.EQUAL, value);
					break;
				case "less":
					dbs = new DbSearchCriterion(tblField[1], DbCompareOperand.LESS, value);
					break;
				case "greater":
					dbs = new DbSearchCriterion(tblField[1], DbCompareOperand.GREATER, value);
					break;
				case "like":
					dbs = new DbSearchCriterion(tblField[1], DbCompareOperand.LIKE, value);
					break;
				case "startsWith":
					dbs = new DbSearchCriterion(tblField[1], DbCompareOperand.LIKE, value + "%");
					break;
				case "endsWith":
					dbs = new DbSearchCriterion(tblField[1], DbCompareOperand.LIKE, "%" + value);
					break;
				default:
					dbs = new DbSearchCriterion(tblField[1], DbCompareOperand.EQUAL, value);
				}
				if (dbs != null) {
					if (epxression == null)
						epxression = new DbSearchExpression();
					epxression.addDbSearchItem(dbs);
				}

			}
		}
		return epxression;
	}

	ArrayList<String> extractFieldNames(JsonArray params, String tableName) {
		ArrayList<String> tables = new ArrayList<>();
		for (JsonElement el : params) {
			JsonObject o = el.getAsJsonObject();
			String fieldname = o.get("field_name").getAsString();
			String[] tblField = fieldname.split("\\.");
			if (tableName.equals(tblField[0]))
				tables.add(tblField[1]);
		}
		return tables;
	}

	public void generateXls(DbDataArray results, HashMap<String, String> fieldNames, ArrayList<DbFieldType> types,
			OutputStream outputStream) {
		Workbook workbook = null;
		try {
			workbook = new XSSFWorkbook();
			Sheet sheet = workbook.createSheet("Export");

			Row header = sheet.createRow(0);

			CellStyle headerStyle = workbook.createCellStyle();
			headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
			headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

			XSSFFont font = ((XSSFWorkbook) workbook).createFont();
			font.setFontName("Arial");
			// font.setFontHeightInPoints((short) 16);
			font.setBold(true);
			headerStyle.setFont(font);
			// create header
			ArrayList<String> celltypes = new ArrayList<>();
			int i = 0;
			for (Entry<String, String> e : fieldNames.entrySet()) {
				Cell headerCell = header.createCell(i++);
				headerCell.setCellValue(e.getValue());
				headerCell.setCellStyle(headerStyle);
			}
			CellStyle style = workbook.createCellStyle();
			style.setWrapText(true);

			i = 1;
			int celltypeIndex = 0;
			for (DbDataObject dbo : results.getItems()) {
				// insert rows
				Row row = sheet.createRow(i++);

				celltypeIndex = 0;
				for (Entry<String, String> e : fieldNames.entrySet()) {
					Cell cell = row.createCell(celltypeIndex);
					Object o = dbo.getVal(e.getKey());
					DbFieldType d = types.get(celltypeIndex++);
					switch (d) {
					case NUMERIC:
						if (o instanceof BigDecimal)
							cell.setCellValue(((BigDecimal) o).doubleValue());
						else
							cell.setCellValue((long) o);
						break;
					case NVARCHAR:
						cell.setCellValue((String) o);
						break;
					case DATE:
					case TIMESTAMP:
						cell.setCellValue(((DateTime) o).toString());
					}
					cell.setCellStyle(style);
				}
			}

			// Finally, let's write the content to a “temp.xlsx” file in the current
			// directory and close the workbook:
			workbook.write(outputStream);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (workbook != null)
				try {
					workbook.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
	}

}
