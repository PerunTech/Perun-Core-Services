package com.prtech.perun_core.ws;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvExecManager;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbQueryExpression;
import com.prtech.svarog_common.DbQueryObject;
import com.prtech.svarog_common.DbQueryObject.DbJoinType;
import com.prtech.svarog_common.DbQueryObject.LinkType;
import com.prtech.svarog_common.DbSearch.DbLogicOperand;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchExpression;
import com.prtech.svarog_common.ResponseHandler;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;
import com.prtech.svarog_common.ResponseHandler.MessageType;

public class BusinessLogicWS {

	private static final Logger log4j = LogManager.getLogger(SvCore.class.getName());

	/*
	 * BLOCK START BL for current active project EDBAR f.r
	 */
	public JsonObject changeEmail(String fic, String idNo, String newEmail, HttpServletRequest httpRequest) {
		ResponseHandler jrh = new ResponseHandler();
		DbDataObject dboUser = null;
		SvSecurity svs = null;
		Boolean userFound = false;
		try {
			svs = new SvSecurity();
			dboUser = svs.getUser(fic.toUpperCase());
			if (dboUser != null) {
				if (dboUser.getVal("PIN").equals(idNo)) {
					userFound = true;
				} else {
					DbSearchCriterion critU = new DbSearchCriterion("FIC", DbCompareOperand.EQUAL, fic);
					if (svs.checkIfExistsConditional("FARMER", critU, "ID_NO", idNo)
							|| svs.checkIfExistsConditional("FARMER", critU, "TAX_NO", idNo))
						userFound = true;
				}
			}
			if (userFound) {
				svs.createUser((String) dboUser.getVal("USER_NAME"), null, (String) dboUser.getVal("FIRST_NAME"),
						(String) dboUser.getVal("LAST_NAME"), newEmail, (String) dboUser.getVal("PIN"),
						(String) dboUser.getVal("TAX_ID"), (String) dboUser.getVal("USER_TYPE"), "PENDING", true);
				dboUser.setVal("E_MAIL", newEmail);
				sendActivationEmail(dboUser, httpRequest);
				jrh.create(MessageType.SUCCESS, I18n.getText("new.email.set"), null);
			} else
				jrh.create(MessageType.ERROR, I18n.getText("user.farmer_embg_not_found"),
						I18n.getText("user.farmer_embg_not_found"));
		} catch (SvException e) {
			if (e.getLabelCode().equals("system.error.no_user_found")) {
				jrh.create(MessageType.ERROR, I18n.getText("user.farmer_notFound"),
						I18n.getLongText("user.farmer_notFound"), new JsonObject());
			} else {
				jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()));
			}
		} finally {
			if (svs != null) {
				svs.release();
				svs.close();
			}
		}
		return jrh.getAll();
	}

	public JsonObject doRegister(String fic, String idNo, String email, String password, HttpServletRequest fehost) {
		JsonObject jbo = null;
		try {
			jbo = registerUser(fic, idNo, email, password, fehost);
		} catch (Exception e) {
			ResponseHandler jrh = new ResponseHandler();
			jrh.create("EXCEPTION", I18n.getText("error.loading.plugin"),
					" agriPluginManager.PluginLogin " + e.getMessage(), new JsonObject());
			jbo = jrh.getAll();
		}
		return jbo;
	}

	DbSearchExpression getFarmerDbSearch(String userName, String pinVat) throws SvException {
		DbSearchExpression getFarmer = new DbSearchExpression();

		DbSearchExpression expIdOrTaxNo = new DbSearchExpression();
		DbSearchCriterion filterByIdNo = new DbSearchCriterion("ID_NO", DbCompareOperand.EQUAL, pinVat);
		DbSearchCriterion filterByTaxNo = new DbSearchCriterion("TAX_NO", DbCompareOperand.EQUAL, pinVat);
		DbSearchCriterion filterByStatusFarmer = new DbSearchCriterion(Rc.STATUS, DbCompareOperand.EQUAL, "VALID");
		DbSearchCriterion filterByFic = new DbSearchCriterion("FIC", DbCompareOperand.EQUAL, userName);

		filterByIdNo.setNextCritOperand(DbLogicOperand.OR.toString());
		expIdOrTaxNo.addDbSearchItem(filterByIdNo).addDbSearchItem(filterByTaxNo);

		getFarmer.addDbSearchItem(expIdOrTaxNo);
		getFarmer.addDbSearchItem(filterByFic);
		getFarmer.addDbSearchItem(filterByStatusFarmer);
		return getFarmer;
	}

	DbSearchExpression getPersonSearch(String pinVat) throws SvException {
		DbSearchExpression getPerson = new DbSearchExpression();

		DbSearchExpression expIdOrTaxNo = new DbSearchExpression();
		DbSearchCriterion filterByIdNo = new DbSearchCriterion("ID_NO", DbCompareOperand.EQUAL, pinVat);
		DbSearchCriterion filterByTaxNo = new DbSearchCriterion("TAX_NO", DbCompareOperand.EQUAL, pinVat);
		DbSearchCriterion filterByStatusFarmer = new DbSearchCriterion(Rc.STATUS, DbCompareOperand.EQUAL, "VALID");

		filterByIdNo.setNextCritOperand(DbLogicOperand.OR.toString());
		expIdOrTaxNo.addDbSearchItem(filterByIdNo).addDbSearchItem(filterByTaxNo);

		getPerson.addDbSearchItem(expIdOrTaxNo);
		getPerson.addDbSearchItem(filterByStatusFarmer);
		return getPerson;
	}

	boolean verifyValidFarmer(String fic, DbSearchExpression getFarmer) throws SvException {
		boolean userExists = false;
		try (SvSecurity svs = new SvSecurity()) {
			userExists = svs.checkIfExists("FARMER", getFarmer);
			long lf = Long.parseLong(fic);
			if (!userExists) {
				svs.switchUser(svCONST.serviceUser);
				try (SvExecManager svx = new SvExecManager(svs)) {
					Map<String, Object> params = new HashMap<String, Object>();
					params.put("FIC", fic);
					JsonObject configuration = (JsonObject) svx.execute("IMPORTER.FARM_DATA", params, null);
				}
				userExists = svs.checkIfExists("FARMER", getFarmer);
			}

		} catch (NumberFormatException ex) {
			userExists = false;
		}
		return userExists;
	}

	/**
	 * method to logout user, we accept the token as parameter, remove it from the
	 * list of tokens that have access
	 * 
	 * @param userName String user-name to login with, farmer identification code
	 *                 (FIC) or name for agency workers
	 * @param pinVat   String used for validation, usually birth number for farmers
	 *                 or company tax number for companies, agency workers dont use
	 *                 this code,so you can use anytihing or null
	 * @param eMail    String e-mail of the user, used for sending activation link
	 *                 or forgotten password change
	 * @param password String password for the user
	 * @param feHost   String front end host-name generated from calling the web
	 *                 service
	 * 
	 * @return String message if user was created or error message of what went
	 *         wrong
	 */
	public JsonObject registerUser(String userName, String pinVat, String eMail, String password,
			HttpServletRequest feHost) {
		ResponseHandler jrh = new ResponseHandler();
		String firstName = " ";
		String lastName = " ";
		// Secondary ID of the user. Tax ID if legal entity.
		String taxId = "";
		if (!(userName == null || password == null || eMail == null || pinVat == null)) {
			DbDataObject dboUser = null;
			try (SvSecurity svs = new SvSecurity();) {
				// check if the user is registered in the Farm register
				DbSearchExpression getFarmer = getFarmerDbSearch(userName, pinVat);
				DbSearchExpression getPerson = getPersonSearch(pinVat);
				/* if farmer does not exist do not create user f.r */
				Boolean farmerExists = verifyValidFarmer(userName, getFarmer);
				Boolean matchingUserNamePinVat = svs.checkIfExistsConditional("FARMER", getFarmer, "FIC",
						userName.toUpperCase());

				if (farmerExists && matchingUserNamePinVat) {
					dboUser = svs.createUser(userName.toUpperCase(), password.toUpperCase(), firstName.toUpperCase(),
							lastName.toUpperCase(), eMail, pinVat.toUpperCase(), taxId.toUpperCase(), "EXTERNAL",
							"PENDING");
					if (dboUser != null) {
						jrh.create(MessageType.SUCCESS, I18n.getText("user.user1_created"),
								I18n.getText("user.created.activation"), new JsonObject());
						try {
							svs.empowerUser(dboUser, "PERSON", getPerson);
						} catch (SvException e) {
							log4j.error("Error empowering user:" + getPerson.toSimpleJson(), e);
						}

						try {
							svs.empowerUser(dboUser, "FARMER", getFarmer);
						} catch (SvException e) {
							log4j.error("Error empowering user:" + getFarmer.toSimpleJson(), e);
						}

						DbDataArray empowerments = svs.getPOAObjects(dboUser.getObjectId(), "PERSON");
						if (!empowerments.getItems().isEmpty() && empowerments.getItems().get(0) != null) {
							DbDataObject personObj = empowerments.getItems().get(0);
							if (personObj.getVal("NAME") != null)
								firstName = personObj.getVal("NAME").toString();
//							if (farmerObj.getVal("SURNAME") != null)
//								lastName = farmerObj.getVal("SURNAME").toString();
						}
						svs.createUser((String) dboUser.getVal("USER_NAME"), (String) password.toUpperCase(), firstName,
								lastName, (String) dboUser.getVal("E_MAIL"), (String) dboUser.getVal("PIN"),
								(String) dboUser.getVal("TAX_ID"), (String) dboUser.getVal("USER_TYPE"), "PENDING",
								true);
						/*
						 * if local than print url f.r to do better solution
						 */
						if (svs.getPublicParam("frontend.gui_host") != null
								&& svs.getPublicParam("frontend.gui_host").trim().length() > 4)
							if (svs.getPublicParam("frontend.gui_host")
									.equals("http://192.168.100.155:9090/perun/index.html")) {
								String guiHost = svs.getPublicParam("frontend.gui_host");
								String localUrl = guiHost + "#/home/activate?uuid=" + dboUser.getVal("USER_UID");
								jrh.create(MessageType.SUCCESS, I18n.getText("user.user1_created"), localUrl,
										new JsonObject());
							} else {
								sendActivationEmail(dboUser, feHost);
							}
					} else {
						jrh.create(MessageType.ERROR, I18n.getText("user.alreadyCreated"),
								I18n.getText("user.alreadyCreated"), new JsonObject());
					}
				} else {
					jrh.create(MessageType.ERROR, I18n.getText("user.farmer_notFound"),
							I18n.getText("user.farmer_notFound"), new JsonObject());
				}
			} catch (SvException e) {
				if (e.getLabelCode().equals("system.error.user_exists")) {
					jrh.create(MessageType.ERROR, e.getLabelCode(), I18n.getText("user.userExists"), new JsonObject());
				} else {
					jrh.create(MessageType.ERROR, e.getLabelCode(), e.getLabelCode(), new JsonObject());
				}
			}
		}
		return jrh.getAll();
	}

	public JsonObject checkBeforeSend(DbDataObject dbo, String edbr, HttpServletRequest httpRequest)
			throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		// SvSecurity svs = null;
		try {
			// svs = new SvSecurity();
			if (dbo != null && dbo.getVal("PIN").equals(edbr) && dbo.getStatus().equals("PENDING")) {
//				if (svs.getPublicParam("frontend.gui_host").equals("http://192.168.100.155:9090/perun/index.html")) {
//					String uri = svs.getPublicParam("frontend.gui_host") + "#/home/activate?uuid="
//							+ dbo.getVal("USER_UID");
//					jrh.create(MessageType.SUCCESS, I18n.getText("user.user1_created"),
//							I18n.getText("user.created.activation"), uri);
//				} else {
				sendActivationEmail(dbo, httpRequest);
				jrh.create(MessageType.SUCCESS, I18n.getText("user.user1_created"),
						I18n.getText("user.created.activation"), new JsonObject());
				// }
			} else {
				jrh.create(MessageType.ERROR, I18n.getText("user.farmer_notFound"),
						I18n.getText("user.farmer_notFound"), new JsonObject());
			}
		} catch (SvException e) {
			jrh = ResponseHandler.responseHandlerByException(e);
		}
		return jrh.getAll();
	}

	private boolean sendActivationEmail(DbDataObject dboUser, HttpServletRequest httpRequest) throws SvException {
		String mailBody = I18n.getLongText("mail.body_activation");
		String feHost = "";
		SvSecurity svs = new SvSecurity();
		WsSecurityActions wsc = new WsSecurityActions();
		if (svs.getPublicParam("frontend.gui_host") != null
				&& svs.getPublicParam("frontend.gui_host").trim().length() > 4)
			feHost = svs.getPublicParam("frontend.gui_host");
		else
			feHost = httpRequest.getScheme() + "://" + httpRequest.getServerName() + ":" + // ":"
					httpRequest.getServerPort();
		String uri = feHost + "#/home/activate?uuid=" + dboUser.getVal("USER_UID");
		HashMap<String, String> extParams = new HashMap<String, String>();
		extParams.put("{NAME}", dboUser.getVal("FIRST_NAME")
				+ (dboUser.getVal("LAST_NAME") != null ? " " + dboUser.getVal("LAST_NAME") : ""));
		extParams.put("{ACTIVATION_LINK}", uri);

		wsc.sendMail((String) dboUser.getVal("E_MAIL"), I18n.getText("mail.subject_activation"), mailBody, extParams);

		if (svs != null) {
			svs.release();
			svs.close();
		}
		return true;
	}

	/**
	 * method to check if the user is valid and the idNumber for it is matching, if
	 * the user is company, this should be company number matching to "TAX_NO". if
	 * user is simple farmer, we check his birth number
	 * 
	 * @param dboUser DbDataObject user that we are processing
	 * @param fic     String Farmer Identification Code
	 * @param idNo    String farmer birth number or company tax number
	 * @param svs     SvSecurity connected
	 * 
	 * @return Boolean true if such farmer exist
	 * 
	 * @throws SvException
	 */
	public Boolean hasValidFarmers(DbDataObject dboUser, String fic, String idNo, SvSecurity svs) throws SvException {
		DbDataArray personDb = svs.getPOAObjects(dboUser.getObjectId(), "PERSON");
		if (personDb != null)
			for (DbDataObject person : personDb.getItems()) {
				String personExternalId = "G".equalsIgnoreCase((String) person.getVal("PERSON_TYPE"))
						? ((String) person.getVal("TAX_NO"))
						: ((String) person.getVal("ID_NO"));
				// if (fic.equals(person.getVal("FIC")) && idNo.equals(personExternalId))
				if (idNo.equals(personExternalId))
					return true;
			}
		return false;
	}

	public JsonObject loginUser(String username, String password) throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		SvSecurity svsec = null;
		SvReader svr = null;
		DbDataObject dboUserG = null;
		try {
			svsec = new SvSecurity();
			dboUserG = new DbDataObject();
			if (!(username == null || password == null)) {
				String token = svsec.logon(username.toUpperCase(), password.toUpperCase());
				svr = new SvReader(token);
				// SvarogLogin.systemUnderMaintenanceCheck(svr);
				/* add token for login */
				JsonObject combo = new JsonObject();
				combo.addProperty("token", token);

				dboUserG = svr.getDefaultUserGroup();
				if (dboUserG != null && dboUserG.getVal("GROUP_NAME").equals("USERS")) {
					// search if the username is actually a farmer
					JsonObject jrhFarmerJson = searchFarmer(svr, "FIC", username);
					String resType = "";
					if (jrhFarmerJson.has("type"))
						resType = jrhFarmerJson.get("type").getAsString();
					if (resType.equalsIgnoreCase("ERROR") || resType.equalsIgnoreCase("EXCEPTION"))
						jrh.create(resType, jrhFarmerJson.get("title").getAsString(),
								jrhFarmerJson.get("message").getAsString(), new JsonObject());
					else {
						JsonArray farmerJson = new JsonArray();
						if (jrhFarmerJson.has("data")) {
							JsonElement responseData = jrhFarmerJson.get("data");
							if (responseData.isJsonArray())
								farmerJson = (JsonArray) responseData;
						}

						if (svr.isAdmin()) {
							combo.addProperty("redirect", "/MODULE_MENU");
							jrh.create(MessageType.SUCCESS, I18n.getText("login.success"),
									I18n.getText("login.success"), combo);
						} else {
							// if it is a farmer , get other data needed for the GUI
							if (farmerJson.size() > 0) {
								JsonObject farmer = null;
								farmer = (JsonObject) farmerJson.get(0);
								JsonObject jrhFieldListFarmer = getTableFiledList(token, "FARMER");
								JsonArray fieldListFarmer = (JsonArray) jrhFieldListFarmer.get("data");
								combo.add("farmer", farmer);
								combo.addProperty("redirect", "/FARMER");
								combo.add("configuration", fieldListFarmer);
								jrh.create(MessageType.SUCCESS, I18n.getText("login.success"),
										I18n.getText("login.success"), combo);
							} else {
								// if missing farmer info
								jrh.create(MessageType.ERROR, I18n.getText("login.missingInfo"),
										I18n.getText("login.login.missingInfo"), new JsonObject());
							}
						}
					}
				} else {
					jrh.create(MessageType.SUCCESS, I18n.getText("login.success"), I18n.getText("login.success"),
							combo);
				}
			}
		} catch (SvException e) {
			throw (e);
		} finally {
			if (svsec != null) {
				svsec.release();
				svsec.close();
			}
			if (svr != null) {
				svr.release();
				svr.close();
			}
		}
		return jrh.getAll();
	}

	public JsonObject searchFarmer(SvReader svr, String searchField, String searchValue) {
		ResponseHandler jrh = new ResponseHandler();
		JsonArray jsonArrayResponse = new JsonArray();
		try {
			if (searchField != null && searchValue != null) {
				Gson gson = new Gson();
				DbDataObject databaseTypeOrgUnit = svr.getObjectById(SvCore.getTypeIdByName("ORG_UNITS"),
						svCONST.OBJECT_TYPE_TABLE, null);
				DbDataObject databaseTypeFarmer = svr.getObjectById(SvCore.getTypeIdByName("FARMER"),
						svCONST.OBJECT_TYPE_TABLE, null);
				// Fill the field arrays with config from database
				DbDataObject linkType = SvCore.getLinkType("POA", databaseTypeOrgUnit.getObjectId(),
						databaseTypeFarmer.getObjectId());
				DbSearchCriterion crit1 = new DbSearchCriterion(searchField, DbCompareOperand.LIKE,
						"%" + searchValue.toUpperCase() + "%");
				DbQueryObject valOrgUni = new DbQueryObject(databaseTypeOrgUnit, null, DbJoinType.INNER, linkType,
						LinkType.DBLINK, null, null);
				DbQueryObject valFarmer = new DbQueryObject(databaseTypeFarmer, crit1, DbJoinType.INNER, null, null,
						null, null);
				DbQueryExpression q = new DbQueryExpression();
				// add the items
				q.addItem(valOrgUni);
				q.addItem(valFarmer);
				DbDataArray ret = svr.getObjects(q, null, null);
				// prepare the data to be displayed
				int tablesusedCount = 2;
				String[] tablesUsedArray = new String[tablesusedCount];
				Boolean[] tableShowArray = new Boolean[tablesusedCount];
				tablesUsedArray[0] = ("ORG_UNITS");
				tablesUsedArray[1] = ("FARMER");
				tableShowArray[0] = true;
				tableShowArray[1] = true;
				String jsonStr = WsReactElements.prapareTableQueryData(ret, tablesUsedArray, tableShowArray,
						tablesusedCount, true, svr);
				jsonArrayResponse = gson.fromJson(jsonStr, JsonArray.class);
				// remove some unused /unwanted fields
				if (jsonArrayResponse.size() > 0) {
					JsonArray tmpJsonArrayResponse = new JsonArray();
					for (int i = 0; i < jsonArrayResponse.size(); i++) {
						JsonObject temp = (JsonObject) jsonArrayResponse.get(i);
						if (temp.has("ORG_UNITS.PARENT_ID"))
							temp.remove("ORG_UNITS.PARENT_ID");
						if (temp.has("ORG_UNITS.STATUS"))
							temp.remove("ORG_UNITS.STATUS");
						if (temp.has("ORG_UNITS.USER_ID"))
							temp.remove("ORG_UNITS.USER_ID");
						if (temp.has("ORG_UNITS.ORG_UNIT_TYPE"))
							temp.remove("ORG_UNITS.ORG_UNIT_TYPE");
						if (temp.has("ORG_UNITS.EXTERNAL_ID"))
							temp.remove("ORG_UNITS.EXTERNAL_ID");
						if (!"{}".equals(temp.toString()))
							tmpJsonArrayResponse.add(temp);
					}
					jsonArrayResponse = tmpJsonArrayResponse;
				}
				jrh.create(MessageType.SUCCESS, I18n.getText("farmer.data.found"), I18n.getText("data.read"),
						jsonArrayResponse);
			}
		} catch (JsonSyntaxException e) {
			jrh.create(MessageType.ERROR, I18n.getText("error.loading.farmer.data"),
					I18n.getText("error.loading.farmer.data"), jsonArrayResponse);
		} catch (SvException e) {
			jrh = ResponseHandler.responseHandlerByException(e);
		} finally {
			if (svr != null) {
				svr.release();
				svr.close();
			}
		}
		return jrh.getAll();
	}

	public JsonObject getTableFiledList(String token, String tableNameOrID) {
		String tableName = tableNameOrID;
		SvReader svr = null;
		ResponseHandler jrh = new ResponseHandler();
		JsonArray jsonArrayResponse = new JsonArray();
		try {
			Long tableLong = Long.parseLong(tableName);
			if (tableLong > 0)
				tableName = WsReactElements.getTableNameById(tableLong, svr);
		} catch (Exception e) {
			// probably not a number, we don't care since string is what we need
		}
		try {
			if (!(token == null || tableName == null)) {
				svr = new SvReader(token);
				WsReactElements reactWS = new WsReactElements();
				Response responseArrayObject = reactWS.getTableFieldList(token, tableName, null);
				Gson gson = new Gson();
				jsonArrayResponse = gson.fromJson(responseArrayObject.getEntity().toString(), JsonArray.class);
				JsonArray tmpJsonArrayResponse = new JsonArray();
				// since STATUS field is made visible, we actually remove the
				// field here, we can set it visible only for some custom tables
				for (int i = 0; i < jsonArrayResponse.size(); i++)
					if (jsonArrayResponse.get(i) != null && !(jsonArrayResponse.get(i)).isJsonNull()) {
						JsonObject form = (JsonObject) jsonArrayResponse.get(i);
						if (!form.get(Rc.FIELD_NAME).getAsString().equalsIgnoreCase(Rc.STATUS))
							tmpJsonArrayResponse.add(form);
					}
				jsonArrayResponse = tmpJsonArrayResponse;
			}
			jrh.create(MessageType.SUCCESS, I18n.getText("fields.grid.list.loaded"),
					I18n.getText("configuration.loaded"), jsonArrayResponse);
		} catch (JsonSyntaxException e) {
			jrh.create(MessageType.ERROR, I18n.getText("error.converting.data"), e.getMessage(), jsonArrayResponse);
		} catch (SvException e) {
			jrh = ResponseHandler.responseHandlerByException(e);
		} finally {
			if (svr != null) {
				svr.release();
				svr.close();
			}
		}
		return jrh.getAll();
	}

	public JsonObject menuService(String token) {
		JsonObject jsonObj = new JsonObject();
		JsonArray arrItems = new JsonArray();
		arrItems.add(helpButton());
		arrItems.add(logoutButton(token));
		jsonObj.add("SERVICE_MENU", arrItems);
		return jsonObj;
	}

	public JsonObject menuMain(String token) {
		JsonObject jsonObj = new JsonObject();
		jsonObj.addProperty("default", "APPLICATION");
		JsonArray arrMainMenuItems = new JsonArray();
		// add item general info, that will display record for farmer,
		// taken from table FARMER, form is read-only
		// new button- menu_item
		JsonObject jsonMMitem = new JsonObject();
		// new button sub-menu_item
		JsonObject jsonMMitemGrid = null;
		// configuration for the menu item
		JsonObject objectConfiguration = new JsonObject();
		// configuration for the sub-grid item
		JsonObject objectConfigurationGrid = null;
		// configuration when there is a form/details view
		JsonObject form = null;
		// form configuration JSONSchema for the standard form
		JsonObject formConfiguration = new JsonObject();
		// form configuration UISchema for the standard form
		JsonObject formUiSchema = new JsonObject();
		// now do we get the data for the selected form object
		JsonObject formData = new JsonObject();
		// is there a save/delete button on the form
		JsonObject formSave = null;
		// how do we get the list of fields that will be shown on the
		// grid
		JsonObject gridConfiguration = null;
		// what happens when we click on row of grid
		JsonObject gridOnRowSelect = null;
		// how do we get the data to fill the grid
		JsonObject gridData = null;
		// in-line save / save all for the grid used in declaration and
		// adm_control

		jsonMMitem.addProperty("label", I18n.getText("perun.menu.farmer.general_info"));
		jsonMMitem.addProperty("ID", "FARMER");
		objectConfiguration.addProperty("enabled", true);
		objectConfiguration.addProperty("readOnly", true);
		objectConfiguration.addProperty("type", "form");
		formConfiguration.addProperty("type", "GET");
		formConfiguration.addProperty("onSubmit", "/farmer/formStyleJSONTableFarmer/" + token);
		formUiSchema.addProperty("type", "GET");
		formUiSchema.addProperty("onSubmit", "/farmer/formStyleUISchemaTableFarmer/" + token);
		formData.addProperty("type", "GET");
		formData.addProperty("onSubmit", "/farmer/formStyleDataTableFarmer/" + token + "/{objectId}");
		objectConfiguration.add("configuration", formConfiguration);
		objectConfiguration.add("uischema", formUiSchema);
		objectConfiguration.add("data", formData);
		jsonMMitem.add("objectConfiguration", objectConfiguration);
		arrMainMenuItems.add(jsonMMitem);

		// add item "holding members", that will display record for all
		// memebers that are part of this farm, get_objects_by_parent_id
		jsonMMitem = new JsonObject();
		jsonMMitem.addProperty("label", I18n.getText("holding_member.general"));
		jsonMMitem.addProperty("ID", "HOLDING_MEMBER");
		objectConfiguration = new JsonObject();
		objectConfiguration.addProperty("enabled", true);
		objectConfiguration.addProperty("readOnly", true);
		objectConfiguration.addProperty("type", "grid");
		gridConfiguration = new JsonObject();
		gridConfiguration.addProperty("type", "GET");
		gridConfiguration.addProperty("onSubmit", "/table/tableFieldList/" + token + "/HOLDING_MEMBER");
		gridData = new JsonObject();
		gridData.addProperty("type", "GET");
		gridData.addProperty("onSubmit", "/table/tableDataByParentId/" + token + "/HOLDING_MEMBER/{objectId}");
		objectConfiguration.add("configuration", gridConfiguration);
		objectConfiguration.add("data", gridData);

		// when clicking an row from grid, open details for that record
		form = new JsonObject();
		form.addProperty("enabled", true);
		form.addProperty("readOnly", true);
		form.addProperty("type", "form");
		formConfiguration = new JsonObject();
		formConfiguration.addProperty("type", "GET");
		formConfiguration.addProperty("onSubmit", "/table/formStyleTableJsonSchema/" + token + "/HOLDING_MEMBER");
		formUiSchema = new JsonObject();
		formUiSchema.addProperty("type", "GET");
		formUiSchema.addProperty("onSubmit", "/table/formStyleTableUiSchema/" + token + "/HOLDING_MEMBER");
		formData = new JsonObject();
		formData.addProperty("type", "GET");
		formData.addProperty("onSubmit", "/table/formStyleTableData/" + token + "/{objectId}/HOLDING_MEMBER");
		form.add("configuration", formConfiguration);
		form.add("uischema", formUiSchema);
		form.add("data", formData);
		objectConfiguration.add("form", form);
		jsonMMitem.add("objectConfiguration", objectConfiguration);
		arrMainMenuItems.add(jsonMMitem);

		// add item "applications", that will display all aplications,
		// and have option to create a new apllication
		jsonMMitem = new JsonObject();
		jsonMMitem.addProperty("label", I18n.getText("application.general"));
		jsonMMitem.addProperty("ID", "APPLICATION");
		objectConfiguration = new JsonObject();
		objectConfiguration.addProperty("enabled", true);
		objectConfiguration.addProperty("readOnly", false);
		objectConfiguration.addProperty("type", "grid");
		gridOnRowSelect = new JsonObject();
		gridOnRowSelect.addProperty("redirect", "/farmer/application");
		gridOnRowSelect.addProperty("get",
				"/configuration/getAppConfiguration/" + token + "/APPLICATION_MENU/{APPLICATION.OBJECT_ID}");
		gridConfiguration = new JsonObject();
		gridConfiguration.addProperty("type", "GET");
		gridConfiguration.addProperty("onSubmit", "/farmer/tableApplicationFieldList/" + token);
		gridData = new JsonObject();
		gridData.addProperty("type", "GET");
		gridData.addProperty("onSubmit", "/farmer/searchApplicationByFarmerId/" + token + "/{objectId}");
		objectConfiguration.add("onRowSelect", gridOnRowSelect);
		objectConfiguration.add("configuration", gridConfiguration);
		objectConfiguration.add("data", gridData);

		// when clicking an row from grid, open details for that record
		form = new JsonObject();
		form.addProperty("enabled", true);
		form.addProperty("type", "form");
		formConfiguration = new JsonObject();
		formConfiguration.addProperty("type", "GET");
		formConfiguration.addProperty("onSubmit", "/table/formStyleTableJsonSchema/" + token + "/APPLICATION");
		formUiSchema = new JsonObject();
		formUiSchema.addProperty("type", "GET");
		formUiSchema.addProperty("onSubmit", "/table/formStyleTableUiSchema/" + token + "/APPLICATION");
		formData = new JsonObject();
		formData.addProperty("type", "GET");
		formData.addProperty("onSubmit", "/table/formStyleTableData/" + token + "/{objectId}/APPLICATION");
		formSave = new JsonObject();
		formSave.addProperty("type", "POST");
		formSave.addProperty("onSave", "/table/createTableRecord/" + token + "/APPLICATION/{objectId}/{jsonString}");
		form.add("configuration", formConfiguration);
		form.add("uischema", formUiSchema);
		form.add("data", formData);
		form.add("save", formSave);
		objectConfiguration.add("form", form);
		jsonMMitem.add("objectConfiguration", objectConfiguration);
		arrMainMenuItems.add(jsonMMitem);

		// add item "animals", that will display all animals that are
		// owned by the farmer, get_objects_by_link
		jsonMMitem = new JsonObject();
		jsonMMitem.addProperty("label", I18n.getText("animal.general"));
		jsonMMitem.addProperty("ID", "ANIMAL");
		objectConfiguration = new JsonObject();
		objectConfiguration.addProperty("enabled", true);
		objectConfiguration.addProperty("readOnly", true);
		objectConfiguration.addProperty("type", "grid");
		gridConfiguration = new JsonObject();
		gridConfiguration.addProperty("type", "GET");
		gridConfiguration.addProperty("onSubmit", "/farmer/tableAnimalFieldList/" + token);
		gridData = new JsonObject();
		gridData.addProperty("type", "GET");

		// link between farmer and animal is year based
		// Long year = Long.valueOf(new DateTime().year().get());
		Long year = 2019L;
		String linkType = "LINK_ANIMAL_FARMER" + "_" + year;
		if (year.compareTo(2016L) == 0)
			linkType = "LINK_ANIMAL_FARMER";
		gridData.addProperty("onSubmit", "/farmer/searchAnimalByFarmerId/" + token + "/{objectId}/" + linkType);
		objectConfiguration.add("configuration", gridConfiguration);
		objectConfiguration.add("data", gridData);

		// when clicking an row from grid, open details for that record
		form = new JsonObject();
		form.addProperty("enabled", true);
		form.addProperty("readOnly", true);
		form.addProperty("type", "form");
		formConfiguration = new JsonObject();
		formConfiguration.addProperty("type", "GET");
		formConfiguration.addProperty("onSubmit", "/table/formStyleTableJsonSchema/" + token + "/ANIMAL");
		formUiSchema = new JsonObject();
		formUiSchema.addProperty("type", "GET");
		formUiSchema.addProperty("onSubmit", "/table/formStyleTableUiSchema/" + token + "/ANIMAL");
		formData = new JsonObject();
		formData.addProperty("type", "GET");
		formData.addProperty("onSubmit", "/table/formStyleTableData/" + token + "/{objectId}/ANIMAL");
		form.add("configuration", formConfiguration);
		form.add("uischema", formUiSchema);
		form.add("data", formData);
		objectConfiguration.add("form", form);
		jsonMMitem.add("objectConfiguration", objectConfiguration);
		arrMainMenuItems.add(jsonMMitem);

		// add item "LPIS_PARCEL", that will display all parcels that
		// are owned by the farmer, get_objects_by_ link or parent

		JsonArray mainArray = new JsonArray();

		jsonMMitem = new JsonObject();
		jsonMMitem.addProperty("label", I18n.getText("lpis_parcel.general"));
		jsonMMitem.addProperty("ID", "LPIS_PARCEL");
		objectConfiguration = new JsonObject();
		objectConfiguration.addProperty("enabled", true);
		objectConfiguration.addProperty("readOnly", true);
		objectConfiguration.addProperty("type", "grid");
		gridConfiguration = new JsonObject();
		gridConfiguration.addProperty("type", "GET");
		gridConfiguration.addProperty("onSubmit", "/table/tableFieldList/" + token + "/LPIS_PARCEL");
		gridData = new JsonObject();
		gridData.addProperty("type", "GET");
		gridData.addProperty("onSubmit", "/table/tableDataByParentId/" + token + "/LPIS_PARCEL/{objectId}");
		objectConfiguration.add("configuration", gridConfiguration);
		objectConfiguration.add("data", gridData);

		// when clicking an row from grid, open details for that record
		form = new JsonObject();
		form.addProperty("enabled", true);
		form.addProperty("readOnly", true);
		form.addProperty("type", "form");
		formConfiguration = new JsonObject();
		formConfiguration.addProperty("type", "GET");
		formConfiguration.addProperty("onSubmit", "/table/formStyleTableJsonSchema/" + token + "/LPIS_PARCEL");
		formUiSchema = new JsonObject();
		formUiSchema.addProperty("type", "GET");
		formUiSchema.addProperty("onSubmit", "/table/formStyleTableUiSchema/" + token + "/LPIS_PARCEL");
		formData = new JsonObject();
		formData.addProperty("type", "GET");
		formData.addProperty("onSubmit", "/table/formStyleTableData/" + token + "/{objectId}/LPIS_PARCEL");
		form.add("configuration", formConfiguration);
		form.add("uischema", formUiSchema);
		form.add("data", formData);
		// objectConfiguration.add("form", form);

		JsonObject lpisObject = new JsonObject();
		lpisObject.addProperty("label", I18n.getText("lpis_parcel.general"));
		lpisObject.addProperty("ID", "LPIS");
		lpisObject.add("objectConfiguration", objectConfiguration);

		mainArray.add(lpisObject);

		// SvReader svr = null;
		// try {
		// svr = new SvReader(token);
		// DbDataObject userObj = SvCore.getUserBySession(token);

		// if (isUserInGroup(userObj, "ADMINISTRATORS", svr) ||
		// isUserInGroup(userObj, "ADM_CONTROL_USERS", svr)
		// || isUserInGroup(userObj, "READONLY_USERS", svr)) {

		// add items for all 4 application types
		JsonObject lpisHistoryObject = new JsonObject();
		lpisHistoryObject.addProperty("label", "2016");
		lpisHistoryObject.addProperty("ID", "LPIS_HISTORY_2016");
		objectConfiguration = new JsonObject();
		objectConfiguration.addProperty("enabled", true);
		objectConfiguration.addProperty("readOnly", true);
		objectConfiguration.addProperty("type", "grid");
		gridConfiguration = new JsonObject();
		gridConfiguration.addProperty("type", "GET");
		gridConfiguration.addProperty("onSubmit", "/gridReport/getReportFirldList/" + token + "/LPIS_HISTORY/0/0");
		gridData = new JsonObject();
		gridData.addProperty("type", "GET");
		gridData.addProperty("onSubmit", "/gridReport/getReport/" + token + "/LPIS_HISTORY/{objectId}/2016");
		objectConfiguration.add("configuration", gridConfiguration);
		objectConfiguration.add("data", gridData);
		lpisHistoryObject.add("objectConfiguration", objectConfiguration);
		mainArray.add(lpisHistoryObject);

		lpisHistoryObject = new JsonObject();
		lpisHistoryObject.addProperty("label", "2017");
		lpisHistoryObject.addProperty("ID", "LPIS_HISTORY_2017");
		objectConfiguration = new JsonObject();
		objectConfiguration.addProperty("enabled", true);
		objectConfiguration.addProperty("readOnly", true);
		objectConfiguration.addProperty("type", "grid");
		gridConfiguration = new JsonObject();
		gridConfiguration.addProperty("type", "GET");
		gridConfiguration.addProperty("onSubmit", "/gridReport/getReportFirldList/" + token + "/LPIS_HISTORY/0/0");
		gridData = new JsonObject();
		gridData.addProperty("type", "GET");
		gridData.addProperty("onSubmit", "/gridReport/getReport/" + token + "/LPIS_HISTORY/{objectId}/2017");
		objectConfiguration.add("configuration", gridConfiguration);
		objectConfiguration.add("data", gridData);
		lpisHistoryObject.add("objectConfiguration", objectConfiguration);
		mainArray.add(lpisHistoryObject);

		lpisHistoryObject = new JsonObject();
		lpisHistoryObject.addProperty("label", "2018");
		lpisHistoryObject.addProperty("ID", "LPIS_HISTORY_2018");
		objectConfiguration = new JsonObject();
		objectConfiguration.addProperty("enabled", true);
		objectConfiguration.addProperty("readOnly", true);
		objectConfiguration.addProperty("type", "grid");
		gridConfiguration = new JsonObject();
		gridConfiguration.addProperty("type", "GET");
		gridConfiguration.addProperty("onSubmit", "/gridReport/getReportFirldList/" + token + "/LPIS_HISTORY/0/0");
		gridData = new JsonObject();
		gridData.addProperty("type", "GET");
		gridData.addProperty("onSubmit", "/gridReport/getReport/" + token + "/LPIS_HISTORY/{objectId}/2018");
		objectConfiguration.add("configuration", gridConfiguration);
		objectConfiguration.add("data", gridData);
		lpisHistoryObject.add("objectConfiguration", objectConfiguration);
		mainArray.add(lpisHistoryObject);

		lpisHistoryObject = new JsonObject();
		lpisHistoryObject.addProperty("label", "2019");
		lpisHistoryObject.addProperty("ID", "LPIS_HISTORY_2019");
		objectConfiguration = new JsonObject();
		objectConfiguration.addProperty("enabled", true);
		objectConfiguration.addProperty("readOnly", true);
		objectConfiguration.addProperty("type", "grid");
		gridConfiguration = new JsonObject();
		gridConfiguration.addProperty("type", "GET");
		gridConfiguration.addProperty("onSubmit", "/gridReport/getReportFirldList/" + token + "/LPIS_HISTORY/0/0");
		gridData = new JsonObject();
		gridData.addProperty("type", "GET");
		gridData.addProperty("onSubmit", "/gridReport/getReport/" + token + "/LPIS_HISTORY/{objectId}/2019");
		objectConfiguration.add("configuration", gridConfiguration);
		objectConfiguration.add("data", gridData);
		lpisHistoryObject.add("objectConfiguration", objectConfiguration);
		mainArray.add(lpisHistoryObject);

		// }
		//
		// } catch (SvException e) {
		// // user not found, no admin console for him
		// } finally {
		// releseAll(svr);
		// }
		//

		jsonMMitem.add("data", mainArray);
		// jsonMMitem.add("objectConfiguration", objectConfiguration);
		arrMainMenuItems.add(jsonMMitem);

		// add item "PRODUCTION PLAN ", that will display all parcels
		// from the farmer on which they can set the cultures that are
		// going to grow

		mainArray = new JsonArray();
		jsonMMitem = new JsonObject();
		jsonMMitem.addProperty("label", I18n.getText("land_use_plan.general"));
		jsonMMitem.addProperty("ID", "PPLAN");

		objectConfiguration = new JsonObject();
		objectConfiguration.addProperty("enabled", true);
		objectConfiguration.addProperty("readOnly", true);
		objectConfiguration.addProperty("type", "multigrid");
		JsonArray grids = new JsonArray();

		// add grid item "CAD_PARCEL", that will display all parcels that are
		// owned by the farmer, get_objects_by_parent
		// production plan will be made for the parcel selected from
		// this grid
		jsonMMitemGrid = new JsonObject();
		jsonMMitemGrid.addProperty("label", I18n.getText("cad_parcel.general"));
		jsonMMitemGrid.addProperty("ID", "CAD_PARCEL");
		objectConfigurationGrid = new JsonObject();
		objectConfigurationGrid.addProperty("enabled", true);
		objectConfigurationGrid.addProperty("readOnly", true);
		objectConfigurationGrid.addProperty("type", "grid");
		gridConfiguration = new JsonObject();
		gridConfiguration.addProperty("type", "GET");
		gridConfiguration.addProperty("onSubmit", "/table/tableFieldList/" + token + "/CAD_PARCEL");
		gridData = new JsonObject();
		gridData.addProperty("type", "GET");
		gridData.addProperty("onSubmit", "/table/tableDataByParentId/" + token + "/CAD_PARCEL/{objectId}");
		objectConfigurationGrid.add("configuration", gridConfiguration);
		objectConfigurationGrid.add("data", gridData);

		// when clicking an row from grid, open details for that record
		form = new JsonObject();
		form.addProperty("enabled", true);
		form.addProperty("readOnly", true);
		form.addProperty("type", "form");
		formConfiguration = new JsonObject();
		formConfiguration.addProperty("type", "GET");
		formConfiguration.addProperty("onSubmit", "/table/formStyleTableJsonSchema/" + token + "/CAD_PARCEL");
		formUiSchema = new JsonObject();
		formUiSchema.addProperty("type", "GET");
		formUiSchema.addProperty("onSubmit", "/table/formStyleTableUiSchema/" + token + "/CAD_PARCEL");
		formData = new JsonObject();
		formData.addProperty("type", "GET");
		formData.addProperty("onSubmit", "/table/formStyleTableData/" + token + "/{objectId}/CAD_PARCEL");
		form.add("configuration", formConfiguration);
		form.add("uischema", formUiSchema);
		form.add("data", formData);
		objectConfigurationGrid.add("form", form);
		jsonMMitemGrid.add("objectConfiguration", objectConfigurationGrid);
		grids.add(jsonMMitemGrid);

		// add grid item "LAND_USE_PLAN" as second part of multi-grid that will
		// contain the crops on the parcel selected in CAD_PARCEL grid

		jsonMMitemGrid = new JsonObject();
		jsonMMitemGrid.addProperty("label", I18n.getText("land_use_plan.general"));
		jsonMMitemGrid.addProperty("ID", "LAND_USE_PLAN");
		objectConfigurationGrid = new JsonObject();
		objectConfigurationGrid.addProperty("enabled", true);
		objectConfigurationGrid.addProperty("readOnly", false);
		objectConfigurationGrid.addProperty("type", "grid");
		gridConfiguration = new JsonObject();
		gridConfiguration.addProperty("type", "GET");
		gridConfiguration.addProperty("onSubmit", "/table/tableFieldList/" + token + "/LAND_USE_PLAN");
		gridData = new JsonObject();
		gridData.addProperty("type", "GET");
		gridData.addProperty("onSubmit", "/farmer/getLandUseByCadParcel/" + token + "/{objectId}");
		objectConfigurationGrid.add("configuration", gridConfiguration);
		objectConfigurationGrid.add("data", gridData);

		// when clicking an row from grid, open details for that record
		form = new JsonObject();
		form.addProperty("enabled", true);
		form.addProperty("readOnly", false);
		form.addProperty("type", "form");
		formConfiguration = new JsonObject();
		formConfiguration.addProperty("type", "GET");
		formConfiguration.addProperty("onSubmit", "/table/formStyleTableJsonSchema/" + token + "/LAND_USE_PLAN");
		formUiSchema = new JsonObject();
		formUiSchema.addProperty("type", "GET");
		formUiSchema.addProperty("onSubmit", "/table/formStyleTableUiSchema/" + token + "/LAND_USE_PLAN");
		formData = new JsonObject();
		formData.addProperty("type", "GET");
		formData.addProperty("onSubmit",
				"/table/formStyleLandUsePlanData/" + token + "/{objectId}/LAND_USE_PLAN/{CAD_PARCEL.OBJECT_ID}");
		formSave = new JsonObject();
		formSave.addProperty("type", "POST");
		formSave.addProperty("onSave", "/table/createTableRecord/" + token
				+ "/LAND_USE_PLAN/{objectId}/{jsonString}/{CAD_PARCEL.OBJECT_ID}/CAD_PARCEL/LINK_LANDUSE_CAD");
		form.add("configuration", formConfiguration);
		form.add("uischema", formUiSchema);
		form.add("data", formData);
		form.add("save", formSave);
		objectConfigurationGrid.add("form", form);
		jsonMMitemGrid.add("objectConfiguration", objectConfigurationGrid);
		grids.add(jsonMMitemGrid);
		objectConfiguration.add("grids", grids);
		JsonObject statusObject0 = new JsonObject();
		statusObject0.addProperty("label", I18n.getText("land_use_plan.general"));
		statusObject0.addProperty("ID", "CREATE");

		statusObject0.add("objectConfiguration", objectConfiguration);
		mainArray.add(statusObject0);

		JsonObject statusObject1 = new JsonObject();
		statusObject1.addProperty("label", I18n.getText("land_use_plan.copy"));
		statusObject1.addProperty("ID", "COPY");
		statusObject1.addProperty("redirect", false);
		statusObject1.addProperty("onSubmit", "/farmer/copyLandUsePlan/" + token + "/{objectId}");
		JsonObject promptObject = new JsonObject();
		String messageString = I18n.getText("perun.menu.land_use_plan_copy.message");
		messageString = messageString.replace("{STATUS_CHANGE}", I18n.getText("land_use_plan.copy"));
		promptObject.addProperty("title", I18n.getText("perun.menu.land_use_plan.title"));
		promptObject.addProperty("message", messageString);
		promptObject.addProperty("buttonConfirm", I18n.getText("perun.menu.transition.button.confirm"));
		promptObject.addProperty("buttonCancel", I18n.getText("perun.menu.transition.button.cancel"));
		statusObject1.add("actionPrompt", promptObject);
		// if (isUserAdministrator(token))
		mainArray.add(statusObject1);

		JsonObject statusObject2 = new JsonObject();
		statusObject2.addProperty("label", I18n.getText("land_use_plan.delete"));
		statusObject2.addProperty("ID", "DELETE");
		statusObject2.addProperty("redirect", false);
		statusObject2.addProperty("onSubmit", "/farmer/deleteLandUsePlan/" + token + "/{objectId}");
		JsonObject promptObject1 = new JsonObject();
		String messageString1 = I18n.getText("perun.menu.land_use_plan_delete.message");
		messageString1 = messageString1.replace("{STATUS_CHANGE}", I18n.getText("land_use_plan.delete"));
		promptObject1.addProperty("title", I18n.getText("perun.menu.land_use_plan.title"));
		promptObject1.addProperty("message", messageString1);
		promptObject1.addProperty("buttonConfirm", I18n.getText("perun.menu.transition.button.confirm"));
		promptObject1.addProperty("buttonCancel", I18n.getText("perun.menu.transition.button.cancel"));
		statusObject2.add("actionPrompt", promptObject);
		// if (isUserAdministrator(token))
		mainArray.add(statusObject2);

		// svr = null;
		// try {
		// svr = new SvReader(token);
		// DbDataObject userObj = SvCore.getUserBySession(token);
		//
		// if (isUserInGroup(userObj, "ADMINISTRATORS", svr) ||
		// isUserInGroup(userObj, "ADM_CONTROL_USERS", svr)
		// || isUserInGroup(userObj, "READONLY_USERS", svr)) {

		//
		// // add items for all 4 application types
		// JsonObject lpisHistoryObject = new JsonObject();
		// lpisHistoryObject.addProperty("label", "2016");
		// lpisHistoryObject.addProperty("ID", "PPLAN_HISTORY_2016");
		// objectConfiguration = new JsonObject();
		// objectConfiguration.addProperty("enabled", true);
		// objectConfiguration.addProperty("readOnly", true);
		// objectConfiguration.addProperty("type", "grid");
		// gridConfiguration = new JsonObject();
		// gridConfiguration.addProperty("type", "GET");
		// gridConfiguration.addProperty("onSubmit",
		// "/gridReport/getReportFirldList/" + token + "/PPLAN_HISTORY/0/0");
		// gridData = new JsonObject();
		// gridData.addProperty("type", "GET");
		// gridData.addProperty("onSubmit", "/gridReport/getReport/" + token +
		// "/PPLAN_HISTORY/{objectId}/2016");
		// objectConfiguration.add("configuration", gridConfiguration);
		// objectConfiguration.add("data", gridData);
		// lpisHistoryObject.add("objectConfiguration", objectConfiguration);
		// mainArray.add(lpisHistoryObject);
		//
		// lpisHistoryObject = new JsonObject();
		// lpisHistoryObject.addProperty("label", "2017");
		// lpisHistoryObject.addProperty("ID", "PPLAN_HISTORY_2017");
		// objectConfiguration = new JsonObject();
		// objectConfiguration.addProperty("enabled", true);
		// objectConfiguration.addProperty("readOnly", true);
		// objectConfiguration.addProperty("type", "grid");
		// gridConfiguration = new JsonObject();
		// gridConfiguration.addProperty("type", "GET");
		// gridConfiguration.addProperty("onSubmit",
		// "/gridReport/getReportFirldList/" + token + "/PPLAN_HISTORY/0/0");
		// gridData = new JsonObject();
		// gridData.addProperty("type", "GET");
		// gridData.addProperty("onSubmit", "/gridReport/getReport/" + token +
		// "/PPLAN_HISTORY/{objectId}/2017");
		// objectConfiguration.add("configuration", gridConfiguration);
		// objectConfiguration.add("data", gridData);
		// lpisHistoryObject.add("objectConfiguration", objectConfiguration);
		// mainArray.add(lpisHistoryObject);
		//
		// lpisHistoryObject = new JsonObject();
		// lpisHistoryObject.addProperty("label", "2018");
		// lpisHistoryObject.addProperty("ID", "PPLAN_HISTORY_2018");
		// objectConfiguration = new JsonObject();
		// objectConfiguration.addProperty("enabled", true);
		// objectConfiguration.addProperty("readOnly", true);
		// objectConfiguration.addProperty("type", "grid");
		// gridConfiguration = new JsonObject();
		// gridConfiguration.addProperty("type", "GET");
		// gridConfiguration.addProperty("onSubmit",
		// "/gridReport/getReportFirldList/" + token + "/PPLAN_HISTORY/0/0");
		// gridData = new JsonObject();
		// gridData.addProperty("type", "GET");
		// gridData.addProperty("onSubmit", "/gridReport/getReport/" + token +
		// "/PPLAN_HISTORY/{objectId}/2018");
		// objectConfiguration.add("configuration", gridConfiguration);
		// objectConfiguration.add("data", gridData);
		// lpisHistoryObject.add("objectConfiguration", objectConfiguration);
		// mainArray.add(lpisHistoryObject);
		//
		// lpisHistoryObject = new JsonObject();
		// lpisHistoryObject.addProperty("label", "2019");
		// lpisHistoryObject.addProperty("ID", "PPLAN_HISTORY_2019");
		// objectConfiguration = new JsonObject();
		// objectConfiguration.addProperty("enabled", true);
		// objectConfiguration.addProperty("readOnly", true);
		// objectConfiguration.addProperty("type", "grid");
		// gridConfiguration = new JsonObject();
		// gridConfiguration.addProperty("type", "GET");
		// gridConfiguration.addProperty("onSubmit",
		// "/gridReport/getReportFirldList/" + token + "/PPLAN_HISTORY/0/0");
		// gridData = new JsonObject();
		// gridData.addProperty("type", "GET");
		// gridData.addProperty("onSubmit", "/gridReport/getReport/" + token +
		// "/PPLAN_HISTORY/{objectId}/2019");
		// objectConfiguration.add("configuration", gridConfiguration);
		// objectConfiguration.add("data", gridData);
		// lpisHistoryObject.add("objectConfiguration", objectConfiguration);
		// mainArray.add(lpisHistoryObject);

		// }

		// } catch (SvException e) {
		// // user not found, no admin console for him
		// } finally {
		// releseAll(svr);
		// }

		jsonMMitem.add("data", mainArray);
		arrMainMenuItems.add(jsonMMitem);

		// help and logout
		arrMainMenuItems.add(helpButton());
		arrMainMenuItems.add(logoutButton(token));

		jsonObj.add("MAIN_MENU", arrMainMenuItems);
		return jsonObj;
	}

	private JsonObject helpButton() {
		JsonObject jsonSM = new JsonObject();
		jsonSM.addProperty("label", I18n.getText("perun.menu.main.help"));
		jsonSM.addProperty("ID", "HELP");
		jsonSM.addProperty("onSubmit", "activateHelper");
		return jsonSM;
	}

	private JsonObject logoutButton(String token) {
		JsonObject jsonSM = new JsonObject();
		jsonSM.addProperty("label", I18n.getText("perun.menu.main.logout"));
		jsonSM.addProperty("ID", "LOGOUT");
		jsonSM.addProperty("onSubmit", "/SvSecurity/logout/" + token);
		return jsonSM;
	}

	/**
	 * method to create JsonObject for search for farmer menu form. it has 3 items
	 * drop-down with 4 choices of fields to search, input field to enter search
	 * keyword and a button that will call web service that do the search/ return
	 * results
	 * 
	 * @return JsonObject
	 */
	public JsonObject menuSearchForm(String token) {
		JsonObject jsonObj = new JsonObject();
		// grid element
		JsonObject jsonGrid = new JsonObject();
		jsonGrid.addProperty("type", "grid");
		jsonGrid.addProperty("route", "/farmer");
		JsonObject jsonConf = new JsonObject();
		jsonConf.addProperty("type", "GET");
		jsonConf.addProperty("onSubmit", "/farmer/searchFarmerFieldList/" + token);
		jsonGrid.add("configuration", jsonConf);
		JsonObject jsonData = new JsonObject();
		jsonData.addProperty("type", "GET");
		jsonData.addProperty("onSubmit", "/farmer/searchFarmer/" + token + "/{searchField}/{searchValue}");
		jsonGrid.add("data", jsonData);
		jsonObj.add("SEARCH_GRID", jsonGrid);

		// form element with 3 subelements: dropdown, text field and
		// button
		JsonObject jsonForm = new JsonObject();
		jsonForm.addProperty("onSubmit", "/farmer/searchFarmer");

		JsonArray arrSearchFormItems = new JsonArray();
		// drop-down sub element
		JsonObject menuItem = new JsonObject();
		JsonObject subItem = new JsonObject();

		JsonArray searchList = new JsonArray();

		// 4 items in the dropdown
		searchList.add(searchItemList("FIC", "FIC", I18n.getText("farmer.fic"), "FIC", 10, 11, 11));
		searchList.add(searchItemList("FULL_NAME", "FULL_NAME", I18n.getText("fname.sname"), "FULL_NAME", 20, 3, 0));
		searchList.add(searchItemList("ID_NO", "ID_NO", I18n.getText("farmer.id_no"), "ID_NO", 30, 13, 13));
		searchList.add(searchItemList("TAX_NO", "TAX_NO", I18n.getText("farmer.tax_no"), "TAX_NO", 40, 13, 13));

		// create the dropdown and add the dropdown items
		subItem.addProperty("id", "searchField");
		subItem.addProperty("order", 10);
		subItem.addProperty("name", "searchField");
		subItem.addProperty("type", "dropdown");
		subItem.add("list", searchList);
		subItem.addProperty("text", I18n.getText("some_dropdown_choice_label"));

		menuItem.add("select", subItem);
		arrSearchFormItems.add(menuItem);
		// input text subelement
		menuItem = new JsonObject();
		subItem = new JsonObject();

		subItem.addProperty("id", "searchValue");
		subItem.addProperty("order", 20);
		subItem.addProperty("name", "searchValue");
		subItem.addProperty("type", "text");
		subItem.addProperty("text", I18n.getText("some_input_label"));

		menuItem.add("input", subItem);
		arrSearchFormItems.add(menuItem);

		// search button subelement
		menuItem = new JsonObject();
		subItem = new JsonObject();

		subItem.addProperty("id", "searchSubmit");
		subItem.addProperty("order", 30);
		subItem.addProperty("type", "submit");
		subItem.addProperty("text", I18n.getText("search"));

		menuItem.add("button", subItem);
		arrSearchFormItems.add(menuItem);
		jsonForm.add("elements", arrSearchFormItems);
		jsonObj.add("SEARCH_FORM", jsonForm);
		return jsonObj;

	}

	private JsonObject searchItemList(String id, String name, String displayText, String dropdownValue, int order,
			int minlength, int maxlength) {
		JsonObject searchItem = new JsonObject();
		searchItem.addProperty("id", id);
		searchItem.addProperty("name", name);
		searchItem.addProperty("text", displayText);
		searchItem.addProperty("value", dropdownValue);
		if (minlength > 0)
			searchItem.addProperty("minlength", minlength);
		if (maxlength > 0)
			searchItem.addProperty("maxlength", maxlength);
		if (order > 0)
			searchItem.addProperty("order", order);
		return searchItem;
	}

	public JsonObject menuModule(SvReader svr) {
		String token = svr.getSessionId();
		DbDataObject userDbo = svr.getInstanceUser();
		JsonObject jsonObj = new JsonObject();
		JsonArray jsonArr = new JsonArray();
		// search for farmer
		JsonObject jObjectSearch = new JsonObject();
		JsonObject objConfSearch = new JsonObject();
		JsonObject confSearch = new JsonObject();
		WsSecurityActions wsc = new WsSecurityActions();

		jObjectSearch.addProperty("label", I18n.getText("perun.menu.search"));
		jObjectSearch.addProperty("ID", "search");
		objConfSearch.addProperty("enabled", true);
		objConfSearch.addProperty("type", "submenu");
		confSearch.addProperty("type", "GET");
		confSearch.addProperty("onSubmit", "/configuration/getConfiguration/" + token + "/SEARCH_FORM/");
		objConfSearch.add("configuration", confSearch);
		jObjectSearch.add("objectConfiguration", objConfSearch);
		jsonArr.add(jObjectSearch);

		// // search for application
		// jObjectSearch = new JsonObject();
		// objConfSearch = new JsonObject();
		// confSearch = new JsonObject();
		// jObjectSearch.addProperty("label",
		// I18n.getText("perun.menu.search_app"));
		// jObjectSearch.addProperty("ID", "search_app");
		// objConfSearch.addProperty("enabled", true);
		// objConfSearch.addProperty("type", "submenu");
		// confSearch.addProperty("type", "GET");
		// confSearch.addProperty("onSubmit", "/configuration/getConfiguration/"
		// + token + "/SEARCH_APPLICATION/");
		// objConfSearch.add("configuration", confSearch);
		// jObjectSearch.add("objectConfiguration", objConfSearch);
		// // if (isUserAdministrator(token))
		// jsonArr.add(jObjectSearch);

		// admin console
		if (wsc.isUserAdministrator(token) || wsc.isUserInGroup(userDbo, "ADVANCED_ADM_CTRL_USERS", svr)) {
			JsonObject jObject = new JsonObject();
			JsonObject objConf = new JsonObject();
			JsonObject conf = new JsonObject();
			jObject.addProperty("label", I18n.getText("perun.menu.console"));
			jObject.addProperty("ID", "console");
			objConf.addProperty("enabled", true);
			objConf.addProperty("type", "submenu");
			conf.addProperty("type", "GET");
			conf.addProperty("onSubmit", "/configuration/getConfiguration/" + token + "/ADMIN_CONSOLE_MENU_TOP/");
			objConf.add("configuration", conf);
			jObject.add("objectConfiguration", objConf);
			jsonArr.add(jObject);
		}

		if (wsc.isUserAdministrator(token) || wsc.isUserInGroup(userDbo, "SVAROG_REPORTS_USERS", svr)) {
			JsonObject jObject = new JsonObject();
			JsonObject objConf = new JsonObject();
			JsonObject conf = new JsonObject();
			jObject.addProperty("label", I18n.getText("perun.menu.reports"));
			jObject.addProperty("ID", "reports");
			objConf.addProperty("enabled", true);
			objConf.addProperty("type", "submenu");
			conf.addProperty("type", "GET");
			conf.addProperty("onSubmit", "/configuration/getConfiguration/" + token + "/REPORTS_MENU_TOP/");
			objConf.add("configuration", conf);
			jObject.add("objectConfiguration", objConf);
			jsonArr.add(jObject);
		}

		jsonObj.add("MODULE_MENU", jsonArr);
		return jsonObj;
	}

	public JsonObject loginIacsUser(String username, String password) throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		SvSecurity svsec = null;
		SvReader svr = null;
		DbDataObject dboUserG = null;
		DbDataObject dbUser = null;
		try {
			svsec = new SvSecurity();
			dboUserG = new DbDataObject();
			if (!(username == null || password == null)) {
				String token = svsec.logon(username.toUpperCase(), password.toUpperCase());
				svr = new SvReader(token);
				// SvarogLogin.systemUnderMaintenanceCheck(svr);
				/* add token for login */
				JsonObject combo = new JsonObject();
				combo.addProperty("token", token);

				dboUserG = svr.getDefaultUserGroup();
				if (dboUserG != null && dboUserG.getVal("GROUP_NAME").equals("USERS")) {
					dbUser = svr.getInstanceUser();
					JsonObject takeUserData = searchPerson(svr, dbUser);
					String resType = "";
					if (takeUserData.has("type"))
						resType = takeUserData.get("type").getAsString();
					if (resType.equalsIgnoreCase("ERROR") || resType.equalsIgnoreCase("EXCEPTION"))
						jrh.create(resType, takeUserData.get("title").getAsString(),
								takeUserData.get("message").getAsString(), new JsonObject());
					else {
						JsonArray userJsonData = new JsonArray();
						if (takeUserData.has("data")) {
							JsonElement responseData = takeUserData.get("data");
							if (responseData.isJsonArray())
								userJsonData = (JsonArray) responseData;
						}

						if (svr.isAdmin()) {
							combo.addProperty("redirect", "/MODULE_MENU");
							jrh.create(MessageType.SUCCESS, I18n.getText("login.success"),
									I18n.getText("login.success"), combo);
						} else {
							if (userJsonData.size() > 0) {
								JsonObject jsonObj = null;

								for (int i = 0; i < userJsonData.size(); i++) {
									jsonObj = (JsonObject) userJsonData.get(i);

									if (jsonObj.has("PERSON.OBJECT_ID"))
										combo.add("person", jsonObj);

									if (jsonObj.has("FARMER.OBJECT_ID"))
										combo.add("farm", jsonObj);

									if (jsonObj.has("IPARD_APPLICANT.OBJECT_ID"))
										combo.add("ipard", jsonObj);
								}

								jrh.create(MessageType.SUCCESS, I18n.getText("login.success"),
										I18n.getText("login.success"), combo);
							} else {
								jrh.create(MessageType.ERROR, I18n.getText("login.missingInfo"),
										I18n.getText("login.login.missingInfo"), new JsonObject());
							}
						}
					}
				} else {
					jrh.create(MessageType.SUCCESS, I18n.getText("login.success"), I18n.getText("login.success"),
							combo);
				}
			}
		} catch (SvException e) {
			throw (e);
		} finally {
			if (svsec != null) {
				svsec.release();
				svsec.close();
			}
			if (svr != null) {
				svr.release();
				svr.close();
			}
		}
		return jrh.getAll();
	}

	public JsonObject searchPerson(SvReader svr, DbDataObject dbUser) {
		ResponseHandler jrh = new ResponseHandler();
		JsonArray finalJsonArray = new JsonArray();
		SvSecurity svc = null;
		try {
			if (dbUser != null) {
				svc = new SvSecurity();
				Gson gson = new Gson();
				DbDataArray personData = svc.getPOAObjects(dbUser.getObjectId(), "PERSON");
				// SvCore.getTypeIdByName("PERSON"), null);
				if (personData != null && personData.size() > 0) {
					DbSearchCriterion crit2 = new DbSearchCriterion("PERSON_ID", DbCompareOperand.EQUAL,
							personData.get(0).getObjectId());
					int tablesusedCount = 1;
					DbDataArray ipardAplicantData = new DbDataArray();
					DbDataArray farmData = svc.getPOAObjects(dbUser.getObjectId(), "FARMER");
					/* remove extra created obj/person f.r */

					Iterator<DbDataObject> itp = personData.getItems().iterator();
					while (itp.hasNext()) {
						DbDataObject dbp = itp.next();
						Iterator<DbDataObject> itf = farmData.getItems().iterator();
						while (itf.hasNext()) {
							DbDataObject dbf = itf.next();
							if (!dbp.getObjectId().equals(dbf.getParentId())) {
								itp.remove();
							}
						}
					}

					try {
						if (SvCore.getTypeIdByName("IPARD_APPLICANT") != null) {
							ipardAplicantData = svr.getObjects(crit2, SvCore.getTypeIdByName("IPARD_APPLICANT"), null,
									0, 0);
						}
					} catch (Exception e) {
						if (log4j.isDebugEnabled())
							log4j.debug("Table IPARD_APPLICANT is missing", e);
					}

					String[] tablesUsedArray = new String[tablesusedCount];
					Boolean[] tableShowArray = new Boolean[tablesusedCount];
					tablesUsedArray[0] = ("PERSON");
					tableShowArray[0] = true;

					String jsonStr = WsReactElements.prapareTableQueryData(personData, tablesUsedArray, tableShowArray,
							tablesusedCount, true, svr);
					JsonArray tmpJson = gson.fromJson(jsonStr, JsonArray.class);
					finalJsonArray.add(tmpJson.get(0));
					tablesUsedArray[0] = ("FARMER");
					tableShowArray[0] = true;
					jsonStr = WsReactElements.prapareTableQueryData(farmData, tablesUsedArray, tableShowArray,
							tablesusedCount, true, svr);
					tmpJson = gson.fromJson(jsonStr, JsonArray.class);

					if (tmpJson.size() > 0)
						finalJsonArray.add(tmpJson.get(0));

					tablesUsedArray[0] = ("IPARD_APPLICANT");
					tableShowArray[0] = true;

					jsonStr = WsReactElements.prapareTableQueryData(ipardAplicantData, tablesUsedArray, tableShowArray,
							tablesusedCount, true, svr);
					System.out.println();
					tmpJson = gson.fromJson(jsonStr, JsonArray.class);

					if (tmpJson.size() > 0)
						finalJsonArray.add(tmpJson.get(0));

					jrh.create(MessageType.SUCCESS, I18n.getText("farmer.data.found"), I18n.getText("data.read"),
							finalJsonArray);
				} else {
					jrh.create(MessageType.ERROR, I18n.getText("error.noPersonFound"),
							I18n.getText("error.noPersonFound.data"), finalJsonArray);
				}
			}
		} catch (

		JsonSyntaxException e) {
			jrh.create(MessageType.ERROR, I18n.getText("error.loading.person.data"),
					I18n.getText("error.loading.person.data"), finalJsonArray);
		} catch (SvException e) {
			jrh = ResponseHandler.responseHandlerByException(e);
		} finally {
			if (svr != null) {
				svr.release();
				svr.close();
			}
			if (svc != null) {
				svc.release();
				svc.close();
			}
		}
		return jrh.getAll();
	}

	/* BLOCK END */
}
