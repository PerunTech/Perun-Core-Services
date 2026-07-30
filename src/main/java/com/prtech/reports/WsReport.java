package com.prtech.reports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import com.google.gson.JsonObject;
import com.prtech.perun.PerunUtil;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvFileStore;
import com.prtech.svarog.SvLink;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSequence;
import com.prtech.svarog.SvWorkflow;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.ResponseHandler;
import com.prtech.svarog_common.ResponseHandler.MessageType;

@Path("/WsReport")
public class WsReport {
	static final Logger log4j = LogManager.getLogger(WsReport.class.getName());

	/**
	 * Generates a specific report for a database object, persists the generated
	 * file to the file store, and streams the result back to the client.
	 * 
	 * @param sessionId       The active user session identifier
	 * @param formVals        Map containing the form parameters required for
	 *                        generation. Expected keys include:
	 *                        <ul>
	 *                        <li>OBJECT_ID: The unique ID of the database object to
	 *                        report on.</li>
	 *                        <li>TABLE_NAME: The name of the table/entity the
	 *                        object belongs to.</li>
	 *                        <li>REPORT_NAME: The name of the report template
	 *                        (JRXML) to execute.</li>
	 *                        <li>CC.OUTPUT_TYPE: The desired output format (e.g.,
	 *                        "PDF", "EXCEL").</li>
	 *                        </ul>
	 * @param shouldSave      Whether the generated file should be saved
	 *                        (true/false)
	 * @param shouldOverwrite Whether an existing file should be overwritten
	 *                        (true/false)
	 * @param httpRequest     The context of the current HTTP request.
	 * @return Response containing the file as a stream or an JSON containing errors
	 *         if any
	 */
	@Path("/generateAndSaveReport/{sessionId}/{shouldSave}/{shouldOverwrite}")
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
	public Response generateAndSaveReport(@PathParam("sessionId") String sessionId,
			@PathParam("shouldSave") Boolean shouldSave, @PathParam("shouldOverwrite") Boolean shouldOverwrite,
			MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) {
		ResponseHandler jrh = new ResponseHandler();
		Long objectId = 0L;
		String outputType = CC.EMPTY_STRING;
		String reportName = CC.EMPTY_STRING;
		String tableName = CC.EMPTY_STRING;
		String localeId = SvConf.getDefaultLocale();
		DbDataObject dbo = null;
		try (SvReader svr = new SvReader(sessionId);
				SvWriter svw = new SvWriter(sessionId);
				SvLink svl = new SvLink(sessionId);
				SvFileStore svfs = new SvFileStore(svr);
				ByteArrayOutputStream bstr = new ByteArrayOutputStream()) {
			localeId = svr.getUserLocaleId(svr.getInstanceUser());
			if (formVals.containsKey(CC.OBJECT_ID)) {
				objectId = Long.valueOf(formVals.getFirst(CC.OBJECT_ID));
			}
			if (formVals.containsKey(CC.OUTPUT_TYPE)) {
				outputType = formVals.getFirst(CC.OUTPUT_TYPE);
			}
			if (formVals.containsKey(CC.REPORT_NAME)) {
				reportName = formVals.getFirst(CC.REPORT_NAME);
			}
			if (formVals.containsKey(CC.TABLE_NAME)) {
				tableName = formVals.getFirst(CC.TABLE_NAME);
			}
			if (objectId == 0L || objectId == null) {
				jrh.create(MessageType.ERROR, I18n.getText("perun.error.invalidObjectId"), CC.EMPTY_STRING,
						new JsonObject());
				return Response.ok(jrh.getAll().toString()).build();
			}
			dbo = svr.getObjectById(objectId, SvCore.getDbtByName(tableName), null);
			if (dbo == null) {
				jrh.create(MessageType.ERROR, I18n.getText("system.error.object_not_found"), CC.EMPTY_STRING,
						new JsonObject());
				return Response.ok(jrh.getAll().toString()).build();
			}
			
			String printParam = SvConf.getParam("print.jrxml_path");
			Properties rb = setReportProperties(printParam, objectId, localeId, svr);
			GeneratePrint.executeReport(rb, reportName, outputType, bstr, svr.dbGetConn());
			if (bstr.size() > 0) {
				String fileExt = outputType.equals(CC.PDF) ? ".pdf" : ".xlsx";
				String fileName = dbo.getObjectId() + "_" + reportName + fileExt;
				if (shouldSave) {
					try {
						final DbDataObject dboExistingFile = getFileDboByLinkedObj(dbo, CC.PRINT, reportName, svr,
								svfs);
						if (shouldOverwrite) {
							DbDataObject dboFile = uploadFile(dbo, fileName, null, DateTime.now(), bstr.toByteArray(),
									CC.PRINT, 0L, svfs);
							if (dboExistingFile != null) {
								invalidateOldReportLinkAndCreateNew(dbo, dboFile, reportName, localeId, svr, svw, svl,
										svfs);
							} else {
								svl.linkObjects(dbo, dboFile, CC.LINK_FILE, null, false);
							}
						} else {
							if (dboExistingFile == null) {
								DbDataObject dboFile = uploadFile(dbo, fileName, null, DateTime.now(),
										bstr.toByteArray(), CC.PRINT, 0L, svfs);
								if (dboFile.getObjectId() != 0) {
									svl.linkObjects(dbo, dboFile, CC.LINK_FILE, null, false);
								}
							}
						}

					} catch (SvException e) {
						log4j.error("Couldn't save and link svarog file for the report: " + reportName, e);
					}
				}
				StreamingOutput fileStream = new StreamingOutput() {
					@Override
					public void write(OutputStream output) throws IOException {
						try {
							final byte[] data = bstr.toByteArray();
							IOUtils.write(data, output);
						} catch (Exception e) {
							log4j.error("Error while retirieving file!", e);
						}
					}
				};

				return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
						.header("content-disposition", "attachment; filename = " + reportName + fileExt).build();
			}
		} catch (Exception e) {
			log4j.error(e);
			return PerunUtil.handleException(e, jrh, "epi.error.generalError");
		}

		return null;
	}

