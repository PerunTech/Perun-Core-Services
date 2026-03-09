package com.prtech.menu.manager;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.prtech.perun.PerunUtil;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;

@Path("/menu")
public class MenuExporter {
	static final Logger log4j = LogManager.getLogger(MenuExporter.class.getName());

	@Path("/config/export")
	@Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
	public Response exportMenu(@HeaderParam("sessionId") String sessionId, @QueryParam("fileName") String fileName) {
		int size = 50;
		String fileNameStr = fileName == null ? String.format("menu_export_%s.json", LocalDate.now().toString())
				: fileName;

		try (SvReader svr = new SvReader(sessionId)) {
			StreamingOutput fileStream = new StreamingOutput() {
				@Override
				public void write(OutputStream output) throws IOException {
					JsonObject resultData = new JsonObject();
					JsonArray menuArr = new JsonArray();
					JsonArray menuConfArr = new JsonArray();
					int start = 0;

					while (true) {
						DbDataArray menuDbArr = new DbDataArray();
						try {
							menuDbArr = svr.getObjectsByTypeId(SvReader.getTypeIdByName(CC.PERUN_MENU), null, size,
									start);
						} catch (SvException e) {
							log4j.error(e.getMessage(), e);
							break;
						}

						if (menuDbArr == null || menuDbArr.isEmpty()) {
							break;
						}

						for (DbDataObject dboMenu : menuDbArr.getItems()) {
							try {
								JsonObject menuJson = MenuHelper.prepareMenuJsonForDownload(dboMenu, false, svr);
								menuArr.add(menuJson);
							} catch (Exception e) {
								log4j.error(e.getMessage(), e);
							}
						}

						if (menuDbArr.getItems().size() < size) {
							break;
						}

						start += size;
					}

					DbDataArray menuConfDbArr = new DbDataArray();
					try {
						menuConfDbArr = svr.getObjectsByTypeId(SvReader.getTypeIdByName(CC.PERUN_MENU_CONF), null, 0,
								0);
					} catch (SvException e) {
						log4j.error(e.getMessage(), e);
					}

					for (DbDataObject menuConfDbo : menuConfDbArr.getItems()) {
						try {
							menuConfArr.add(MenuHelper.removeRepoData(menuConfDbo.toSimpleJson()));
						} catch (Exception e) {
							log4j.error(e.getMessage(), e);
						}
					}

					resultData.add("menuData", menuArr);
					resultData.add("menuConfData", menuConfArr);
					output.write(resultData.toString().getBytes());
				}
			};
			return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
					.header("content-disposition", "attachment; filename = " + fileNameStr).build();
		} catch (Exception e) {
			log4j.error("Error exporting menu configuration: ", e);
			return PerunUtil.handleException(e, "perun.error.generalError");
		}
	}
}
