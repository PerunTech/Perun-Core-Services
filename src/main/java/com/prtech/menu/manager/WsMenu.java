package com.prtech.menu.manager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
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
import com.prtech.perun.PerunUtil;
import com.prtech.perun.services.ws.DbReader;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;
import com.prtech.svarog_common.ResponseHandler;
import com.prtech.svarog_common.ResponseHandler.MessageType;

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
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(sessionId)) {
			DbDataObject menuRoot = new DbReader().searchDbObjectBySingleFilter(DbCompareOperand.EQUAL,
					SvReader.getTypeIdByName(CC.PERUN_MENU), CC.MENU_CODE, rootMenuCode, svr);
			if (menuRoot == null) {
				jrh.create(MessageType.ERROR, "Menu not found", null, new JsonObject());
				return Response.status(Response.Status.NOT_FOUND).entity(jrh.getAll().toString()).build();
			}
			JsonObject resultJson = MenuHelper.buildFullHierarchy(menuRoot, svr, new HashSet<>());
			return Response.ok(resultJson.toString(), MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
			log4j.error("Error generating menu: ", e);
			return PerunUtil.handleException(e, "Error generating menu");
		}
	}

	/**
	 * Endpoint for generation of the menu configuration. The values sent in the
	 * POST data will be replaced in the menu configuration.
	 * 
	 * @param sessionId    Session ID
	 * @param rootMenuCode Root menu code
	 * @param entity       JSON object containing the values that will be replaced
	 *                     in the menu configuration
	 * @return JSON with merged buttonArray content
	 */
	@POST
	@Path("/getMenu/{sid}/{rootMenuCode}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMenu(@PathParam("sid") String sessionId, @PathParam("rootMenuCode") String rootMenuCode,
			String entity) {
		ResponseHandler jrh = new ResponseHandler();
		JsonObject requestData = new JsonObject();
		try (SvReader svr = new SvReader(sessionId)) {
			try {
				requestData = new Gson().fromJson(entity, JsonObject.class);
			} catch (Exception e) {
				jrh.create(MessageType.ERROR, "Request body has bad format", null, new JsonObject());
				return Response.status(Response.Status.BAD_REQUEST).entity(jrh.getAll().toString()).build();
			}
			DbDataObject menuRoot = new DbReader().searchDbObjectBySingleFilter(DbCompareOperand.EQUAL,
					SvReader.getTypeIdByName(CC.PERUN_MENU), CC.MENU_CODE, rootMenuCode, svr);
			if (menuRoot == null) {
				jrh.create(MessageType.ERROR, "Menu not found", null, new JsonObject());
				return Response.status(Response.Status.NOT_FOUND).entity(jrh.getAll().toString()).build();
			}
			JsonObject resultJson = MenuHelper.buildFullHierarchy(menuRoot, svr, new HashSet<>(), requestData);
			resultJson = MenuHelper.applyDataToObject(resultJson, requestData, svr);
			jrh.create(MessageType.SUCCESS, "Menu successfully generated", null, resultJson);
			return Response.ok(jrh.getAll().toString()).build();
		} catch (Exception e) {
			log4j.error("Error generating menu: ", e);
			return PerunUtil.handleException(e, "Error generating menu");
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
		ResponseHandler jrh = new ResponseHandler();
		JsonObject requestData;
		try {
			requestData = new Gson().fromJson(entity, JsonObject.class);
		} catch (Exception e) {
			jrh.create(MessageType.ERROR, "Request body has bad format", null, new JsonObject());
			return Response.status(Response.Status.BAD_REQUEST).entity(jrh.getAll().toString()).build();
		}
		return generateGetMenuResponse(sessionId, requestData);
	}

	/**
	 * Return full menu config for the object that is sent in the request. GET
	 * version
	 * 
	 * @param sessionId  User's session ID
	 * @param objectId   Object ID we want to generate the menu for
	 * @param objectType The type of the object
	 * @return
	 */
	@GET
	@Path("/getMenu/{sid}/{objectId}/{objectType}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMenu(@PathParam("sid") String sessionId, @PathParam("objectId") Long objectId,
			@PathParam("objectType") String objectType) {
		ResponseHandler jrh = new ResponseHandler();
		JsonObject requestData = new JsonObject();

		try (SvReader svr = new SvReader(sessionId)) {
			DbDataObject dbo = svr.getObjectById(objectId, SvReader.getTypeIdByName(objectType), null);
			if (dbo == null) {
				jrh.create(MessageType.ERROR, "Object not found", null, new JsonObject());
				return Response.status(Response.Status.BAD_REQUEST).entity(jrh.getAll().toString()).build();
			}

			JsonObject dboJson = dbo.toSimpleJson();
			for (String key : dboJson.keySet()) {
				requestData.add(key.toUpperCase(), dboJson.get(key));
			}
		} catch (Exception e) {
			log4j.error("Error fetching object: ", e);
			return PerunUtil.handleException(e, "Error fetching object");
		}

		return generateGetMenuResponse(sessionId, requestData);
	}

	/**
	 * Return full menu config for the object that is sent in the request. GET
	 * version
	 * 
	 * @param sessionId  User's session ID
	 * @param objectId   Object ID we want to generate the menu for
	 * @param objectType The type of the object
	 * @param rootMenuCode Default rootMenuCode
	 * @return
	 */
	@GET
	@Path("/getMenu2/{sid}/{objectId}/{objectType}/{rootMenuCode}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMenu2(@PathParam("sid") String sessionId, @PathParam("objectId") Long objectId,
			@PathParam("objectType") String objectType, @PathParam("rootMenuCode") String rootMenuCode) {
		ResponseHandler jrh = new ResponseHandler();
		JsonObject requestData = new JsonObject();

		try (SvReader svr = new SvReader(sessionId)) {
			DbDataObject dbo = svr.getObjectById(objectId, SvReader.getTypeIdByName(objectType), null);
			if (dbo == null) {
				jrh.create(MessageType.ERROR, "Object not found", null, new JsonObject());
				return Response.status(Response.Status.BAD_REQUEST).entity(jrh.getAll().toString()).build();
			}

			JsonObject dboJson = dbo.toSimpleJson();
			for (String key : dboJson.keySet()) {
				requestData.add(key.toUpperCase(), dboJson.get(key));
			}
		} catch (Exception e) {
			log4j.error("Error fetching object: ", e);
			return PerunUtil.handleException(e, "Error fetching object");
		}

		return generateGetMenuResponse(sessionId, requestData, rootMenuCode);
	}

	/**
	 * Common logic for generating menu response from request data
	 * 
	 * @param sessionId   Session ID
	 * @param requestData JSON object containing the request data
	 * @return Response with menu configuration or error
	 */
	private Response generateGetMenuResponse(String sessionId, JsonObject requestData) {
		ResponseHandler jrh = new ResponseHandler();
		DbDataObject menuRoot;

		try (SvReader svr = new SvReader(sessionId)) {
			menuRoot = MenuHelper.findMenuCodeForObject(requestData, svr);
			if (menuRoot == null) {
				jrh.create(MessageType.ERROR, "Menu for this type of object was not found", null, new JsonObject());
				return Response.status(Response.Status.BAD_REQUEST).entity(jrh.getAll().toString()).build();
			}

			JsonObject resultJson = MenuHelper.buildFullHierarchy(menuRoot, svr, new HashSet<>(), requestData);
			resultJson = MenuHelper.applyDataToObject(resultJson, requestData, svr);
			Set<String> missingData = MenuHelper.findPlaceholders(resultJson);

			if (!missingData.isEmpty()) {
				jrh.create(MessageType.ERROR, "The following placeholders were not replaced", missingData.toString(),
						new JsonObject());
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(jrh.getAll().toString()).build();
			}

			jrh.create(MessageType.SUCCESS, "Menu successfully generated", null, resultJson);
			return Response.ok(jrh.getAll().toString()).build();
		} catch (Exception e) {
			log4j.error("Error generating menu: ", e);
			return PerunUtil.handleException(e, "Error generating menu");
		}
	}

	/**
	 * Common logic for generating menu response from request data
	 * 
	 * @param sessionId   Session ID
	 * @param requestData JSON object containing the request data
	 * @return Response with menu configuration or error
	 */
	private Response generateGetMenuResponse(String sessionId, JsonObject requestData, String rootMenuCode) {
		ResponseHandler jrh = new ResponseHandler();
		DbDataObject menuRoot;

		try (SvReader svr = new SvReader(sessionId)) {
			menuRoot = new DbReader().searchDbObjectBySingleFilter(DbCompareOperand.EQUAL,
					SvReader.getTypeIdByName(CC.PERUN_MENU), CC.MENU_CODE, rootMenuCode, svr);
			if (menuRoot == null) {
				jrh.create(MessageType.ERROR, "Menu not found", null, new JsonObject());
				return Response.status(Response.Status.NOT_FOUND).entity(jrh.getAll().toString()).build();
			}
			
			JsonObject resultJson = MenuHelper.buildFullHierarchy(menuRoot, svr, new HashSet<>(), requestData);
			resultJson = MenuHelper.applyDataToObject(resultJson, requestData, svr);
			Set<String> missingData = MenuHelper.findPlaceholders(resultJson);

			if (!missingData.isEmpty()) {
				jrh.create(MessageType.ERROR, "The following placeholders were not replaced", missingData.toString(),
						new JsonObject());
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(jrh.getAll().toString()).build();
			}

			jrh.create(MessageType.SUCCESS, "Menu successfully generated", null, resultJson);
			return Response.ok(jrh.getAll().toString()).build();
		} catch (Exception e) {
			log4j.error("Error generating menu: ", e);
			return PerunUtil.handleException(e, "Error generating menu");
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
		ResponseHandler jrh = new ResponseHandler();
		JsonObject requestData = new JsonObject();
		DbDataObject menuDbo = null;
		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			try {
				requestData = new Gson().fromJson(entity, JsonObject.class);
			} catch (Exception e) {
				jrh.create(MessageType.ERROR, "Request body has bad format", null, new JsonObject());
				return Response.status(Response.Status.BAD_REQUEST).entity(jrh.getAll().toString()).build();
			}
			List<String> missing = MenuHelper.checkAndReturnMissingKeys(requestData, Arrays.asList(CC.OBJECT_ID));
			if (!missing.isEmpty()) {
				jrh.create(MessageType.ERROR, "Missing required keys in request data", missing.toString(),
						new JsonObject());
				return Response.status(Response.Status.BAD_REQUEST).entity(jrh.getAll().toString()).build();
			}
			menuDbo = MenuHelper.saveMenuHelper(requestData, svr);
			if (menuDbo != null) {
				svw.saveObject(menuDbo);
				MenuHelper.updateCache(CC.PERUN_MENU, CC.PM, CC.PERUN_MENU);
				jrh.create(MessageType.SUCCESS, "Menu item successfully saved", null, menuDbo.toSimpleJson());
			} else {
				jrh.create(MessageType.ERROR, "The item couldn't be saved", null, new JsonObject());
			}
			return Response.ok(jrh.getAll().toString()).build();
		} catch (UserNotAuthorizedError e) {
			log4j.error("User is not authorized to save or edit the menu: ", e);
			jrh.create(MessageType.ERROR, "User is not authorized to save or edit the menu", e.getMessage(),
					new JsonObject());
			return Response.status(Response.Status.UNAUTHORIZED).entity(jrh.getAll().toString()).build();
		} catch (MenuError e) {
			log4j.error("Error while adding menu: ", e);
			jrh.create(MessageType.ERROR, "Error while adding menu", e.getMessage(), new JsonObject());
			return Response.status(Response.Status.BAD_REQUEST).entity(jrh.getAll().toString()).build();
		} catch (Exception e) {
			log4j.error("Error while adding menu: ", e);
			return PerunUtil.handleException(e, "Error while adding menu");
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
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			DbDataObject menuRoot = new DbReader().searchDbObjectBySingleFilter(DbCompareOperand.EQUAL,
					SvReader.getTypeIdByName(CC.PERUN_MENU), CC.MENU_CODE, rootMenuCode, svr);
			if (menuRoot == null) {
				jrh.create(MessageType.ERROR, "Menu not found", null, new JsonObject());
				return Response.status(Response.Status.NOT_FOUND).entity(jrh.getAll().toString()).build();
			}
			if (!MenuHelper.checkUserHasPermission(menuRoot, Arrays.asList("FULL", "WRITE"), svr)) {
				jrh.create(MessageType.ERROR, "User does not have permission to delete this menu", null,
						new JsonObject());
				return Response.status(Response.Status.UNAUTHORIZED).entity(jrh.getAll().toString()).build();
			}
			MenuHelper.deleteMenuHelper(rootMenuCode, svr);
			svw.deleteObject(menuRoot);
			jrh.create(MessageType.SUCCESS, "Menu item successfully deleted", null, menuRoot.toSimpleJson());
			return Response.ok(jrh.getAll().toString()).build();
		} catch (MenuError e) {
			log4j.error("Error while removing menu: ", e);
			jrh.create(MessageType.ERROR, "Error while removing menu", e.getMessage(), new JsonObject());
			return Response.status(Response.Status.BAD_REQUEST).entity(jrh.getAll().toString()).build();
		} catch (Exception e) {
			log4j.error("Error removing menu: ", e);
			return PerunUtil.handleException(e, "Error removing menu");
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
			return PerunUtil.handleException(e, "Error downloading menu");
		}
	}

	/**
	 * Uploads a menu configuration file to the system.
	 * 
	 * The uploaded file must be in UTF-8 format, contain valid JSON.
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
			@FormDataParam("file") FormDataContentDisposition fileDetail, @Context HttpServletRequest request) {
		byte[] data;
		String fileData = null;
		JsonObject menuJson = new JsonObject();
		DbDataObject menuDbo = null;
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			try {
				data = IOUtils.toByteArray(fileInput);
			} catch (IOException e) {
				throw new SvException("perun.error.read_failed", svr.getInstanceUser(), e);
			}

			try {
				fileData = new String(data, StandardCharsets.UTF_8);
			} catch (Exception e) {
				throw new SvException("perun.error.invalid_encoding", svr.getInstanceUser(), e);
			}

			if (fileData != null) {
				try {
					menuJson = new Gson().fromJson(fileData, JsonObject.class);
				} catch (Exception e) {
					throw new SvException("perun.error.invalid_json_format", svr.getInstanceUser(), e);
				}
			}
			if (menuJson != null && menuJson.size() != 0) {
				menuDbo = MenuHelper.saveMenuHelper(menuJson, svr);
				if (menuDbo != null) {
					svw.saveObject(menuDbo);
					jrh.create(MessageType.SUCCESS, I18n.getText("perun.success.upload_menu"), null,
							menuDbo.toSimpleJson());
					return Response.ok(jrh.getAll().toString()).build();
				} else {
					throw new SvException("perun.error.save_failed", svr.getInstanceUser());
				}
			} else {
				throw new SvException("perun.error.empty_json", svr.getInstanceUser());
			}
		} catch (MenuError e) {
			log4j.error("Error while uploading menu: ", e);
			jrh.create(MessageType.ERROR, I18n.getText("perun.error.upload_menu_failed"), e.getMessage(),
					new JsonObject());
			return Response.status(Response.Status.BAD_REQUEST).entity(jrh.getAll().toString()).build();
		} catch (Exception e) {
			log4j.error("Error while uploading menu: ", e);
			return PerunUtil.handleException(e, "Error while uploading menu", null, 400);
		}
	}
}