	/**
	 * Generates a specific report for a database object, persists the generated
	 * file to the file store, and streams the result back to the client.
	 * 
	 * @param sessionId       The active user session identifier
	 * @param objectId
	 * @param tableName
	 * @param reportName
	 * @param outputType
	 * @param shouldSave      Whether the generated file should be saved
	 * @param shouldOverwrite Whether an existing file should be overwritten
	 * @param httpRequest     The context of the current HTTP request.
	 * @return Response containing the file as a stream or JSON containing errors
	 */
	@Path("/generateAndSaveReport/{sessionId}/{objectId}/{tableName}/{reportName}/{outputType}/{shouldSave}/{shouldOverwrite}")
	@GET
	@Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
	public Response generateAndSaveReport(@PathParam("sessionId") String sessionId,
			@PathParam("shouldSave") Boolean shouldSave, @PathParam("shouldOverwrite") Boolean shouldOverwrite,
			@PathParam("objectId") Long objectId, @PathParam("tableName") String tableName,
			@PathParam("reportName") String reportName, @PathParam("outputType") String outputType,
			@QueryParam("fileSuffix") String fileSuffix, @Context HttpServletRequest httpRequest) {

		ResponseHandler jrh = new ResponseHandler();
		String localeId = SvConf.getDefaultLocale();
		DbDataObject dbo = null;

		String responseType = MediaType.APPLICATION_OCTET_STREAM;
		if (CC.PDF.equals(outputType)) {
			responseType = "application/pdf";
		} else if (CC.XLSX.equals(outputType)) {
			responseType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
		}

		String fileNameSuffix = fileSuffix == null ? CC.EMPTY_STRING
				: fileSuffix.substring(0, Math.min(100, fileSuffix.length()));

		try (SvReader svr = new SvReader(sessionId);
				SvWriter svw = new SvWriter(sessionId);
				SvWorkflow sww = new SvWorkflow(sessionId);
				SvFileStore svfs = new SvFileStore(svr);
				SvLink svl = new SvLink(svr);
				ByteArrayOutputStream bstr = new ByteArrayOutputStream()) {
			localeId = svr.getUserLocaleId(svr.getInstanceUser());
			if (objectId == 0L || objectId == null) {
				jrh.create(MessageType.ERROR, I18n.getText("epi.error.invalidObjectId"), CC.EMPTY_STRING,
						new JsonObject());
				return Response.ok(jrh.getAll().toString()).build();
			}
			dbo = svr.getObjectById(objectId, SvCore.getDbtByName(tableName), null);
			if (dbo == null) {
				jrh.create(MessageType.ERROR, I18n.getText("system.error.object_not_found"), CC.EMPTY_STRING,
						new JsonObject());
				return Response.ok(jrh.getAll().toString()).build();
			}
			String printParam = SvConf.getParam("print.jrxml_path");
			Properties rb;
			Map<String, String[]> params = httpRequest.getParameterMap();
			rb = setReportProperties(printParam, objectId, params, localeId, svr);
			GeneratePrint.executeReport(rb, reportName, outputType, bstr, svr.dbGetConn());

			if (bstr.size() > 0) {
				String fileExt = outputType.equals(CC.PDF) ? ".pdf" : ".xlsx";
				String fileName = fileNameSuffix.isBlank() ? dbo.getObjectId() + "_" + reportName + fileExt
						: dbo.getObjectId() + "_" + reportName + "_" + fileNameSuffix + fileExt;
				if (shouldSave) {
					try {
						final DbDataObject dboExistingFile = getFileDboByLinkedObj(dbo, CC.PRINT, reportName, svr,
								svfs);
						if (shouldOverwrite) {
							DbDataObject dboFile = uploadFile(dbo, fileName, null, DateTime.now(), bstr.toByteArray(),
									CC.PRINT, 0L, svfs);
							if (dboExistingFile != null) {
								invalidateOldReportLinkAndCreateNew(dbo, dboFile, reportName, localeId, svr, svw, svl,
										svfs);
							} else {
								svl.linkObjects(dbo, dboFile, CC.LINK_FILE, null, false);
							}
						} else {
							if (dboExistingFile == null) {
								DbDataObject dboFile = uploadFile(dbo, fileName, null, DateTime.now(),
										bstr.toByteArray(), CC.PRINT, 0L, svfs);
								if (dboFile.getObjectId() != 0) {
									svl.linkObjects(dbo, dboFile, CC.LINK_FILE, null, false);
								}
							}
						}

					} catch (SvException e) {
						log4j.error("Couldn't save and link svarog file for the report: " + reportName, e);
					}
				}
				StreamingOutput fileStream = new StreamingOutput() {
					@Override
					public void write(OutputStream output) throws IOException {
						try {
							IOUtils.write(bstr.toByteArray(), output);
						} catch (Exception e) {
							log4j.error("Error while retirieving file!", e);
						}
					}
				};
				return Response.ok(fileStream, responseType)
						.header("content-disposition", "inline; filename = " + fileName).build();
			}
		} catch (Exception e) {
			log4j.error(e);
			return PerunUtil.handleException(e, jrh, "epi.error.generalError");
		}
		return null;
	}

