package com.prtech.perun.services.ws;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.prtech.perun.PerunUtil;
import com.prtech.svarog.I18n;
import com.prtech.svarog.Sv;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvParameter;
import com.prtech.svarog.SvPerunInstance;
import com.prtech.svarog.SvPerunManager;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.ResponseHandler;
import com.prtech.svarog_common.ResponseHandler.MessageType;

@Path("/WsConf")
public class WsConf {
	static final Logger log4j = SvConf.getLogger(WsConf.class);

	private static String getLocaleId(SvReader svr) {
		String locale = SvConf.getDefaultLocale();
		try {
			if (svr.getUserLocale(SvReader.getUserBySession(svr.getSessionId())) != null) {
				DbDataObject localeObj = svr.getUserLocale(SvReader.getUserBySession(svr.getSessionId()));
				if (localeObj.getVal("LOCALE_ID").toString() != null)
					locale = localeObj.getVal("LOCALE_ID").toString();
			}
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
		}
		return locale;
	}

	/**
	 * returns server location from svarog.properties param "frontend.server"
	 * 
	 * @return server location
	 */
	@Path("/getServer")
	@GET
	@Produces("text/html;charset=utf-8")
	public String getServer(@Context HttpServletRequest httpRequest) {
		String server = "window.server=" + SvConf.getParam("frontend.server");
		return server;
	}

