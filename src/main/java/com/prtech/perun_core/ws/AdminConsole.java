package com.prtech.perun_core.ws;

import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvConf.SvDbType;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvExecManager;
import com.prtech.svarog.SvLink;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbQueryExpression;
import com.prtech.svarog_common.DbQueryObject;
import com.prtech.svarog_common.DbSearch;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;
import com.prtech.svarog_common.ResponseHandler;
import com.prtech.svarog_common.DbQueryObject.DbJoinType;
import com.prtech.svarog_common.DbQueryObject.LinkType;
import com.prtech.svarog_common.ResponseHandler.MessageType;

@Path("/WsAdminConsole")
public class AdminConsole {
	static final Logger log4j = SvConf.getLogger(WsConf.class);

	/**
	 * default user group f.r
	 * 
	 */
	@Path("/addDefaultUserGroup/{session_id}/{objIdUser}/{objIdGroup}/{defaultGroup}")
	@GET
	@Produces("application/json")
	public Response addDefaultUserGroup(@PathParam("session_id") String session, @PathParam("objIdUser") Long objIdUser,
			@PathParam("objIdGroup") Long objIdGroup, @PathParam("defaultGroup") String defaultGroup,
			@Context HttpServletRequest httpRequest) throws SvException {
		ResponseHandler jrh = new ResponseHandler();

		SvReader svr = null;
		SvSecurity svs = null;
		DbDataObject dbG = null;
		DbDataObject dbO = null;

		try {
			svr = new SvReader(session);
			svs = new SvSecurity(svr);
			dbG = new DbDataObject();
			if (objIdUser != null && objIdGroup != null && defaultGroup != null && defaultGroup.equals("true")) {
				dbG = svr.getObjectById(objIdGroup, SvReader.getTypeIdByName("SVAROG_USER_GROUPS"), null);
				dbO = svr.getObjectById(objIdUser, SvReader.getTypeIdByName("SVAROG_USERS"), null);
				if (dbG != null && dbO != null) {
					svs.addUserToGroup(dbO, dbG, true);
				} else {
					jrh.create(MessageType.SUCCESS, I18n.getText("updateDefaultGroup.error.missngInfo"),
							I18n.getText("updateDefaultGroup.error.missingGroupOrUser"), new JsonObject());
				}
				jrh.create(MessageType.SUCCESS, I18n.getText("updateDefaultGroup.success.updateGroup"),
						I18n.getText("updateDefaultGroup.success.updateGroup" + "_" + dbG.getVal("GROUP_NAME")),
						new JsonObject());
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
			if (svs != null) {
				svs.release();
				svs.close();
			}
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * save user group f.r
	 * 
	 */
	@Path("/saveUserGroup/{session_id}")
	@POST
	@Produces("application/json")
	public Response saveUserGroup(@PathParam("session_id") String session, MultivaluedMap<String, String> formVals,
			@Context HttpServletRequest httpRequest) throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(session);
				SvWriter svw = new SvWriter(session);
				SvSecurity svs = new SvSecurity(svr);) {
			DbDataObject dbG = new DbDataObject();
			if (formVals != null) {
				for (Entry<String, List<String>> entry : formVals.entrySet()) {
					if (entry.getKey() != null && !entry.getKey().isEmpty()) {
						String key = entry.getKey();
						JsonObject jobj = new JsonObject();
						Gson gs = new Gson();
						jobj = gs.fromJson(key, JsonObject.class);

						if (jobj.get("GROUP_NAME") != null)
							dbG.setVal("GROUP_NAME", jobj.get("GROUP_NAME").getAsString());
						if (jobj.get("GROUP_TYPE") != null)
							dbG.setVal("GROUP_TYPE", jobj.get("GROUP_TYPE").getAsString());
						if (jobj.get("GROUP_SECURITY_TYPE") != null)
							dbG.setVal("GROUP_SECURITY_TYPE", jobj.get("GROUP_SECURITY_TYPE").getAsString());
						if (jobj.get("E_MAIL") != null)
							dbG.setVal("E_MAIL", jobj.get("E_MAIL").getAsString());

						dbG.setObjectType(SvReader.getTypeIdByName("SVAROG_USER_GROUPS"));
						dbG.setVal("GROUP_UID", SvUtil.getUUID());
						dbG.setStatus("VALID");
						if (jobj.get("OBJECT_ID") != null && jobj.get("OBJECT_ID").getAsString() != "0") {
							dbG.setObjectId(jobj.get("OBJECT_ID").getAsLong());
							dbG.setPkid(jobj.get("PKID").getAsLong());
						}
						svw.saveObject(dbG, false);
						svw.dbCommit();

						if (jobj.get("groupAccess") != null && !jobj.get("groupAccess").getAsString().isEmpty()) {
							String groupAccess = "%." + jobj.get("groupAccess").getAsString();
							DbDataArray permissions = svs.getPermissions(groupAccess);
							for (DbDataObject perm : permissions.getItems()) {
								svs.grantPermission(dbG, (String) perm.getVal("LABEL_CODE"));
							}
						}
					}
				}

				jrh.create(MessageType.SUCCESS, I18n.getText("saveUser.success.saveUser"),
						I18n.getText("saveUser.success.saveUser"), new JsonObject());
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
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * save internal user f.r
	 * 
	 */
	@Path("/saveUser/{session_id}")
	@POST
	@Produces("application/json")
	public Response saveUser(@PathParam("session_id") String session, MultivaluedMap<String, String> formVals,
			@Context HttpServletRequest httpRequest) throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		String password = null;
		DbReader dbr = null;

		try (SvReader svr = new SvReader(session);
				SvWriter svw = new SvWriter(svr);
				SvSecurity svs = new SvSecurity(svr);
				SvLink svl = new SvLink(svr);) {
			dbr = new DbReader();

			if (formVals != null) {
				for (Entry<String, List<String>> entry : formVals.entrySet()) {
					if (entry.getKey() != null && !entry.getKey().isEmpty()) {
						String key = entry.getKey();
						JsonObject jobj = new JsonObject();
						Gson gs = new Gson();
						jobj = gs.fromJson(key, JsonObject.class);
						if (jobj.get("confUserPassword") != null && jobj.get("userPassword") != null
								&& jobj.get("userPassword").equals(jobj.get("confUserPassword"))) {
							password = jobj.get("userPassword").getAsString().toUpperCase();

							svs.createUser(jobj.get("userName").getAsString().toUpperCase(), password,
									jobj.get("firstName").getAsString(), jobj.get("lastName").getAsString(),
									jobj.get("userEmail").getAsString(), jobj.get("pin").getAsString(), "", "INTERNAL",
									null, false);
							if (jobj.has("linkToUsers")) {
								DbDataObject user = dbr.searchDbObjectBySingleFilter(svCONST.OBJECT_TYPE_USER,
										"USER_NAME", jobj.get("userName").getAsString().toUpperCase(), svr);
								DbDataObject users = dbr.searchDbObjectBySingleFilter(DbCompareOperand.LIKE,
										svCONST.OBJECT_TYPE_GROUP, "GROUP_NAME", "USERS", svr);
								if (!Objects.isNull(user) && !Objects.isNull(users)) {
									DbDataObject link = DbReader.findLink(user.getObjectId(), users.getObjectId(),
											"USER_GROUP", svr);
									if (Objects.isNull(link)) {
										svl.linkObjects(user, users, "USER_GROUP", "", true);
									}
								}
							}
						} else {
							jrh.create(MessageType.ERROR, I18n.getText("saveUser.error.passwordNotMatch"),
									I18n.getText("saveUser.error.passwordNotMatch"), new JsonObject());
						}
					}
				}

				jrh.create(MessageType.SUCCESS, I18n.getText("saveUser.success.saveUser"),
						I18n.getText("saveUser.success.saveUser"), new JsonObject());
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
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * assignee user to group f.r
	 * 
	 */
	@Path("/updateUserGroup/{session_id}/{user_obj}/{group_obj}/{updateType}")
	@GET
	@Produces("application/json")
	public Response updateUserGroup(@PathParam("session_id") String session, @PathParam("user_obj") Long user_obj,
			@PathParam("group_obj") Long group_obj, @PathParam("updateType") String updateType,
			@Context HttpServletRequest httpRequest) throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(session);
				SvWriter svw = new SvWriter(session);
				SvSecurity svs = new SvSecurity(svr);){

			DbDataArray dboUserGroup = new DbDataArray();
			DbDataObject dboDefaultUserGroup = new DbDataObject();


			DbDataObject dboUser = svr.getObjectById(user_obj, SvReader.getTypeIdByName("SVAROG_USERS"), null);

			if (dboUser != null) {
				dboUserGroup = svr.getAllUserGroups(dboUser, false);
				dboDefaultUserGroup = svr.getDefaultUserGroup();
			} else {
				jrh.create(MessageType.ERROR, I18n.getText("console.errpr.noUserFound"),
						I18n.getText("console.errpr.noUserFound"), new JsonObject());
			}

			DbDataObject dboGroup = svr.getObjectById(group_obj, SvReader.getTypeIdByName("SVAROG_USER_GROUPS"), null);

			if (updateType.equals("remove") && dboUser != null && dboGroup != null) {
				svs.removeUserFromGroup(dboUser, dboGroup);
				if (dboDefaultUserGroup != null && dboDefaultUserGroup.getObjectId().equals(group_obj)) {
					jrh.create(MessageType.SUCCESS, I18n.getText("console.success.removedDefaultGroup"),
							I18n.getText("console.success.msg.removedDefaultGroup"), new JsonObject());
					return Response.status(200).entity(jrh.getAll().toString()).build();
				}
			}

			if (updateType.equals("add") && dboUser != null && dboGroup != null) {
				if (dboUserGroup != null && !dboUserGroup.isEmpty()) {
					svs.addUserToGroup(dboUser, dboGroup, false);
				} else {
					svs.addUserToGroup(dboUser, dboGroup, true);
				}
			}

			jrh.create(MessageType.SUCCESS, I18n.getText("console.success.actionCompleted"),
					I18n.getText("console.success.actionCompleted"), new JsonObject());

		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * updates status of internal user
	 * 
	 */
	@Path("/changeUserStatus/{session_id}/{object_id}/{newstatus}")
	@GET
	@Produces("application/json")
	public Response changeUserStatus(@PathParam("session_id") String session, @PathParam("object_id") Long object_id,
			@PathParam("newstatus") String newstatus, @Context HttpServletRequest httpRequest) throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(session); SvWriter svw = new SvWriter(svr);) {
			DbDataObject dboUser = svr.getObjectById(object_id, SvReader.getTypeIdByName("SVAROG_USERS"), null);
			String oldStatus = dboUser.getStatus();

			if (!dboUser.getVal("USER_TYPE").equals("EXTERNAL")) {
				if (!newstatus.equals(oldStatus)) {
					dboUser.setStatus(newstatus);
					svw.saveObject(dboUser, false);
					svw.dbCommit();
				}
				jrh.create(MessageType.SUCCESS, I18n.getText("console.success.actionCompleted"),
						I18n.getText("console.success.actionCompleted"), new JsonObject());
			} else {
				jrh.create(MessageType.ERROR, I18n.getText("console.error.cannotUpdateExtUser"),
						I18n.getText("console.success.cannotUpdateExtUser"), new JsonObject());
			}

		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		} 
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * return linked object for the assigned group
	 * 
	 */
	@Path("/getLinkedUsers/{session_id}/{object_id}")
	@GET
	@Produces("application/json")
	public Response getLinkedUsers(@PathParam("session_id") String session, @PathParam("object_id") Long objectId,
			@Context HttpServletRequest httpRequest) throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(session); SvLink svlink = new SvLink(svr)) {
			DbReader rdr = new DbReader();
			JsonArray jsonArray = new JsonArray();
			DbDataObject defaultGroup = SvCore.getLinkType("USER_DEFAULT_GROUP", SvCore.getTypeIdByName("SVAROG_USERS"),
					SvCore.getTypeIdByName("SVAROG_USER_GROUPS"));

			DbDataObject additionalGroup = SvCore.getLinkType("USER_GROUP", SvCore.getTypeIdByName("SVAROG_USERS"),
					SvCore.getTypeIdByName("SVAROG_USER_GROUPS"));

			DbDataArray dbaDefaultGroupUsers = svr.getObjectsByLinkedId(objectId,
					SvCore.getTypeIdByName("SVAROG_USER_GROUPS"), defaultGroup, SvCore.getTypeIdByName("SVAROG_USERS"),
					true, null, null, null);

			DbDataArray dbaAdditionalGroupUsers = svr.getObjectsByLinkedId(objectId,
					SvCore.getTypeIdByName("SVAROG_USER_GROUPS"), additionalGroup,
					SvCore.getTypeIdByName("SVAROG_USERS"), true, null, null, null);

			if ((dbaDefaultGroupUsers != null && !dbaDefaultGroupUsers.isEmpty())
					|| (dbaAdditionalGroupUsers != null && !dbaAdditionalGroupUsers.isEmpty())) {
				if (dbaDefaultGroupUsers != null && !dbaDefaultGroupUsers.isEmpty()) {
					for (DbDataObject dboDefaultGroupUser : dbaDefaultGroupUsers.getItems()) {
						JsonObject jsonObj = rdr.getDboUserDetailsAsJsonObject(dboDefaultGroupUser);
						jsonArray.add(jsonObj);
					}
				}
				if (dbaAdditionalGroupUsers != null && !dbaAdditionalGroupUsers.isEmpty()) {
					for (DbDataObject dboAdditionalGroupUser : dbaAdditionalGroupUsers.getItems()) {
						JsonObject jsonObj = rdr.getDboUserDetailsAsJsonObject(dboAdditionalGroupUser);
						jsonArray.add(jsonObj);
					}
				}
				jrh.create(MessageType.SUCCESS, I18n.getText("console.success.defaultUsers"),
						I18n.getText("console.success.defaultUsers"), jsonArray);
			} else {
				jrh.create(MessageType.WARNING, I18n.getText("console.warning.usersNotFound"),
						I18n.getText("console.warning.usersNotFound"), new JsonObject());
			}
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	@Path("/Users/ByUserGroup/sid/{sid}/groupName/{groupName}")
	@GET
	@Produces("application/json")
	public Response getUsersByUserGroup(@PathParam("sid") String session, @PathParam("groupName") String groupName,
			@Context HttpServletRequest httpRequest) throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(session); SvLink svlink = new SvLink(svr)) {
			String[] tablesUsedArray = new String[1];
			Boolean[] tableShowArray = new Boolean[1];
			int tablesusedCount = 1;

			DbDataObject dboUserGroupDesc = SvCore.getDbt(svCONST.OBJECT_TYPE_GROUP);
			DbDataObject dboUserGroup = svr.getObjectByUnqConfId(groupName, dboUserGroupDesc);

			if (dboUserGroup == null) {
				throw new SvException("error.user_group_not_found", svr.getInstanceUser());
			}
			DbDataArray dbaResultUsers = new DbDataArray();
			DbDataObject defaultGroup = SvCore.getLinkType("USER_DEFAULT_GROUP", SvCore.getTypeIdByName("SVAROG_USERS"),
					SvCore.getTypeIdByName("SVAROG_USER_GROUPS"));

			DbDataObject additionalGroup = SvCore.getLinkType("USER_GROUP", SvCore.getTypeIdByName("SVAROG_USERS"),
					SvCore.getTypeIdByName("SVAROG_USER_GROUPS"));

			DbDataArray dbaDefaultGroupUsers = svr.getObjectsByLinkedId(dboUserGroup.getObjectId(),
					SvCore.getTypeIdByName("SVAROG_USER_GROUPS"), defaultGroup, SvCore.getTypeIdByName("SVAROG_USERS"),
					true, null, null, null);

			DbDataArray dbaAdditionalGroupUsers = svr.getObjectsByLinkedId(dboUserGroup.getObjectId(),
					SvCore.getTypeIdByName("SVAROG_USER_GROUPS"), additionalGroup,
					SvCore.getTypeIdByName("SVAROG_USERS"), true, null, null, null);

			if ((dbaDefaultGroupUsers != null && !dbaDefaultGroupUsers.isEmpty())
					|| (dbaAdditionalGroupUsers != null && !dbaAdditionalGroupUsers.isEmpty())) {
				if (dbaDefaultGroupUsers != null && !dbaDefaultGroupUsers.isEmpty()) {
					for (DbDataObject dboDefaultGroupUser : dbaDefaultGroupUsers.getItems()) {
						dbaResultUsers.addDataItem(dboDefaultGroupUser);
					}
				}
				if (dbaAdditionalGroupUsers != null && !dbaAdditionalGroupUsers.isEmpty()) {
					for (DbDataObject dboAdditionalGroupUser : dbaAdditionalGroupUsers.getItems()) {
						dbaResultUsers.addDataItem(dboAdditionalGroupUser);
					}
				}

				tablesUsedArray[0] = Rc.SVAROG_USERS;
				tableShowArray[0] = true;

				JsonArray jsonArray = WsReactElements.prapareTableQueryData(dbaResultUsers, tablesUsedArray,
						tableShowArray, tablesusedCount, true, svr, false, null);
				jrh.create(MessageType.SUCCESS, I18n.getText("console.success.defaultUsers"),
						I18n.getText("console.success.defaultUsers"), jsonArray);
			} else {
				jrh.create(MessageType.WARNING, I18n.getText("console.warning.usersNotFound"),
						I18n.getText("console.warning.usersNotFound"), new JsonObject());
			}
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * Get user groups by the session id of the user logged in
	 * 
	 * @param sid - session id
	 * @return application/json
	 */
	@Path("/Groups/BySid/{sid}")
	@GET
	@Produces("application/json")
	public Response getGroupsBySid(@PathParam("sid") String sid, @Context HttpServletRequest httpRequest)
			throws SvException {
		JsonObject jsonObj;
		String groupName;
		String objectId;
		DbDataArray dboUserGroup = null;
		JsonArray jsonArray = new JsonArray();
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(sid); SvWriter svw = new SvWriter(svr)) {
			DbDataObject dboUser = SvCore.getUserBySession(sid);
			if (dboUser != null)
				dboUserGroup = svr.getAllUserGroups(dboUser, false);
			if (dboUserGroup != null && !dboUserGroup.isEmpty()) {
				for (DbDataObject dboG : dboUserGroup.getItems()) {
					jsonObj = new JsonObject();
					groupName = dboG.getVal("GROUP_NAME").toString();
					objectId = dboG.getObjectId().toString();
					jsonObj.addProperty("groupName", groupName);
					jsonObj.addProperty("objectId", objectId);
					jsonArray.add(jsonObj);
				}
				jrh.create(MessageType.SUCCESS, I18n.getText("console.success.defaultUsers"),
						I18n.getText("console.success.defaultUsers"), jsonArray);
			} else {
				jrh.create(MessageType.WARNING, I18n.getText("console.warning.userGroupNotFound"),
						I18n.getText("console.warning.userGroupNotFound"), new JsonObject());
			}
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * return linked object for the assigned group
	 * 
	 */
	@Path("/getLinkedGroups/{session_id}/{object_id}")
	@GET
	@Produces("application/json")
	public Response getLinkedGroups(@PathParam("session_id") String session, @PathParam("object_id") Long object_id,
			@Context HttpServletRequest httpRequest) throws SvException {
		JsonObject jsonObj = null;
		JsonArray jsonArray = null;
		DbDataObject dboUser = null;
		DbDataArray dboUserGroup = null;
		boolean defaultGroup = false;
		DbDataObject dboUserDefaultGroup = null;

		ResponseHandler jrh = new ResponseHandler();

		try (SvReader svr = new SvReader(session); SvWriter svw = new SvWriter(session)) {
			jsonArray = new JsonArray();
			dboUserDefaultGroup = new DbDataObject();

			dboUser = svr.getObjectById(object_id, SvReader.getTypeIdByName("SVAROG_USERS"), null);

			if (dboUser != null) {
				dboUserGroup = svr.getAllUserGroups(dboUser, false);
				dboUserDefaultGroup = svr.getDefaultUserGroup();
			}

			if (dboUserDefaultGroup != null) {
				defaultGroup = true;
			}

			if (dboUserGroup != null && !dboUserGroup.isEmpty()) {
				for (DbDataObject dboG : dboUserGroup.getItems()) {
					jsonObj = new JsonObject();
					String groupName = dboG.getVal("GROUP_NAME").toString();
					String objectId = dboG.getObjectId().toString();
					jsonObj.addProperty("groupName", groupName);
					jsonObj.addProperty("objectId", objectId);
					jsonObj.addProperty("defaultGroup", defaultGroup);
					jsonArray.add(jsonObj);
				}

				jrh.create(MessageType.SUCCESS, I18n.getText("console.success.defaultUsers"),
						I18n.getText("console.success.defaultUsers"), jsonArray);
			} else {
				jrh.create(MessageType.WARNING, I18n.getText("console.warning.userGroupNotFound"),
						I18n.getText("console.warning.userGroupNotFound"), new JsonObject());
			}
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * return all user groups
	 * 
	 */
	@Path("/getAllGroups/{session_id}")
	@GET
	@Produces("application/json")
	public Response getAllGroups(@PathParam("session_id") String session, @Context HttpServletRequest httpRequest)
			throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(session); SvWriter svw = new SvWriter(svr);) {
			JsonArray jsonArray = new JsonArray();
			DbDataArray dboAllUserGroup = svr.getObjects(null, SvReader.getTypeIdByName("SVAROG_USER_GROUPS"), null, 0, 0);
			if (dboAllUserGroup != null && !dboAllUserGroup.isEmpty()) {
				for (DbDataObject dboG : dboAllUserGroup.getItems()) {
					JsonObject jsonObj = new JsonObject();
					String groupName = dboG.getVal("GROUP_NAME").toString();
					String objectId = dboG.getObjectId().toString();
					jsonObj.addProperty("groupName", groupName);
					jsonObj.addProperty("objectId", objectId);
					jsonArray.add(jsonObj);
				}
				jrh.create(MessageType.SUCCESS, I18n.getText("console.success.defaultUsers"),
						I18n.getText("console.success.defaultUsers"), jsonArray);
			} else {
				jrh.create(MessageType.WARNING, I18n.getText("console.warning.userGroupNotFound"),
						I18n.getText("console.warning.userGroupNotFound"), new JsonObject());
			}
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * remove user from ORG UNIT
	 */
	@Path("/get/removeUserFromOU/sid/{sid}/objectIdOU/{objectIdOU}/objecidUser/{objecidUser}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response removeUserFromOU(@PathParam("sid") String sid, @PathParam("objectIdOU") Long objectIdOU,
			@PathParam("objecidUser") Long objecidUser, @Context HttpServletRequest httpRequest) {
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(sid); SvSecurity svs = new SvSecurity(svr); SvWriter swr = new SvWriter(svr)) {
			DbDataObject dblOb = SvCore.getLinkType("POA", svCONST.OBJECT_TYPE_USER, svCONST.OBJECT_TYPE_ORG_UNITS);
			DbDataObject linkObj = DbReader.findLink(objecidUser, objectIdOU, dblOb.getObjectId(), svr);

			if (linkObj != null)
				swr.deleteObject(linkObj);

			jrh.create(MessageType.SUCCESS, I18n.getText("success.userWasRemovedFromOU"),
					I18n.getText("success.msg.userWasRemovedFromOU"), new JsonObject());

		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * assign user to ORG UNIT
	 */
	@Path("/get/assignUserToOU/sid/{sid}/objectIdOU/{objectIdOU}/objecidUser/{objecidUser}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response assignUserToOU(@PathParam("sid") String sid, @PathParam("objectIdOU") Long objectIdOU,
			@PathParam("objecidUser") Long objecidUser, @Context HttpServletRequest httpRequest) {
		ResponseHandler jrh = new ResponseHandler();
		SvLink svl = null;
		DbDataObject dbO = null;
		DbDataObject dbG = null;
		try (SvReader svr = new SvReader(sid); SvSecurity svs = new SvSecurity(svr)) {
			dbO = svr.getObjectById(objecidUser, SvCore.getTypeIdByName("SVAROG_USERS"), null);
			dbG = svr.getObjectById(objectIdOU, SvCore.getTypeIdByName("SVAROG_ORG_UNITS"), null);
			svl = new SvLink(svr);
			svs.empowerUser(dbO, dbG, svl);
			jrh.create(MessageType.SUCCESS, I18n.getText("success.userAssignedToOU"),
					I18n.getText("success.msg.success.userAssignedToOU"), new JsonObject());
			/* TODO check for empowered user */
//			DbDataArray empowerments = svs.getPOAObjects(dbO.getObjectId(), "SVAROG_USERS");
//			if (!empowerments.getItems().isEmpty() && empowerments.getItems().get(0) != null) {
//			
//			}
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * get users that belong to ORG UNIT
	 */
	@Path("/get/usersByOU/sid/{sid}/objectIdOU/{objectIdOU}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response usersByOU(@PathParam("sid") String sid, @PathParam("objectIdOU") Long objectIdOU,
			@Context HttpServletRequest httpRequest) {
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(sid); SvSecurity svs = new SvSecurity(svr)) {
			DbDataObject dbl = SvCore.getLinkType("POA", svCONST.OBJECT_TYPE_USER, svCONST.OBJECT_TYPE_ORG_UNITS);
			DbDataArray dboAllUsersByOU = svr.getObjectsByLinkedId(objectIdOU, svCONST.OBJECT_TYPE_ORG_UNITS, dbl,
					svCONST.OBJECT_TYPE_USER, true, null, null, null);

			String[] tablesUsedArray = new String[1];
			Boolean[] tableShowArray = new Boolean[1];
			tablesUsedArray[0] = Rc.SVAROG_USERS;
			tableShowArray[0] = true;
			int tablesusedCount = 1;

			JsonArray jsonArray = WsReactElements.prapareTableQueryData(dboAllUsersByOU, tablesUsedArray,
					tableShowArray, tablesusedCount, true, svr, false, null);

			jrh.create(MessageType.SUCCESS, I18n.getText("console.success.loadUserORGUNIT"),
					I18n.getText("console.success.loadUserORGUNIT"), jsonArray);
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		} 
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * create custom acl code f.r formVals - keys - > {aclCode}/{accessType}
	 */
	@Path("/createCustomAcl/{session_id}")
	@POST
	@Produces("application/json")
	public Response createCustomAcl(@PathParam("session_id") String session, MultivaluedMap<String, String> formVals,
			@Context HttpServletRequest httpRequest) throws SvException {
		String[] listAcl = null;
		String accessType = null;
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(session); SvWriter svw = new SvWriter(svr);) {
			svw.setAutoCommit(false);
			if (formVals != null) {
				for (Entry<String, List<String>> entry : formVals.entrySet()) {
					if (entry.getKey() != null && !entry.getKey().isEmpty()) {
						String key = entry.getKey();
						JsonObject jobj = new JsonObject();
						Gson gs = new Gson();
						jobj = gs.fromJson(key, JsonObject.class);
						if (jobj.get("accessType") != null)
							accessType = jobj.get("accessType").getAsString();

						if (jobj.get("aclCode") != null) {
							listAcl = jobj.get("aclCode").getAsString().split("\n");
						}
						if (accessType != null && listAcl.length > 0) {
							for (Integer i = 0; i < listAcl.length; i++) {
								System.out.println(listAcl[i]);
								DbDataObject dboAcl = new DbDataObject(svCONST.OBJECT_TYPE_ACL);
								dboAcl.setVal("ACCESS_TYPE", accessType);
								dboAcl.setVal("ACL_OBJECT_ID", 0L);
								dboAcl.setVal("ACL_OBJECT_TYPE", 50L);
								dboAcl.setVal("ACL_CONFIG_UNQ", listAcl[i]);
								dboAcl.setVal("LABEL_CODE", listAcl[i]);
								svw.saveObject(dboAcl, false);
								svw.dbCommit();
								jrh.create(MessageType.SUCCESS, I18n.getText("console.success.createdAclCode"),
										I18n.getText("console.success.createdAclCode"), listAcl.toString());
							}
						}
					} else {
						jrh.create(MessageType.ERROR, I18n.getText("console.error.missingAclOrAccess"),
								I18n.getText("console.success.missingAclOrAccess"));
					}
				}
			} else {
				jrh.create(MessageType.ERROR, I18n.getText("console.error.missingAclOrAccess"),
						I18n.getText("console.success.missingAclOrAccess"));
			}

		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		} 
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * manage custom acl code f.r {manage}/{aclCode}/{groupObjId}
	 */

	@Path("/manageCustomAcl/{session_id}")
	@POST
	@Produces("application/json")
	public Response manageCustomAcl(@PathParam("session_id") String session, MultivaluedMap<String, String> formVals,
			@Context HttpServletRequest httpRequest) throws SvException {
		DbDataObject group = new DbDataObject();
		ResponseHandler jrh = new ResponseHandler();
		String[] listAcl = null;
		try (SvReader svr = new SvReader(session); SvWriter svw = new SvWriter(svr); SvSecurity svc = new SvSecurity(svr);) {
			if (formVals != null) {
				for (Entry<String, List<String>> entry : formVals.entrySet()) {
					if (entry.getKey() != null && !entry.getKey().isEmpty()) {
						String key = entry.getKey();
						JsonObject jobj = new JsonObject();
						Gson gs = new Gson();
						jobj = gs.fromJson(key, JsonObject.class);

						if (jobj.get("groupObjId") != null)
							group = svr.getObjectById(jobj.get("groupObjId").getAsLong(),
									SvCore.getTypeIdByName("SVAROG_USER_GROUPS"), null);

						if (jobj.get("manage") != null && !jobj.get("manage").toString().equals("0")) {
							if (jobj.get("aclCode") != null)
								listAcl = jobj.get("aclCode").getAsString().split("\n");

							for (Integer i = 0; i < listAcl.length; i++) {

								if (jobj.get("manage").getAsString().equals("GRANT")) {
									svc.grantPermission(group, listAcl[i]);
									jrh.create(MessageType.SUCCESS, I18n.getText("console.success.grantedPerm"),
											I18n.getText("console.success.grantedPerm"));
								}

								if (jobj.get("manage").getAsString().equals("REVOKE")) {
									svc.revokePermission(group, listAcl[i]);
									jrh.create(MessageType.SUCCESS, I18n.getText("console.success.revokePerm"),
											I18n.getText("console.success.revokePerm"));
								}
							}
						} else {
							jrh.create(MessageType.ERROR, I18n.getText("console.error.missingManageAction"),
									I18n.getText("console.error.missingManageAction"));
						}
					}
				}
			} else {
				jrh.create(MessageType.ERROR, I18n.getText("console.error.missingAclOrAccess"),
						I18n.getText("console.error.missingAclOrAccess"));
			}
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		} 
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * initAllOsgiExecturos
	 * 
	 */
	@Path("/executeAll/{session_id}")
	@GET
	@Produces("application/json")
	public Response executeAll(@PathParam("session_id") String session, @Context HttpServletRequest httpRequest)
			throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(session); SvExecManager svx = new SvExecManager(svr);) {
			svx.initOSGIExecutors();
			jrh.create(MessageType.SUCCESS, I18n.getText("success.loadedExecturos"),
					I18n.getText("success.loadedExecturos"), new JsonObject());
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * updateGroup, use this method when new table is added and old user group
	 * should have access on it f.r
	 */
	@Path("/updateGroup/{session_id}/{objId}/{accessType}")
	@GET
	@Produces("application/json")
	public Response updateGroup(@PathParam("session_id") String session, @PathParam("objId") Long objId,
			@PathParam("accessType") String accessType, @Context HttpServletRequest httpRequest) throws SvException {

		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(session); SvSecurity svs = new SvSecurity(svr);) {
			DbDataObject group = svr.getObjectById(objId, SvCore.getTypeIdByName("SVAROG_USER_GROUPS"), null);
			if (group != null) {
				String groupAccess = "%." + accessType;
				DbDataArray permissions = svs.getPermissions(groupAccess);
				for (DbDataObject perm : permissions.getItems()) {
					svs.grantPermission(group, (String) perm.getVal("LABEL_CODE"));
				}
			}
			jrh.create(MessageType.SUCCESS, I18n.getText("success.updatedGroupPermissions"),
					I18n.getText("success.updatedGroupPermissions"), new JsonObject());
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	/**
	 * create code list
	 * 
	 */
	@Path("/save-code-list/sid/{session_id}/parent_id/{parent_id}")
	@POST
	@Produces("application/json")
	public Response saveCodeList(@PathParam("session_id") String session, @PathParam("parent_id") Long parent_id,
			MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(session);
				SvWriter svw = new SvWriter(svr);
				SvSecurity svs = new SvSecurity(svr);) {

			if (formVals != null) {
				for (Entry<String, List<String>> entry : formVals.entrySet()) {
					if (entry.getKey() != null && !entry.getKey().isEmpty()) {
						String key = entry.getKey();
						DbDataObject dbC = new DbDataObject();
						DbDataObject dbL = new DbDataObject();
						JsonObject jobj = new JsonObject();
						JsonObject jobCodes = new JsonObject();
						JsonObject jobLabels = new JsonObject();
						Gson gs = new Gson();
						jobj = gs.fromJson(key, JsonObject.class);
						jobCodes = jobj.getAsJsonObject("SVAROG_CODES");
						jobLabels = jobj.getAsJsonObject("SVAROG_LABELS");
						if (jobCodes.entrySet().size() > 0) {
							if (jobCodes.get("CODE_VALUE") != null)
								dbC.setVal("CODE_VALUE", jobCodes.get("CODE_VALUE").getAsString());
							if (jobCodes.get("PARENT_CODE_VALUE") != null)
								dbC.setVal("PARENT_CODE_VALUE", jobCodes.get("PARENT_CODE_VALUE").getAsString());
							if (jobCodes.get("LABEL_CODE") != null)
								dbC.setVal("LABEL_CODE", jobCodes.get("LABEL_CODE").getAsString());
							if (jobCodes.get("SORT_ORDER") != null)
								dbC.setVal("SORT_ORDER", jobCodes.get("SORT_ORDER").getAsNumber());

							dbC.setParentId(parent_id);
							if (jobCodes.get("OBJECT_ID") != null && jobCodes.get("OBJECT_ID").getAsString() != "0") {
								dbC.setObjectId(jobCodes.get("OBJECT_ID").getAsLong());
								dbC.setPkid(jobCodes.get("PKID").getAsLong());
							}
							dbC.setObjectType(SvReader.getTypeIdByName("SVAROG_CODES"));
							dbC.setStatus("VALID");
							svw.saveObject(dbC, false);

							if (jobLabels.entrySet().size() > 0) {
								if (jobLabels.get("LABEL_DESCR") != null)
									dbL.setVal("LABEL_DESCR", jobLabels.get("LABEL_DESCR").getAsString());
								if (jobLabels.get("LABEL_TEXT") != null)
									dbL.setVal("LABEL_TEXT", jobLabels.get("LABEL_TEXT").getAsString());
								if (jobLabels.get("LOCALE_ID") != null)
									dbL.setVal("LOCALE_ID", jobLabels.get("LOCALE_ID").getAsString());
								if (jobCodes.get("LABEL_CODE") != null)
									dbL.setVal("LABEL_CODE", jobCodes.get("LABEL_CODE").getAsString());

								dbL.setParentId(parent_id);
								if (jobLabels.get("OBJECT_ID") != null
										&& jobLabels.get("OBJECT_ID").getAsString() != "0") {
									dbL.setObjectId(jobLabels.get("OBJECT_ID").getAsLong());
									dbL.setPkid(jobLabels.get("PKID").getAsLong());
								}
								dbL.setObjectType(SvReader.getTypeIdByName("SVAROG_LABELS"));
								dbL.setStatus("VALID");
								svw.saveObject(dbL, false);
							} else {
								jrh.create(MessageType.ERROR, I18n.getText("error.invalid_code_label"),
										I18n.getText("error.invalid_code_label"), new JsonObject());
							}
						} else {
							jrh.create(MessageType.ERROR, I18n.getText("error.invalid_svarog_code"),
									I18n.getText("error.invalid_svarog_code"), new JsonObject());
						}
					}
				}
				svw.dbCommit();
				jrh.create(MessageType.SUCCESS, I18n.getText("saveUser.success.saveCodeElement"),
						I18n.getText("saveUser.success.saveCodeElement"), new JsonObject());
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
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}
	
	
	

	/**
	 * return all ACLs for a given group OBJECT_ID
	 * 
	 */
	@Path("/get-acl-by-group/sid/{session_id}/group_object_id/{group_id}")
	@GET
	@Produces("application/json")
	public Response getAclByGroup(@PathParam("session_id") String session, @PathParam("group_id") Long group_id, 
			@Context HttpServletRequest httpRequest)
			throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(session)) {
			JsonArray responseArray = new JsonArray();
			Gson gson = new Gson();
			int tablesusedCount = 3;
			String[] tablesUsedArray = new String[tablesusedCount];
			Boolean[] tableShowArray = new Boolean[tablesusedCount];
			tablesUsedArray[0] = ("SVAROG_SID_ACL");
			tablesUsedArray[1] = ("SVAROG_ACL");
			tablesUsedArray[2] = ("SVAROG_TABLES");
			Arrays.fill(tableShowArray, true);
			DbSearch dbSSidObjectId = new DbSearchCriterion("SID_OBJECT_ID", DbCompareOperand.EQUAL, group_id);
			DbQueryObject dbtSidAcl = new DbQueryObject(SvCore.getDbtByName("SVAROG_SID_ACL"), dbSSidObjectId,
					DbJoinType.INNER, null, LinkType.CUSTOM, null, null);
			dbtSidAcl.addCustomJoinLeft("acl_object_id");
			dbtSidAcl.addCustomJoinRight("object_id");
			DbQueryObject dbtAcl = new DbQueryObject(SvCore.getDbtByName("SVAROG_ACL"), null, DbJoinType.LEFT, null,
					LinkType.CUSTOM_FREETEXT, null, null);
			SvDbType dbType = SvConf.getDbType();
			String timestamp = dbType.equals(SvDbType.ORACLE)? "sysdate" : "current_timestamp";
			dbtAcl.setCustomFreeTextJoin(
					" on (tbl2.object_id = tbl1.acl_object_id and "+timestamp+" between tbl2.dt_insert and tbl2.dt_delete) or tbl2.object_id is null");
			DbQueryObject dbtTable = new DbQueryObject(SvCore.getDbtByName("SVAROG_TABLES"), null, DbJoinType.INNER,
					null, null, null, null);
			DbQueryExpression q = new DbQueryExpression();
			q.addItem(dbtSidAcl);
			q.addItem(dbtAcl);
			q.addItem(dbtTable);
			DbDataArray ret = svr.getObjects(q, null, null);
			String jsonStr = WsReactElements.prapareTableQueryData(ret, tablesUsedArray, tableShowArray,
					tablesusedCount, true, svr);
			JsonArray tmpResponseArray = gson.fromJson(jsonStr, JsonArray.class);
			// parse the data so we display only things that we need
			if (tmpResponseArray.size() > 0) {
				String[] stringFields = new String[4];
				String[] longFields = new String[1];
				stringFields[0] = ("SVAROG_ACL.ACCESS_TYPE");
				stringFields[1] = ("SVAROG_ACL.LABEL_CODE");
				stringFields[2] = ("SVAROG_TABLES.TABLE_NAME");
				stringFields[3] = ("SVAROG_TABLES.LABEL_CODE");
				longFields[0] = ("SVAROG_SID_ACL.ACL_OBJECT_ID");
				responseArray = filterFields(tmpResponseArray, stringFields, longFields);
			}
			jrh.create(MessageType.SUCCESS, I18n.getText("console.success.defaultUsers"),
					I18n.getText("console.success.defaultUsers"), responseArray);
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}
	
	
	private JsonArray filterFields(JsonArray responseArray, String[] stringFields, String[] longFields) {
		JsonArray tmpJsonArrayResponse = new JsonArray();
		for (int i = 0; i < responseArray.size(); i++) {
			JsonObject temp = (JsonObject) responseArray.get(i);
			JsonObject newObject = new JsonObject();
			for (int j = 0; j < stringFields.length; j++)
				if (temp.has(stringFields[j]))
					newObject.addProperty(stringFields[j], temp.get(stringFields[j]).getAsString());
			for (int j = 0; j < longFields.length; j++)
				if (temp.has(longFields[j]))
					newObject.addProperty(longFields[j], temp.get(longFields[j]).getAsLong());
			tmpJsonArrayResponse.add(newObject);
		}
		return tmpJsonArrayResponse;
		
	}

	/**
	 * return all ACLs for a given group OBJECT_ID, field list for the show grid
	 * 
	 */
	@Path("/get-acl-by-group-field-list/sid/{session_id}")
	@GET
	@Produces("application/json")
	public Response getAclByGroupFieldList(@PathParam("session_id") String session, 
			@Context HttpServletRequest httpRequest)
			throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(session)) {
			int tablesusedCount = 3;
			String[] tablesUsedArray = new String[tablesusedCount];
			Boolean[] svarogShowArray = new Boolean[tablesusedCount];
			Boolean[] tableShowArray = new Boolean[tablesusedCount];
			tablesUsedArray[0] = ("SVAROG_SID_ACL");
			tablesUsedArray[1] = ("SVAROG_ACL");
			tablesUsedArray[2] = ("SVAROG_TABLES");
			Arrays.fill(tableShowArray, Boolean.TRUE);
			Arrays.fill(svarogShowArray, Boolean.FALSE);
			svarogShowArray[0] = Boolean.TRUE;
			WsReactElements wsReact = new WsReactElements();
			JsonArray jsonArrayResponse = wsReact.prepareJsonArrayFromGrid(tablesUsedArray, svarogShowArray,
					tableShowArray, svr);
			// remove columns that we don't need in the view so we save on size
			// of response
			if (jsonArrayResponse.size() > 0) {
				JsonArray tmpJsonArrayResponse = new JsonArray();
				for (int i = 0; i < jsonArrayResponse.size(); i++)
					if (jsonArrayResponse.get(i) != null && !(jsonArrayResponse.get(i)).isJsonNull()) {
						JsonObject temp = (JsonObject) jsonArrayResponse.get(i);
						if (temp.has("key")) {
							String tmpS = temp.get("key").getAsString().toUpperCase();
							if ("SVAROG_SID_ACL.ACL_OBJECT_ID".equals(tmpS) || "SVAROG_ACL.ACCESS_TYPE".equals(tmpS)
									|| "SVAROG_ACL.LABEL_CODE".equals(tmpS) || "SVAROG_TABLES.TABLE_NAME".equals(tmpS)
									|| "SVAROG_TABLES.LABEL_CODE".equals(tmpS) ) {
								temp.addProperty("visible", true);
								if (temp.has("width"))
									temp.remove("width");
								tmpJsonArrayResponse.add(temp);
							}
						}
					}
				jsonArrayResponse = tmpJsonArrayResponse;
			}
			jrh.create(MessageType.SUCCESS, I18n.getText("console.success.acl.sid.field.list"),
					I18n.getText("console.success.acl.sid.field.list"), jsonArrayResponse);
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}
	
	
	


	/**
	 * return all ACLs for a given user OBJECT_ID
	 * 
	 */
	@Path("/get-acl-by-user/sid/{session_id}/user_object_id/{object_id}")
	@GET
	@Produces("application/json")
	public Response getAclByUser(@PathParam("session_id") String session, @PathParam("object_id") Long object_id, 
			@Context HttpServletRequest httpRequest)
			throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(session)) {
			JsonArray responseArray = new JsonArray();
			Gson gson = new Gson();
			int tablesusedCount = 6;
			String[] tablesUsedArray = new String[tablesusedCount];
			Boolean[] tableShowArray = new Boolean[tablesusedCount];
			
			tablesUsedArray[0] = ("SVAROG_LINK");
			tablesUsedArray[1] = ("SVAROG_LINK_TYPE");
			tablesUsedArray[2] = ("SVAROG_USER_GROUPS");
			tablesUsedArray[3] = ("SVAROG_SID_ACL");
			tablesUsedArray[4] = ("SVAROG_ACL");
			tablesUsedArray[5] = ("SVAROG_TABLES");
			Arrays.fill(tableShowArray, true);
			
			DbSearch dbUserObjectId = new DbSearchCriterion("link_obj_id_1", DbCompareOperand.EQUAL, object_id);
			
			DbQueryObject dbtLink = new DbQueryObject(SvCore.getDbtByName("SVAROG_LINK"), dbUserObjectId,
					DbJoinType.INNER, null, LinkType.CUSTOM, null, null);
			dbtLink.addCustomJoinLeft("link_type_id");
			dbtLink.addCustomJoinRight("object_id");
			
			
			DbQueryObject dbtLinkType = new DbQueryObject(SvCore.getDbtByName("SVAROG_LINK_TYPE"), null,
					DbJoinType.INNER, null, LinkType.CUSTOM_FREETEXT, null, null);
			dbtLinkType.setCustomFreeTextJoin(
					" on tbl0.link_obj_id_2 = tbl2.object_id ");
			
			DbQueryObject dbtUserGroups = new DbQueryObject(SvCore.getDbtByName("SVAROG_USER_GROUPS"), null,
					DbJoinType.INNER, null, LinkType.CUSTOM, null, null);
			dbtUserGroups.addCustomJoinLeft("object_id");
			dbtUserGroups.addCustomJoinRight("sid_object_id");

			DbQueryObject dbtSidAcl = new DbQueryObject(SvCore.getDbtByName("SVAROG_SID_ACL"), null,
					DbJoinType.INNER, null, LinkType.CUSTOM, null, null);
			dbtSidAcl.addCustomJoinLeft("acl_object_id");
			dbtSidAcl.addCustomJoinRight("object_id");

			DbQueryObject dbtAcl = new DbQueryObject(SvCore.getDbtByName("SVAROG_ACL"), null, DbJoinType.LEFT, null,
					LinkType.CUSTOM_FREETEXT, null, null);
			SvDbType dbType = SvConf.getDbType();
			String timestamp = dbType.equals(SvDbType.ORACLE)? "sysdate" : "current_timestamp";
			dbtAcl.setCustomFreeTextJoin(
					" on (tbl5.object_id = tbl4.acl_object_id and "+timestamp+" between tbl5.dt_insert and tbl5.dt_delete) or tbl5.object_id is null");
			DbQueryObject dbtTable = new DbQueryObject(SvCore.getDbtByName("SVAROG_TABLES"), null, DbJoinType.INNER,
					null, null, null, null);
			DbQueryExpression q = new DbQueryExpression();
			
			q.addItem(dbtLink);
			q.addItem(dbtLinkType);
			q.addItem(dbtUserGroups);
			
			q.addItem(dbtSidAcl);
			q.addItem(dbtAcl);
			q.addItem(dbtTable);
			DbDataArray ret = svr.getObjects(q, null, null);
			String jsonStr = WsReactElements.prapareTableQueryData(ret, tablesUsedArray, tableShowArray,
					tablesusedCount, true, svr);
			JsonArray tmpResponseArray = gson.fromJson(jsonStr, JsonArray.class);
			// parse the data so we display only things that we need
			if (tmpResponseArray.size() > 0) {
				String[] stringFields = new String[6];
				String[] longFields = new String[1];
				stringFields[0] = ("SVAROG_LINK.STATUS");
				stringFields[1] = ("SVAROG_USER_GROUPS.GROUP_NAME");
				stringFields[2] = ("SVAROG_ACL.ACCESS_TYPE");
				stringFields[3] = ("SVAROG_ACL.LABEL_CODE");
				stringFields[4] = ("SVAROG_TABLES.TABLE_NAME");
				stringFields[5] = ("SVAROG_TABLES.LABEL_CODE");

				longFields[0] = ("SVAROG_SID_ACL.ACL_OBJECT_ID");
				
				responseArray = filterFields(tmpResponseArray, stringFields, longFields);
			}
			jrh.create(MessageType.SUCCESS, I18n.getText("console.success.defaultUsers"),
					I18n.getText("console.success.defaultUsers"), responseArray);
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}
	

	/**
	 * return all ACLs for a given group OBJECT_ID, field list for the show grid
	 * 
	 */
	@Path("/get-acl-by-user-field-list/sid/{session_id}")
	@GET
	@Produces("application/json")
	public Response getAclByUserFieldList(@PathParam("session_id") String session, 
			@Context HttpServletRequest httpRequest)
			throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(session)) {
			int tablesusedCount = 6;
			String[] tablesUsedArray = new String[tablesusedCount];
			Boolean[] svarogShowArray = new Boolean[tablesusedCount];
			Boolean[] tableShowArray = new Boolean[tablesusedCount];
			
			tablesUsedArray[0] = ("SVAROG_LINK");
			tablesUsedArray[1] = ("SVAROG_LINK_TYPE");
			tablesUsedArray[2] = ("SVAROG_USER_GROUPS");
			tablesUsedArray[3] = ("SVAROG_SID_ACL");
			tablesUsedArray[4] = ("SVAROG_ACL");
			tablesUsedArray[5] = ("SVAROG_TABLES");
			Arrays.fill(tableShowArray, Boolean.TRUE);
			Arrays.fill(svarogShowArray, Boolean.FALSE);
			WsReactElements wsReact = new WsReactElements();
			JsonArray jsonArrayResponse = wsReact.prepareJsonArrayFromGrid(tablesUsedArray, svarogShowArray,
					tableShowArray, svr);
			// remove columns that we don't need in the view so we save on size
			// of response
			if (jsonArrayResponse.size() > 0) {
				JsonArray tmpJsonArrayResponse = new JsonArray();
				for (int i = 0; i < jsonArrayResponse.size(); i++)
					if (jsonArrayResponse.get(i) != null && !(jsonArrayResponse.get(i)).isJsonNull()) {
						JsonObject temp = (JsonObject) jsonArrayResponse.get(i);
						if (temp.has("key")) {
							String tmpS = temp.get("key").getAsString().toUpperCase();
							if ("SVAROG_LINK.STATUS".equals(tmpS) || "SVAROG_ACL.ACCESS_TYPE".equals(tmpS)
									|| "SVAROG_ACL.LABEL_CODE".equals(tmpS) || "SVAROG_TABLES.TABLE_NAME".equals(tmpS)
									|| "SVAROG_TABLES.LABEL_CODE".equals(tmpS) || "SVAROG_USER_GROUPS.GROUP_NAME".equals(tmpS)   ) {
								temp.addProperty("visible", true);
								if (temp.has("width"))
									temp.remove("width");
								tmpJsonArrayResponse.add(temp);
							}
						}
					}
				jsonArrayResponse = tmpJsonArrayResponse;
			}
			jrh.create(MessageType.SUCCESS, I18n.getText("console.success.acl.sid.field.list"),
					I18n.getText("console.success.acl.sid.field.list"), jsonArrayResponse);
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(200).entity(jrh.getAll().toString()).build();
			}
			jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
					new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}
	
	
	
}
