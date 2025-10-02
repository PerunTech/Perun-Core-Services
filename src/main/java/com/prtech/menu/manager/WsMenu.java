package com.prtech.menu.manager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.prtech.menu.manager.MenuExceptions.MenuError;
import com.prtech.menu.manager.MenuExceptions.UserNotAuthorizedError;
import com.prtech.perun.services.ws.DbReader;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;

/**
 * WsMenu class for generating merged menu definitions. Uses PERUN_MENU as
 * source table, and supports recursive merging based on IMPORT_MENU as KEY
 * field in MENU_CONF JSON.
 */
@Path("/Menu")
public class WsMenu {

	private static final Logger log4j = LogManager.getLogger(WsMenu.class);

	/**
	 * Web service endpoint to generate full merged menu config
	 * 
	 * @param sessionId    Session ID
	 * @param rootMenuCode Root menu code
	 * @return JSON with merged buttonArray content
	 */
	@GET
	@Path("/generate/{sid}/{rootMenuCode}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response generateMenu(@PathParam("sid") String sessionId, @PathParam("rootMenuCode") String rootMenuCode) {
		try (SvReader svr = new SvReader(sessionId)) {
			DbDataObject menuRoot = new DbReader().searchDbObjectBySingleFilter(DbCompareOperand.EQUAL,
					SvReader.getTypeIdByName(CC.PERUN_MENU), CC.MENU_CODE, rootMenuCode, svr);
			if (menuRoot == null)
				return Response.status(Response.Status.NOT_FOUND).entity("Menu not found").build();
			JsonObject resultJson = MenuHelper.buildFullHierarchy(menuRoot, svr, new HashSet<>());
			return Response.ok(resultJson.toString(), MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
			log4j.error("Error generating menu: ", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		}
	}

	/**
	 * Return full menu config for the object that is sent in the request
	 * 
	 * @param sessionId Session ID
	 * @param entity    JSON object containing the whole DB object
	 * @return
	 */
	@POST
	@Path("/getMenu/{sid}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMenu(@PathParam("sid") String sessionId, String entity) {
		JsonObject requestData = new JsonObject();
		DbDataObject menuRoot = null;
		try (SvReader svr = new SvReader(sessionId)) {
			try {
				requestData = new Gson().fromJson(entity, JsonObject.class);
			} catch (Exception e) {
				return Response.status(Response.Status.BAD_REQUEST).entity("Request body has bad format").build();
			}
			menuRoot = MenuHelper.findMenuCodeForObject(requestData, svr);
			if (menuRoot == null) {
				return Response.status(Response.Status.BAD_REQUEST).entity("Menu for this type of object was not found")
						.build();
			}
			JsonObject resultJson = MenuHelper.buildFullHierarchy(menuRoot, svr, new HashSet<>());
			return Response.ok(resultJson.toString(), MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
			log4j.error("Error generating menu: ", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		}
	}

	/**
	 * Web service for adding a new menu in the system
	 * 
	 * @param sessionId Session ID
	 * @param formVals  form parameters map holding info about the new menu
	 * @return JSON with info about the new menu object
	 */
	@POST
	@Path("/add/{sid}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response addMenu(@PathParam("sid") String sessionId, String entity) {
		JsonObject requestData = new JsonObject();
		DbDataObject menuDbo = null;
		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			try {
				requestData = new Gson().fromJson(entity, JsonObject.class);
			} catch (Exception e) {
				return Response.status(Response.Status.BAD_REQUEST).entity("Request body has bad format").build();
			}
			List<String> missing = MenuHelper.checkAndReturnMissingKeys(requestData, Arrays.asList(CC.OBJECT_ID));
			if (!missing.isEmpty()) {
				return Response.status(Response.Status.BAD_REQUEST)
						.entity("Missing required keys in request data: " + missing.toString()).build();
			}
			menuDbo = MenuHelper.saveMenuHelper(requestData, svr);
			if (menuDbo != null) {
				svw.saveObject(menuDbo);
				String responseObj = menuDbo.toSimpleJson().toString();
				return Response.ok(responseObj, MediaType.APPLICATION_JSON).build();
			} else {
				return Response.ok("The item couldn't be saved", MediaType.APPLICATION_JSON).build();
			}
		} catch (UserNotAuthorizedError e) {
			log4j.error("User is not authorized to save or edit the menu: ", e);
			return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
		} catch (MenuError e) {
			log4j.error("Error while adding menu: ", e);
			return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
		} catch (Exception e) {
			log4j.error("Error while adding menu: ", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		}
	}

	/**
	 * Web service for removing a menu from the system by it's menu code
	 * 
	 * @param sessionId    Session ID
	 * @param rootMenuCode Root menu code
	 * @return
	 */
	@GET
	@Path("/remove/{sid}/{rootMenuCode}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response removeMenu(@PathParam("sid") String sessionId, @PathParam("rootMenuCode") String rootMenuCode) {
		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			DbDataObject menuRoot = new DbReader().searchDbObjectBySingleFilter(DbCompareOperand.EQUAL,
					SvReader.getTypeIdByName(CC.PERUN_MENU), CC.MENU_CODE, rootMenuCode, svr);
			if (menuRoot == null) {
				return Response.status(Response.Status.NOT_FOUND).entity("Menu not found").build();
			}
			if (!MenuHelper.checkUserHasPermission(menuRoot, Arrays.asList("FULL", "WRITE"), svr)) {
				return Response.status(Response.Status.UNAUTHORIZED)
						.entity("User does not have permission to delete this menu").build();
			}
			MenuHelper.deleteMenuHelper(rootMenuCode, svr);
			String responseObj = menuRoot.toSimpleJson().toString();
			svw.deleteObject(menuRoot);
			return Response.ok(responseObj, MediaType.APPLICATION_JSON).build();
		} catch (MenuError e) {
			log4j.error("Error while removing menu: ", e);
			return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
		} catch (Exception e) {
			log4j.error("Error removing menu: ", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		}
	}

	/**
	 * This endpoint retrieves a menu configuration by its root menu code and
	 * exports it as a downloadable JSON file
	 * 
	 * @param sessionId      Session ID
	 * @param rootMenuCode   the unique code identifying the menu to download
	 * @param resolveImports whether to include imported menu references in the
	 *                       export
	 * @return Response containing the menu JSON as an octet-stream attachment, or
	 *         error message on failure
	 */
	@GET
	@Path("/download/{sid}/{rootMenuCode}/{resolveImports}")
	@Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
	public Response downloadMenu(@PathParam("sid") String sessionId, @PathParam("rootMenuCode") String rootMenuCode,
			@PathParam("resolveImports") Boolean resolveImports) {
		try (SvReader svr = new SvReader(sessionId)) {
			DbDataObject menuRoot = new DbReader().searchDbObjectBySingleFilter(DbCompareOperand.EQUAL,
					SvReader.getTypeIdByName(CC.PERUN_MENU), CC.MENU_CODE, rootMenuCode, svr);
			if (menuRoot == null) {
				return Response.status(Response.Status.NOT_FOUND).entity("Menu not found").build();
			}
			if (!MenuHelper.checkUserHasPermission(menuRoot, Arrays.asList("FULL", "READ", "WRITE"), svr)) {
				return Response.status(Response.Status.UNAUTHORIZED)
						.entity("User does not have permission to download this menu").build();
			}
			StreamingOutput fileStream = new StreamingOutput() {
				@Override
				public void write(OutputStream output) throws IOException {
					JsonObject menuJson = new JsonObject();
					try {
						menuJson = MenuHelper.prepareMenuJsonForDownload(menuRoot, resolveImports, svr);
					} catch (Exception e) {
						log4j.error("Error while preparing menu for download", e);
					}
					output.write(menuJson.toString().getBytes());
				}
			};
			return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
					.header("content-disposition", "attachment; filename = " + rootMenuCode + ".json").build();
		} catch (Exception e) {
			log4j.error("Error downloading menu: ", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		}
	}

	/**
	 * Uploads a menu configuration file to the system.
	 * 
	 * The uploaded file must be in UTF-8 format, contain valid JSON, and not exceed
	 * 5MB in size.
	 * 
	 * @param sessionId  Session ID
	 * @param fileInput  the input stream of the uploaded file containing menu JSON
	 *                   data
	 * @param fileDetail metadata about the uploaded file
	 * @return Response containing the saved menu object as JSON on success, or
	 *         error message on failure
	 */
	@POST
	@Path("/upload/{sid}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_JSON)
	public Response uploadMenu(@PathParam("sid") String sessionId, @FormDataParam("file") InputStream fileInput,
			@FormDataParam("file") FormDataContentDisposition fileDetail) {
		byte[] data;
		String fileData = null;
		JsonObject menuJson = new JsonObject();
		DbDataObject menuDbo = null;
		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			try {
				data = IOUtils.toByteArray(fileInput);
			} catch (IOException e) {
				log4j.error("Error reading file: ", e);
				return Response.status(Response.Status.BAD_REQUEST).entity("Error reading uploaded file").build();
			}

			try {
				fileData = new String(data, StandardCharsets.UTF_8);
			} catch (Exception e) {
				return Response.status(Response.Status.BAD_REQUEST).entity("File is not in UTF-8 format").build();
			}

			if (data.length > 5 * 1024 * 1024) {
				return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE)
						.entity("File is bigger than the allowed size of 5MB").build();
			}

			if (fileData != null) {
				try {
					menuJson = new Gson().fromJson(fileData, JsonObject.class);
				} catch (Exception e) {
					return Response.status(Response.Status.BAD_REQUEST).entity("File does not contain valid JSON")
							.build();
				}
			}
			if (menuJson != null && menuJson.size() != 0) {
				menuDbo = MenuHelper.saveMenuHelper(menuJson, svr);
				if (menuDbo != null) {
					svw.saveObject(menuDbo);
					String responseObj = menuDbo.toSimpleJson().toString();
					return Response.ok(responseObj, MediaType.APPLICATION_JSON).build();
				} else {
					return Response.status(Response.Status.BAD_REQUEST).entity("The item couldn't be saved").build();
				}
			} else {
				return Response.status(Response.Status.BAD_REQUEST).entity("Input JSON is empty").build();
			}
		} catch (MenuError e) {
			log4j.error("Error while uploading menu: ", e);
			return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
		} catch (Exception e) {
			log4j.error("Error while uploading menu: ", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		}
	}
}