	private DbDataObject getFileDboByLinkedObj(DbDataObject dbo, String fileType, String reportName, SvReader svr,
			SvFileStore svfs) throws SvException {
		DbDataObject dboFile = null;
		DbDataArray dbArraySvFiles = svfs.getFiles(dbo, fileType, null);
		if (!dbArraySvFiles.isEmpty()) {
			for (DbDataObject dboTempFile : dbArraySvFiles.getItems()) {
				if (dboTempFile.getVal(CC.FILE_NAME) != null && dboTempFile.getVal(CC.FILE_NAME).toString()
						.toLowerCase().contains((dbo.getObjectId().toString() + "_" + reportName).toLowerCase())) {
					dboFile = dboTempFile;
					break;
				}
			}
		}

		return dboFile;
	}

	public static Properties setReportProperties(String reportPath, Long objectId, String localeId, SvReader svr) {
		Properties prop = new Properties();
		prop.put("path", reportPath);
		prop.put("object_id", objectId);
		prop.put("locale_code", localeId);
		prop.put("session", svr.getSessionId());
		prop.put("isDraft", 1);
		return prop;
	}

	public static Properties setReportProperties(String reportPath, Long objectId, Map<String, String[]> requestParams,
			String localeId, SvReader svr) {
		Properties prop = setReportProperties(reportPath, objectId, localeId, svr);
		for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
			String key = entry.getKey();
			if (prop.containsKey(key))
				continue;
			String[] values = entry.getValue();
			if (values != null && values.length > 0 && values[0] != null) {
				prop.put(key, values[0]);
			}
		}
		return prop;
	}

	private static DbDataObject uploadFile(DbDataObject dbo, String fileName, String note, DateTime fileDate,
			byte[] data, String fileType, Long fileStoreId, SvFileStore svfs) throws SvException {
		DbDataObject dboFile = new DbDataObject();
		dboFile.setObjectType(svCONST.OBJECT_TYPE_FILE);
		dboFile.setVal(CC.FILE_TYPE, fileType);
		dboFile.setVal(CC.FILE_NAME, fileName);
		dboFile.setVal(CC.FILE_SIZE, data.length);
		dboFile.setVal(CC.FILE_DATE, fileDate);
		dboFile.setVal(CC.FILE_NOTES, note);
		dboFile.setVal(CC.FILE_STORE_ID, fileStoreId);
		svfs.saveFile(dboFile, dbo, data, true);
		return dboFile;
	}

	private void invalidateOldReportLinkAndCreateNew(DbDataObject dbo, DbDataObject dboFile, String reportName,
			String localeId, SvReader svr, SvWriter svw, SvLink svl, SvFileStore svfs) throws SvException {
		DbDataObject existingFileDbo = getFileDboByLinkedObj(dbo, CC.PRINT, reportName, svr, svfs);
		if (existingFileDbo == null) {
			throw new SvException("reports.error.no_report_found", svr.getInstanceUser());
		}
		DbDataArray svLinkArr = HelperMethods.getLinks(dbo.getObjectId(), dbo.getObjectType(),
				existingFileDbo.getObjectId(), SvReader.getTypeIdByName("SVAROG_FILES"), CC.LINK_FILE, true, svr);
		if (svLinkArr == null || svLinkArr.isEmpty()) {
			throw new SvException("reports.error.no_report_found", svr.getInstanceUser());
		}
		DbDataObject dboLink = svLinkArr.get(0);
		if (dboLink != null) {
			svw.deleteObject(dboLink);
		}
		svl.linkObjects(dbo, dboFile, CC.LINK_FILE, null, false);
	}
}