	/**
	 * build json for Module cards for GUI, which are entry point of application or
	 * module Configuration of the cards is being fetched from table CARD_CONF and
	 * it returns json array to be iterated by front end component.
	 * 
	 * @param token
	 * @param httpRequest
	 * @return f.r
	 */
	@Path("/getConfigModuleCardsEntry/{token}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response getConfigModuleCardsEntry(@PathParam("token") String token,
			@Context HttpServletRequest httpRequest) {
		JsonArray jso;
		ResponseHandler jrh = new ResponseHandler();
		try {
			jso = getConfigModules(token);
			jrh.create(MessageType.SUCCESS, I18n.getText("load.plugins"), I18n.getText("load.plugins"), jso);
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				if (log4j.isDebugEnabled())
					log4j.error(e.getFormattedMessage());
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(401).entity(jrh.getAll().toString()).build();
			} else {
				log4j.error(e.getFormattedMessage(), e);
				jrh.create(MessageType.ERROR, I18n.getText("load.plugins"), e.getFormattedMessage(), new JsonObject());
			}
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * Method to return a parameter value for a specific object.
	 * 
	 * @param token     The session id of the authenticated user
	 * @param paramName the name of the parameter
	 * @param parentId  the parentID of the object to which the parameter applies
	 * @return Response object
	 */
	@Path("/params/get/{token}/{paramName}/{parentId}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response getParam(@PathParam("token") String token, @PathParam("paramName") String paramName,
			@PathParam("parentId") Long parentId, @Context HttpServletRequest httpRequest) {
		JsonObject jo = new JsonObject();
		ResponseHandler jrh = new ResponseHandler();
		try (SvParameter svp = new SvParameter(token)) {
			Object value = svp.getUserParam(paramName, parentId);
			if (value instanceof String)
				jo.addProperty("VALUE", (String) value);
			else if (value instanceof Boolean)
				jo.addProperty("VALUE", (Boolean) value);
			else if (value instanceof Number)
				jo.addProperty("VALUE", (Number) value);
			else
				jo.addProperty("VALUE", value.toString());

		} catch (SvException e) {
			return PerunUtil.handleException(e, jrh, token);
		}
		return Response.status(200).entity(jo.toString()).build();
	}

	/**
	 * Method to return a parameter value for a specific object.
	 * 
	 * @param paramName the name of the parameter
	 * @return Response object with the value if any, otherwise empty JSON object
	 */
	@Path("/params/get/sys/{paramName}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response getParam(@PathParam("paramName") String paramName, @Context HttpServletRequest httpRequest) {
		JsonObject jo = new JsonObject();
		ResponseHandler jrh = new ResponseHandler();
		try {
			Object value = SvParameter.getSysParam(paramName, false);
			if (value != null) {
				if (value instanceof String)
					jo.addProperty("VALUE", (String) value);
				else if (value instanceof Boolean)
					jo.addProperty("VALUE", (Boolean) value);
				else if (value instanceof Number)
					jo.addProperty("VALUE", (Number) value);
				else
					jo.addProperty("VALUE", value.toString());
			}
		} catch (SvException e) {
			return PerunUtil.handleException(e, jrh, paramName);
		}
		return Response.status(200).entity(jo.toString()).build();
	}

	/**
	 * Method to return a parameter value for a specific object.
	 * 
	 * @param paramName the name of the parameter
	 * @return Response object with the value if any, otherwise empty JSON object
	 */
	@Path("/params/get/conf/{sessionId}/{paramName}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response getConf(@PathParam("sessionId") String sessionId, @PathParam("paramName") String paramName,
			@Context HttpServletRequest httpRequest) {
		JsonObject jo = new JsonObject();
		ResponseHandler jrh = new ResponseHandler();
		try (SvParameter svp = new SvParameter(sessionId)) {
			if (paramName.startsWith("sys.gis"))
				jo.addProperty("VALUE", (String) SvConf.getParam(paramName));
		} catch (SvException e) {
			return PerunUtil.handleException(e, jrh, "Error fetching parameter");
		}
		return Response.status(200).entity(jo.toString()).build();
	}

	/**
	 * Method to set a String parameter value for a specific object.
	 * 
	 * @param token      The session id of the authenticated user
	 * @param paramName  the name of the parameter
	 * @param parentId   the parentID of the object to which the parameter applies
	 * @param paramValue the value of parameter
	 * @return Response object
	 */
	@Path("/params/set/string/{token}/{paramName}/{parentId}/{paramValue}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response setParam(@PathParam("token") String token, @PathParam("paramName") String paramName,
			@PathParam("paramValue") String paramValue, @PathParam("parentId") Long parentId,
			@Context HttpServletRequest httpRequest) {
		ResponseHandler jrh = new ResponseHandler();
		try (SvParameter svp = new SvParameter(token)) {
			SvParameter.setUserParam(paramName, paramValue, parentId);
			jrh.create(MessageType.SUCCESS, "Saved");
		} catch (SvException e) {
			return PerunUtil.handleException(e, jrh, token);
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * Method to set a Boolean parameter value for a specific object.
	 * 
	 * @param token      The session id of the authenticated user
	 * @param paramName  the name of the parameter
	 * @param parentId   the parentID of the object to which the parameter applies
	 * @param paramValue the value of parameter
	 * @return Response object
	 */
	@Path("/params/set/boolean/{token}/{paramName}/{parentId}/{paramValue}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response setParam(@PathParam("token") String token, @PathParam("paramName") String paramName,
			@PathParam("paramValue") Boolean paramValue, @PathParam("parentId") Long parentId,
			@Context HttpServletRequest httpRequest) {
		ResponseHandler jrh = new ResponseHandler();
		try (SvParameter svp = new SvParameter(token)) {
			SvParameter.setUserParam(paramName, paramValue, parentId);
			jrh.create(MessageType.SUCCESS, "Saved");
		} catch (SvException e) {
			return PerunUtil.handleException(e, jrh, token);
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * Method to set a Boolean parameter value for a specific object.
	 * 
	 * @param token      The session id of the authenticated user
	 * @param paramName  the name of the parameter
	 * @param parentId   the parentID of the object to which the parameter applies
	 * @param paramValue the value of parameter
	 * @return Response object
	 */
	@Path("/params/set/long/{token}/{paramName}/{parentId}/{paramValue}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response setParam(@PathParam("token") String token, @PathParam("paramName") String paramName,
			@PathParam("paramValue") Long paramValue, @PathParam("parentId") Long parentId,
			@Context HttpServletRequest httpRequest) {
		ResponseHandler jrh = new ResponseHandler();
		try (SvParameter svp = new SvParameter(token)) {
			SvParameter.setUserParam(paramName, paramValue, parentId);
			jrh.create(MessageType.SUCCESS, "Saved");
		} catch (SvException e) {
			return PerunUtil.handleException(e, jrh, token);
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * Method to set a Boolean parameter value for a specific object.
	 * 
	 * @param token      The session id of the authenticated user
	 * @param paramName  the name of the parameter
	 * @param parentId   the parentID of the object to which the parameter applies
	 * @param paramValue the value of parameter
	 * @return Response object
	 */
	@Path("/params/set/double/{token}/{paramName}/{parentId}/{paramValue}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response setParam(@PathParam("token") String token, @PathParam("paramName") String paramName,
			@PathParam("paramValue") Double paramValue, @PathParam("parentId") Long parentId,
			@Context HttpServletRequest httpRequest) {
		ResponseHandler jrh = new ResponseHandler();
		try (SvParameter svp = new SvParameter(token)) {
			SvParameter.setUserParam(paramName, paramValue, parentId);
			jrh.create(MessageType.SUCCESS, "Saved");
		} catch (SvException e) {
			return PerunUtil.handleException(e, jrh, token);
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	public JsonArray getConfigModules(String token) throws SvException {
		SvPerunManager spm = null;
		SvReader svr = null;
		JsonArray jArray = new JsonArray();
		Boolean accessCard = true;
		String localeId = null;
		try {
			Gson gson = new Gson();
			svr = new SvReader(token);
			spm = new SvPerunManager(token);
			localeId = getLocaleId(svr);
			if (svr.isAdmin()) {
				accessCard = true;
			} else {
				accessCard = false;
			}

//			if (accessCard) {
			JsonObject jObj = new JsonObject();
			for (Entry<String, SvPerunInstance> plugins : spm.getPerunPlugins()) {
				SvPerunInstance dbocard = plugins.getValue();
				jObj = new JsonObject();
				jObj.addProperty("id", dbocard.getPlugin().getContextName());
				jObj.addProperty("title", I18n.getText(localeId, dbocard.getLabelCode()));
				jObj.addProperty("text", I18n.getLongText(localeId, dbocard.getLabelCode()));
				jObj.addProperty("cardHidden", cardIsHidden(dbocard.getDboPlugin()));
				if (!svr.isAdmin()) {

					jObj.addProperty("cardDirectAccess", manageCardAccess(dbocard.getDboPlugin(), svr));
				} else {
					jObj.addProperty("cardDirectAccess", false);
				}
				jObj.addProperty("hasPersistReducer", hasReducer(dbocard.getDboPlugin()));
				if (dbocard.getImgPath() != null) {
					jObj.addProperty("imgPath", dbocard.getImgPath());
				} else {
					jObj.addProperty("imgPath", "/perun/assets/img/login/afpzrr_trans_grey.png");
				}
				List<String> deps = dbocard.getPlugin().dependencies();
				if (deps != null) {
					jObj.addProperty("deps", gson.toJson(deps));
				}
				jObj.addProperty("js", dbocard.getJsPath());
				jArray.add(jObj);
			}
			// } else {
			// log4j.error(
			// "the user is in administrators group, but it is not an admin therefor he
			// doesn't have an access to card menu");
			// }
		} catch (SvException e) {
			log4j.error(e.getMessage());
			throw (e);
		} finally {
			if (svr != null) {
				svr.release();
				svr.close();
			}
			if (spm != null) {
				spm.release();
				spm.close();
			}
		}
		return jArray;
	}

	private boolean cardIsHidden(DbDataObject dboPlugin) throws SvException {

		boolean hidden = false;
		try {
			JsonObject meta = (new Gson()).fromJson((String) dboPlugin.getVal(Sv.GUI_METADATA), JsonObject.class);
			if (meta != null && meta.has("cardHidden"))
				hidden = meta.get("cardHidden").getAsBoolean();
		} catch (Exception e) {
			log4j.error("Error fetching cardHidden status from Plugin", e);
		}
		return hidden;
	}

	private boolean hasReducer(DbDataObject dboPlugin) throws SvException {

		boolean hasReducer = false;
		try {
			JsonObject meta = (new Gson()).fromJson((String) dboPlugin.getVal(Sv.GUI_METADATA), JsonObject.class);
			if (meta != null && meta.has("hasPersistReducer"))
				hasReducer = meta.get("hasPersistReducer").getAsBoolean();
		} catch (Exception e) {
			log4j.error("Error fetching hasPersistReducer status from Plugin", e);
		}
		return hasReducer;
	}

	private boolean manageCardAccess(DbDataObject dboPlugin, SvReader svr) throws SvException {

		boolean directAccess = false;
		JsonArray groupAccess = new JsonArray();
		try {
			JsonObject meta = (new Gson()).fromJson((String) dboPlugin.getVal(Sv.GUI_METADATA), JsonObject.class);

			if (meta != null && meta.has("directAccess"))
				directAccess = meta.get("directAccess").getAsBoolean();

			if (meta != null && meta.has("accessGroup")) {
				groupAccess = meta.get("accessGroup").getAsJsonArray();
				for (int i = 0; i < groupAccess.size(); i++) {
					directAccess = checkGroupAccess(groupAccess.get(i).getAsString(), svr);
					/* if direct access is granted, no need to check further f.r */
					if (directAccess)
						return directAccess;
				}
			}
		} catch (Exception e) {
			log4j.error("Error fetching directAccess value from Plugin", e);
		}
		return directAccess;
	}

	private boolean checkGroupAccess(String groupAccess, SvReader svr) throws SvException {

		boolean directAccess = false;
		try {
			DbDataObject defaultUserGroup = svr.getDefaultUserGroup();
			/* if default group is in the jsonarray grant direct access f.r */
			if (groupAccess != null && groupAccess.contains(defaultUserGroup.getVal("GROUP_NAME").toString()))
				directAccess = true;

		} catch (Exception e) {
			log4j.error("Error while matching userGroup and directAccess value from Plugin", e);
		}
		return directAccess;
	}

	@Path("/getMenu/{sessionId}/{context}")
	@GET
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces("text/html;charset=utf-8")
	public Response getMenu(@PathParam("sessionId") String sessionId, @PathParam("context") String context,
			@Context HttpServletRequest httpRequest) {

		SvPerunManager svpm = null;
		JsonObject jsonResult = new JsonObject();
		ResponseHandler jrh = new ResponseHandler();
		try {
			svpm = new SvPerunManager(sessionId);

			jsonResult = svpm.getMenu(context);

			if (jsonResult.has("error")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.get_module_menu"),
						jsonResult.get("error").getAsString(), jsonResult);
			} else {
				jrh.create(MessageType.SUCCESS, I18n.getText("success.get_module_menu"),
						I18n.getText("success.get_module_menu"), jsonResult);
			}
		} catch (Exception e) {
			if (e instanceof SvException) {
				SvException ex = (SvException) e;
				jrh.create(MessageType.ERROR, I18n.getText("error.get_module_menu"), I18n.getText(ex.getLabelCode()),
						new JsonObject());
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
			} else {
				log4j.error(e.getMessage(), e);
				jrh.create(MessageType.ERROR, I18n.getText(".error.get_module_menu"), e.getMessage(), new JsonObject());
				return Response.status(500).entity(jrh.getAll().toString()).build();
			}
		} finally {
			if (svpm != null) {
				svpm.release();
				svpm.close();
			}
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * 
	 * @param sessionId        - valid session id for authorization and
	 *                         authentication in SVAROG
	 * @param ppiKey           - the context attribute in the PerunPluginInfo.java -
	 *                         on bundle level
	 * @param additionalParams - all params related for building the context menu -
	 *                         the context items from ContextMenu.json is sent by
	 *                         key "context" in order to be recognized by the
	 *                         algorithm
	 * @param httpRequest
	 * @return
	 */
	@Path("/getContextMenu/{sessionId}/{ppiKey}")
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces("application/json")
	public Response getContextMenu(@PathParam("sessionId") String sessionId, @PathParam("ppiKey") String ppiKey,
			MultivaluedMap<String, String> additionalParams, @Context HttpServletRequest httpRequest) {
		SvPerunManager svpm = null;
		JsonObject jsonResult = new JsonObject();
		ResponseHandler jrh = new ResponseHandler();
		HashMap<String, String> contextMap = new HashMap<String, String>();
		if (ppiKey != null && additionalParams != null) {
			try (SvReader svr = new SvReader(sessionId)) {
				svpm = new SvPerunManager(sessionId);
				for (Map.Entry<String, List<String>> e : additionalParams.entrySet()) {
					if (e.getValue() != null && e.getValue().size() >= 1)
						contextMap.put(e.getKey(), e.getValue().get(0));
				}
				jsonResult = svpm.getContextMenu(ppiKey, contextMap);
				if (jsonResult.has("error")) {
					jrh.create(MessageType.ERROR, I18n.getText("error.get_context_menu"),
							jsonResult.get("error").getAsString(), jsonResult);
				} else {
					jrh.create(MessageType.SUCCESS, I18n.getText("success.get_context_menu"),
							I18n.getText("success.get_context_menu"), jsonResult);
				}
			} catch (Exception e) {
				if (e instanceof SvException) {
					SvException ex = (SvException) e;
					jrh.create(MessageType.ERROR, I18n.getText("error.get_context_menu"),
							I18n.getText(ex.getLabelCode()), new JsonObject());
					if (ex.getLabelCode().equals("error.invalid_session")) {
						jrh.create(MessageType.ERROR, I18n.getText(ex.getLabelCode()), I18n.getText(ex.getLabelCode()),
								new JsonObject());
						log4j.error(ex.getFormattedMessage());
						return Response.status(401).entity(jrh.getAll().toString()).build();
					} else {
						log4j.error(ex.getLabelCode(), ex);
						if (ex.getLabelCode().startsWith("sys")) {
							return Response.status(500).entity(jrh.getAll()).build();
						}
					}
				} else {
					log4j.error(e.getMessage(), e);
					jrh.create(MessageType.ERROR, I18n.getText("error.get_context_menu"), e.getMessage(),
							new JsonObject());
					return Response.status(500).entity(jrh.getAll().toString()).build();
				}
			}
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	@Path("/saveFormWizard/{sessionId}")
	@POST
	@Produces("application/json")
	public Response saveFormWizard(@PathParam("sessionId") String session, MultivaluedMap<String, String> formVals,
			@Context HttpServletRequest httpRequest) throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		String multistep_key = "";
		String form_name = "";
		Integer exec_order = 0;
		SvWriter svw = null;
		SvReader svr = null;
		DbDataObject formWizard = null;
		try {
			if (formVals != null) {
				for (Entry<String, List<String>> entry : formVals.entrySet()) {
					if (entry.getKey() != null && !entry.getKey().isEmpty()) {
						String key = entry.getKey();
						JsonObject jobj = new JsonObject();
						Gson gs = new Gson();
						jobj = gs.fromJson(key, JsonObject.class);
						if (jobj.get("MULTISTEP_KEY") != null)
							multistep_key = jobj.get("MULTISTEP_KEY").getAsString();
						if (jobj.get("FORM_NAME") != null)
							form_name = jobj.get("FORM_NAME").getAsString();
						if (jobj.get("EXEC_ORDER") != null)
							exec_order = jobj.get("EXEC_ORDER").getAsInt();
					}
				}

				svr = new SvReader(session);
				svw = new SvWriter(session);

				formWizard = new DbDataObject();
				formWizard.setVal("MULTISTEP_KEY", multistep_key);
				formWizard.setVal("FORM_NAME", form_name);
				formWizard.setObjectType(SvCore.getTypeIdByName("FORM_WIZARD"));
				formWizard.setVal("EXEC_ORDER", exec_order);
				formWizard.setStatus("VALID");
				svw.saveObject(formWizard, false);
				svw.dbCommit();

				jrh.create(MessageType.SUCCESS, I18n.getText("wizard.success.configuredFormWizard"),
						I18n.getText("batch.success.configuredFormWizard"), new JsonObject());
			}
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(401).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText("wizard.error.configuredFormWizard"),
					I18n.getText("wizard.error.configuredFormWizard"), new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		} finally {
			if (svr != null) {
				svr.release();
				svr.close();
			}
			if (svw != null) {
				svw.release();
				svw.close();
			}
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * @param bundleObj - object id of bundle/plugin
	 * 
	 *                  updates bundle/plugin GUI_METADATA
	 */
	@Path("/saveTypeAccess/{sessionId}/{bundleObj}")
	@POST
	@Produces("application/json")
	public Response saveTypeAccess(@PathParam("sessionId") String session, @PathParam("bundleObj") Long bundleObj,
			MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		SvWriter svw = null;
		SvReader svr = null;
		DbDataObject plugin = null;
		String guiMeta = "";
		try {
			svr = new SvReader(session);
			if (bundleObj != null)
				plugin = svr.getObjectById(bundleObj, SvCore.getTypeIdByName("SVAROG_PERUN_PLUGIN"), null);
			if (formVals != null && plugin != null) {
				for (Entry<String, List<String>> entry : formVals.entrySet()) {
					List<String> value = entry.getValue();
					guiMeta = value.get(0);
					plugin.setVal("GUI_METADATA", guiMeta);
				}
			}
			svw = new SvWriter(session);
			svw.saveObject(plugin, false);
			svw.dbCommit();
			jrh.create(MessageType.SUCCESS, I18n.getText("access.success.assigned"),
					I18n.getText("access.success.assignedToPlugin"), new JsonObject());

		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(401).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		} finally {
			if (svr != null) {
				svr.release();
				svr.close();
			}
			if (svw != null) {
				svw.release();
				svw.close();
			}
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	@Path("/saveFormConf/{sessionId}")
	@POST
	@Produces("application/json")
	public Response saveFormConf(@PathParam("sessionId") String session, MultivaluedMap<String, String> formVals,
			@Context HttpServletRequest httpRequest) throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		String card_id = "";
		String title = "";
		String descr = "";
		String imgPath = "default.png";
		Long user_group_id = -6l;
		SvWriter svw = null;
		SvReader svr = null;
		DbDataObject saveFormConf = null;
		try {
			if (formVals != null) {
				for (Entry<String, List<String>> entry : formVals.entrySet()) {
					if (entry.getKey() != null && !entry.getKey().isEmpty()) {
						String key = entry.getKey();
						JsonObject jobj = new JsonObject();
						Gson gs = new Gson();
						jobj = gs.fromJson(key, JsonObject.class);
						if (jobj.get("CARD_ID") != null)
							card_id = jobj.get("CARD_ID").getAsString();
						if (jobj.get("TITLE") != null)
							title = jobj.get("TITLE").getAsString();
						if (jobj.get("DESCR") != null)
							descr = jobj.get("DESCR").getAsString();
						if (jobj.get("IMG_PATH") != null)
							imgPath = jobj.get("IMG_PATH").getAsString();
						if (jobj.get("USER_GROUP_ID") != null)
							user_group_id = jobj.get("USER_GROUP_ID").getAsLong();
					}
				}

				svr = new SvReader(session);
				svw = new SvWriter(session);

				saveFormConf = new DbDataObject();
				saveFormConf.setVal("CARD_ID", card_id);
				saveFormConf.setVal("TITLE", title);
				saveFormConf.setObjectType(SvCore.getTypeIdByName("CARD_CONF"));
				saveFormConf.setVal("DESCR", descr);
				saveFormConf.setStatus("VALID");
				saveFormConf.setVal("IMG_PATH", imgPath);
				saveFormConf.setVal("USER_GROUP_ID", user_group_id);
				svw.saveObject(saveFormConf, false);
				svw.dbCommit();

				jrh.create(MessageType.SUCCESS, I18n.getText("conf.success.configuredFormCard"),
						I18n.getText("conf.success.configuredFormCard"), new JsonObject());
			}
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(401).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		} finally {
			if (svr != null) {
				svr.release();
				svr.close();
			}
			if (svw != null) {
				svw.release();
				svw.close();
			}
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	@Path("/deleteCard/{sessionId}/{objectId}/{objectType}/{objectPkId}/{card_id}")
	@POST
	@Produces("application/json")
	public Response deleteCard(@PathParam("sessionId") String sessionId, @PathParam("card_id") String card_id,
			@PathParam("objectId") Long objectId, @PathParam("objectType") Long objectType,
			@PathParam("objectPkId") Long objectPkId, @Context HttpServletRequest httpRequest) {
		String returnString = "";
		SvWriter svw = null;
		DbDataObject vobject = new DbDataObject();
		try {
			if (!card_id.equals("user_manager")) {
				vobject.setObjectId(objectId);
				vobject.setObjectType(objectType);
				vobject.setPkid(objectPkId);
				svw = new SvWriter(sessionId);
				svw.deleteObject(vobject);
				returnString = "true";
			} else {
				return Response.status(200).entity("error.cannot_erase_system_card").build();
			}
		} catch (SvException e) {
			e.printStackTrace();
			// return
			// Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			if (svw != null) {
				svw.release();
				svw.close();
			}
		}
		return Response.status(200).entity(returnString).build();
	}

	@Path("/checkUserActionPermission/{sessionId}/{actionName}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response checkUserActionPermission(@PathParam("sessionId") String sessionId,
			@PathParam("actionName") String actionName, @Context HttpServletRequest httpRequest) throws SvException {
		String result = Boolean.TRUE.toString();
		try (SvReader svr = new SvReader(sessionId);) {
			DbReader dbr = new DbReader();
			dbr.checkIfCurrentUserHasActionPermission(actionName, svr);
		} catch (SvException e) {
			log4j.debug(e.getMessage(), e);
			result = Boolean.FALSE.toString();
		}
		return Response.status(200).entity(result).build();
	}
}
