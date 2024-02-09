package com.prtech.perun.services.ws;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.prtech.perun.PerunUtil;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvExecManager;
import com.prtech.svarog.SvParameter;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvWriter;
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
import com.prtech.svarog_interfaces.ISvCore;
import com.prtech.svarog_interfaces.ISvExecutorGroup;

public class BusinessLogicExecutors implements ISvExecutorGroup {

	private static final Logger log4j = LogManager.getLogger(SvCore.class.getName());
	static HashMap<String, Object[]> passRecoveryTokens = new HashMap<String, Object[]>();

	private static final String REGISTER_USER = "REGISTER_USER";

	private static final String PERUN_CORE_EXEC = "PERUN_CORE_EXEC";
	private static final String LOGIN_USER = "LOGIN_USER";
	private static final String ACTIVATION_LINK = "ACTIVATION_LINK";
	private static final String PASSWORD_RECOVERY = "PASSWORD_RECOVERY";
	private static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
	private static final String EMAIL_CHANGE = "EMAIL_CHANGE";
	private static final String ACTIVATE_USER = "ACTIVATE_USER";
	private static final String LOGOFF_USER = "LOGOFF_USER";
	private static final String DEFAULT_NAME = "FARMER";

	@Override
	public long versionUID() {
		// TODO Auto-generated method stub
		return 1;
	}

	@Override
	public Map<String, Class<?>> getReturningTypes() {
		// TODO Auto-generated method stub
		Map<String, Class<?>> types = new HashMap<String, Class<?>>();
		types.put(REGISTER_USER, JsonObject.class);
		types.put(LOGIN_USER, JsonObject.class);
		types.put(ACTIVATION_LINK, JsonObject.class);
		types.put(PASSWORD_RECOVERY, JsonObject.class);
		types.put(PASSWORD_CHANGE, JsonObject.class);
		types.put(EMAIL_CHANGE, JsonObject.class);
		types.put(ACTIVATE_USER, JsonObject.class);
		types.put(LOGOFF_USER, JsonObject.class);
		return types;
	}

	@Override
	public String getCategory() {
		// TODO Auto-generated method stub
		return PERUN_CORE_EXEC;
	}

	@Override
	public List<String> getNames() {
		List<String> names = new ArrayList<>();
		names.add(REGISTER_USER);
		names.add(LOGIN_USER);
		names.add(ACTIVATION_LINK);
		names.add(PASSWORD_RECOVERY);
		names.add(PASSWORD_CHANGE);
		names.add(EMAIL_CHANGE);
		names.add(ACTIVATE_USER);
		names.add(LOGOFF_USER);
		return names;
	}

	@Override
	public Map<String, String> getDescriptions() {
		// TODO Auto-generated method stub
		Map<String, String> types = new HashMap<String, String>();
		types.put(REGISTER_USER, "Method for default user registration, compatible with IACS Farmers");
		types.put(LOGIN_USER, "Method for default user logon to the IACS");
		types.put(ACTIVATION_LINK, "Method for default mail with activation link");
		types.put(PASSWORD_RECOVERY, "Method for password recovery");
		types.put(PASSWORD_CHANGE, "Method for changing password after a recovery link has been sent");
		types.put(EMAIL_CHANGE, "Method for changing e-mail with a PIN");
		types.put(ACTIVATE_USER, "User activation method, by using an email address");
		types.put(LOGOFF_USER, "Method to logoff an user");
		return types;
	}

	@Override
	public DateTime getStartDate() {
		// TODO Auto-generated method stub
		return new DateTime();
	}

	@Override
	public DateTime getEndDate() {
		// TODO Auto-generated method stub
		return SvConf.MAX_DATE;
	}

	@Override
	public Object execute(String name, Map<String, Object> params, ISvCore svCore) throws SvException {
		// TODO Auto-generated method stub
		Object returnValue = null;
		JsonObject jparams = (JsonObject) params.get("json_params");
		switch (name) {
		case REGISTER_USER:
			returnValue = registerUser(jparams, (SvCore) svCore); // farmer, fic, idNo, email, password1, httpRequest);
			break;
		case LOGIN_USER:
			returnValue = loginIacsUser(jparams, (SvCore) svCore);
			break;
		case ACTIVATION_LINK:
			returnValue = activationLink(jparams, (SvCore) svCore);
			break;
		case PASSWORD_RECOVERY:
			returnValue = recoverPassword(jparams, (SvCore) svCore);
			break;
		case PASSWORD_CHANGE:
			returnValue = changePassword(jparams, (SvCore) svCore);
			break;
		case EMAIL_CHANGE:
			returnValue = changeEmail(jparams, (SvCore) svCore);
			break;
		case ACTIVATE_USER:
			returnValue = recoverPassword(jparams, (SvCore) svCore);
			break;
		case LOGOFF_USER:
			try (SvSecurity svsec = new SvSecurity((SvCore) svCore)) {
				if (jparams.get("token") != null)
					svsec.logoff(jparams.get("token").getAsString());
				returnValue = new JsonObject();
			}
			break;

		default:
			break;
		}
		return returnValue;
	}

	public JsonObject activateUser(JsonObject jobj, SvCore svc) throws SvException {
		JsonObject retval = new JsonObject();
		String userUid = "";
		if (jobj.get("uuid") != null) {
			userUid = jobj.get("uuid").getAsString();
			try (SvSecurity svs = new SvSecurity(svc)) {
				svs.activateExternalUser(userUid);
			}
		} else
			throw new SvException(("user.missing_activation_token"), svc.getInstanceUser());
		return retval;

	}

	public JsonObject changePassword(JsonObject jobj, SvCore svc) throws SvException {
		cleanUpRecoveryData();
		JsonObject jso = new JsonObject();
		String recoverToken = "";
		String fic = "";
		String idNo = "";
		String newPass = "";
		String repNewPass = "";
		if (jobj.get("username") != null)
			fic = jobj.get("username").getAsString();
		if (jobj.get("recoverToken") != null)
			recoverToken = jobj.get("recoverToken").getAsString();
		if (jobj.get("idNo") != null)
			idNo = jobj.get("idNo").getAsString();
		if (jobj.get("password") != null)
			newPass = jobj.get("password").getAsString();
		if (jobj.get("repeatPassword") != null)
			repNewPass = jobj.get("repeatPassword").getAsString();
		Object[] recoveryData = passRecoveryTokens.get(fic);
		if (recoverToken != "" && fic != "" && newPass != "" && recoveryData != null
				&& recoverToken.equals(recoveryData[0]) && newPass.equals(repNewPass)) {
			jso = changePasswordImpl(fic, idNo, newPass, svc);
		}
		return jso;
	}

	/**
	 * method to change users password, we just need user-name, birth number and
	 * what the new password will be
	 * 
	 * @param fic     String user-name: Farmer identification code, and whatever we
	 *                use for agency employees
	 * @param idNo    String farmer birth number or company tax number
	 * @param newPass String new password that was entered by the user
	 * 
	 * @return String message if password was changed or not
	 * @throws SvException
	 */
	private JsonObject changePasswordImpl(String fic, String idNo, String newPass, SvCore svc) throws SvException {
		try (SvSecurity svs = new SvSecurity(svc)) {

			DbDataObject dboUser = svs.getUser(fic.toUpperCase());
			if (dboUser == null)
				throw new SvException(("user.farmer_notFound"), svc.getInstanceUser());
			else {
				Boolean validPerson = false;
				String vUserType = dboUser.getVal("USER_TYPE").toString();
				if ("EXTERNAL".equalsIgnoreCase(vUserType)) {
					validPerson = hasValidFarmers(dboUser, fic.toUpperCase(), idNo, svs);
					if (!validPerson) {
						throw new SvException("system.farmer_id_not_matched", svc.getInstanceUser());
					} else {
						svs.createUser((String) dboUser.getVal("USER_NAME"), newPass.toUpperCase(),
								(String) dboUser.getVal("FIRST_NAME"), (String) dboUser.getVal("LAST_NAME"),
								(String) dboUser.getVal("E_MAIL"), (String) dboUser.getVal("PIN"),
								(String) dboUser.getVal("TAX_ID"), (String) dboUser.getVal("USER_TYPE"), null, true);
						// jrh.create(MessageType.SUCCESS,
						// I18n.getText("system.password_changed_success"), " ");
					}
				}

				if ("INTERNAL".equalsIgnoreCase(vUserType) || "ADM".equalsIgnoreCase(vUserType)
						|| "ARCHIVE".equalsIgnoreCase(vUserType) || "CONTROL".equalsIgnoreCase(vUserType)
						|| "BATCH".equalsIgnoreCase(vUserType)) {
					svs.createUser((String) dboUser.getVal("USER_NAME"), newPass.toUpperCase(),
							(String) dboUser.getVal("FIRST_NAME"), (String) dboUser.getVal("LAST_NAME"),
							(String) dboUser.getVal("E_MAIL"), (String) dboUser.getVal("PIN"),
							(String) dboUser.getVal("TAX_ID"), (String) dboUser.getVal("USER_TYPE"), null, true);
					// jrh.create(MessageType.SUCCESS,
					// I18n.getText("system.password_changed_success"), " ");
				}
			}
		}
		return new JsonObject();
	}
	/* BLOCK END */

	private JsonObject recoverPassword(JsonObject jobj, SvCore svc) throws SvException {

		String fic = "", feHost = "";
		if (jobj.get("username") != null)
			fic = jobj.get("username").getAsString();
		if (jobj.get("feHost") != null)
			feHost = jobj.get("feHost").getAsString();
		if (fic != null && fic != "") {
			String recoveryToken = UUID.randomUUID().toString();
			Object[] recoveryData = new Object[2];
			recoveryData[0] = recoveryData[1] = new DateTime();
			passRecoveryTokens.put(fic, recoveryData);

			try (SvSecurity svsec = new SvSecurity(svc)) {
				DbDataObject dboUser = svsec.getUser(fic.toUpperCase());

				if (dboUser != null) {
					sendPassRecoveryEmail(dboUser, recoveryToken, feHost);
				} else
					throw new SvException(("user.farmer_notFound"), svc.getInstanceUser());
			}
		} else
			throw new SvException(("user.farmer_notFound"), svc.getInstanceUser());
		return new JsonObject();
	}

	/**
	 * method to generate data for password recovery e-mail and then call the actual
	 * send procedure. link is different if user is external (farmer, company) or
	 * internal ( agency workers), since externals need to confirm identity with
	 * birth number or company tax-id number
	 * 
	 * @param dboUser      DbDataObject user that we are processing
	 * @param recoverToken String token that exist only x minutes (10) and can be
	 *                     used to change forgotten password
	 * @param feHost       String front end host-name generated from calling the web
	 *                     service
	 * 
	 * @return Boolean true if mail was send with no errors
	 * 
	 * @throws SvException
	 */
	private boolean sendPassRecoveryEmail(DbDataObject dboUser, String recoverToken, String feHost) throws SvException {
		String mailBody = I18n.getLongText("mail.body_pass_recovery");
		String uri = feHost + "#/home/change_pass?username=" + dboUser.getVal("USER_NAME") + "&recoveryToken="
				+ recoverToken;
		// if user is internal send flag for internal.jsp in gui no embg needed
		String vUserType = dboUser.getVal("USER_TYPE").toString();
		if ("INTERNAL".equalsIgnoreCase(vUserType) || "ADM".equalsIgnoreCase(vUserType)
				|| "ARCHIVE".equalsIgnoreCase(vUserType) || "CONTROL".equalsIgnoreCase(vUserType)
				|| "BATCH".equalsIgnoreCase(vUserType))
			uri = feHost + "#/home/change_pass_internal?username=" + dboUser.getVal("USER_NAME") + "&recoveryToken="
					+ recoverToken;
		HashMap<String, String> extParams = new HashMap<>();
		extParams.put("{NAME}", dboUser.getVal("FIRST_NAME")
				+ (dboUser.getVal("LAST_NAME") != null ? " " + dboUser.getVal("LAST_NAME") : ""));
		extParams.put("{RECOVERY_TOKEN}", uri);
		PerunUtil.sendMail((String) dboUser.getVal("E_MAIL"), I18n.getText("mail.subject_revocerpass"), mailBody,
				extParams);

		return true;
	}

	private JsonObject registerUser(JsonObject jobj, SvCore svc) throws SvException {
		// TODO Auto-generated method stub
		String password1 = null;
		String password2 = null;
		String email = "";
		String fic = "";
		String idNo = "";
		String feHost = "";
		boolean farmer = false;
		if (jobj.get("eMail") != null)
			email = jobj.get("eMail").getAsString();
		if (jobj.get("password") != null)
			password1 = jobj.get("password").getAsString();
		if (jobj.get("repeatPassword") != null)
			password2 = jobj.get("repeatPassword").getAsString();
		if (jobj.get("username") != null)
			fic = jobj.get("username").getAsString();
		if (jobj.get("idNo") != null)
			idNo = jobj.get("idNo").getAsString();
		if (jobj.get("farmer") != null)
			farmer = jobj.get("farmer").getAsBoolean();
		if (jobj.get("feHost") != null)
			feHost = jobj.get("feHost").getAsString();
		if (PerunUtil.isValidEmailAddress(email) && password1 != "" && fic != "" && password1.equals(password2)) {
			return registerUser(farmer, fic, idNo, email, password1, feHost, svc);

		}

		return null;
	}

	/*
	 * BLOCK START BL for current active project EDBAR f.r
	 */
	private JsonObject changeEmailImpl(String fic, String idNo, String newEmail, String feHost, SvCore svc)
			throws SvException {
		DbDataObject dboUser = null;
		Boolean userFound = false;
		try (SvSecurity svs = new SvSecurity(svc)) {

			dboUser = svs.getUser(fic.toUpperCase());
			if (dboUser != null) {
				if (dboUser.getVal("PIN").equals(idNo)) {
					userFound = true;
				} else {
					String businessObjectName = SvParameter.getSysParam("BUSINESS_OBJECT_NAME", DEFAULT_NAME);
					DbSearchCriterion critU = new DbSearchCriterion("FIC", DbCompareOperand.EQUAL, fic);
					if (svs.checkIfExistsConditional(businessObjectName, critU, "ID_NO", idNo)
							|| svs.checkIfExistsConditional(businessObjectName, critU, "TAX_NO", idNo))
						userFound = true;
				}
			}
			if (userFound) {
				svs.createUser((String) dboUser.getVal("USER_NAME"), null, (String) dboUser.getVal("FIRST_NAME"),
						(String) dboUser.getVal("LAST_NAME"), newEmail, (String) dboUser.getVal("PIN"),
						(String) dboUser.getVal("TAX_ID"), (String) dboUser.getVal("USER_TYPE"), "PENDING", true);
				dboUser.setVal("E_MAIL", newEmail);
				sendActivationEmail(dboUser, feHost);
			}
		}
		return new JsonObject();
	}

	public JsonObject noRestrictionUser(MultivaluedMap<String, String> formVals, SvSecurity svs) {
		JsonObject jbo = null;
		ResponseHandler jrh = new ResponseHandler();
		try {
			String firstName = " ";
			String lastName = " ";
			String password1 = null;
			String password2 = null;
			String email = "";
			String userName = "";
			String idNo = "";
			String taxId = "";
			if (formVals != null) {
				for (Entry<String, List<String>> entry : formVals.entrySet()) {
					if (entry.getKey() != null && !entry.getKey().isEmpty()) {
						String key = entry.getKey();
						JsonObject jobj = new JsonObject();
						Gson gs = new Gson();
						jobj = gs.fromJson(key, JsonObject.class);
						if (jobj.get("eMail") != null)
							email = jobj.get("eMail").getAsString();
						if (jobj.get("password") != null)
							password1 = jobj.get("password").getAsString();
						if (jobj.get("repeatPassword") != null)
							password2 = jobj.get("repeatPassword").getAsString();
						if (jobj.get("username") != null)
							userName = jobj.get("username").getAsString();
						if (jobj.get("idNo") != null)
							idNo = jobj.get("idNo").getAsString();
					}
				}
				if (password1 != "" && userName != "" && password1.equals(password2)) {
					DbDataObject dboUser = svs.createUser(userName.toUpperCase(), password1.toUpperCase(),
							firstName.toUpperCase(), lastName.toUpperCase(), email, idNo.toUpperCase(),
							taxId.toUpperCase(), "EXTERNAL", "VALID");
					if (dboUser != null)
						try (SvWriter svw = new SvWriter(svs)) {
							jrh.create(MessageType.SUCCESS, I18n.getText("user.created"), I18n.getText("user.created"),
									new JsonObject());
							dboUser.setStatus("VALID");
							svw.saveObject(dboUser);

						}

				}
			}
			jbo = jrh.getAll();
		} catch (Exception e) {
			jrh.create(MessageType.EXCEPTION, I18n.getText("error.creating user"),
					" agriPluginManager.PluginLogin " + e.getMessage(), new JsonObject());
			jbo = jrh.getAll();
		}
		return jbo;
	}

	DbSearchExpression getBusinessSubjectDbSearch(String userName, String pinVat) throws SvException {
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

	boolean verifyValidBusinessSubject(String fic, DbSearchExpression getBusiness) throws SvException {
		boolean userExists = false;
		try (SvSecurity svs = new SvSecurity()) {
			String businessObjectName = SvParameter.getSysParam("BUSINESS_OBJECT_NAME", DEFAULT_NAME);
			userExists = svs.checkIfExists(businessObjectName, getBusiness);
			long lf = Long.parseLong(fic);
			if (!userExists) {
				String importExecName = SvParameter.getSysParam("BUSINESS_OBJECT_IMPORT_CUSTOM", "NO_CUSTOM_EXEC");
				svs.switchUser(svCONST.serviceUser);
				try (SvExecManager svx = new SvExecManager(svs)) {
					Map<String, Object> params = new HashMap<String, Object>();
					params.put("FIC", fic);
					JsonObject configuration = (JsonObject) svx.execute(importExecName, params, null);
				}
				userExists = svs.checkIfExists(businessObjectName, getBusiness);
			}

		} catch (NumberFormatException ex) {
			userExists = false;
		}
		return userExists;
	}

	/**
	 * method to register user, we accept the token as parameter, remove it from the
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
	 * @throws SvException
	 */
	public JsonObject registerUser(Boolean isBusiness, String userName, String pinVat, String eMail, String password,
			String feHost, SvCore svc) throws SvException {
		if ((userName == null || password == null || eMail == null || pinVat == null || isBusiness == null))
			throw new SvException("perun.error.null_params", svc.getInstanceUser());

		ResponseHandler jrh = new ResponseHandler();
		String firstName = " ";
		String lastName = " ";
		Boolean businessExists = false;
		Boolean matchingUserNamePinVat = false;
		// Secondary ID of the user. Tax ID if legal entity.
		String taxId = "";
		{
			DbDataObject dboUser = null;
			try (SvSecurity svs = new SvSecurity(svc);) {
				// check if the user is registered in the business register
				DbSearchExpression getPerson = getPersonSearch(pinVat);
				// refactor this
				String businessObjectName = SvParameter.getSysParam("BUSINESS_OBJECT_NAME", DEFAULT_NAME);
				// get the farmer table
				// check if the user is registered in the business register
				DbSearchExpression getFarmer = getBusinessSubjectDbSearch(userName, pinVat);
				businessExists = verifyValidBusinessSubject(userName, getFarmer);
				matchingUserNamePinVat = svs.checkIfExistsConditional(businessObjectName, getFarmer, "FIC",
						userName.toUpperCase());
				String labelCode = "user." + businessObjectName.toLowerCase() + "_notFound";
				if (isBusiness) {
					if (businessExists && matchingUserNamePinVat) {
						dboUser = svs.createUser(userName.toUpperCase(), password.toUpperCase(),
								firstName.toUpperCase(), lastName.toUpperCase(), eMail, pinVat.toUpperCase(),
								taxId.toUpperCase(), "EXTERNAL", "PENDING");
					} else {
						return jrh.create(MessageType.ERROR, I18n.getText(labelCode), I18n.getText(labelCode),
								new JsonObject());
					}
				} else {
					dboUser = svs.createUser(userName.toUpperCase(), password.toUpperCase(), firstName.toUpperCase(),
							lastName.toUpperCase(), eMail, pinVat.toUpperCase(), taxId.toUpperCase(), "EXTERNAL",
							"PENDING");
				}

				if (dboUser != null) {
					jrh.create(MessageType.SUCCESS, I18n.getText("user.user1_created"),
							I18n.getText("user.created.activation"), new JsonObject());
					try {
						svs.empowerUser(dboUser, "PERSON", getPerson);
					} catch (Exception e) {
						if (!(e instanceof IndexOutOfBoundsException))
							log4j.error("Error empowering user:" + getPerson.toSimpleJson(), e);
					}

					try {
						svs.empowerUser(dboUser, businessObjectName, getFarmer);
					} catch (Exception e) {
						if (!(e instanceof IndexOutOfBoundsException))
							log4j.error("Error empowering user:" + getFarmer.toSimpleJson(), e);
					}

					// check if it h
					DbDataArray empowerments = svs.getPOAObjects(dboUser.getObjectId(), "PERSON");
					if (!empowerments.getItems().isEmpty() && empowerments.getItems().get(0) != null) {
						DbDataObject personObj = empowerments.getItems().get(0);
						if (personObj.getVal("NAME") != null)
							firstName = personObj.getVal("NAME").toString();
					}

					// call the standard SvSecurity create user.
					svs.createUser((String) dboUser.getVal("USER_NAME"), (String) password.toUpperCase(), firstName,
							lastName, (String) dboUser.getVal("E_MAIL"), (String) dboUser.getVal("PIN"),
							(String) dboUser.getVal("TAX_ID"), (String) dboUser.getVal("USER_TYPE"), "PENDING", true);
					sendActivationEmail(dboUser, feHost);
				} else {
					jrh.create(MessageType.ERROR, I18n.getText("user.notCreated"), I18n.getText("user.notCreated"),
							new JsonObject());
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

	public JsonObject activationLink(JsonObject jobj, SvCore svCore) throws SvException {
		String username = "";
		String password = null;
		String edbr = "";
		String feHost = "";
		JsonObject retval = new JsonObject();
		DbDataObject dbo = new DbDataObject();
		// BusinessLogicWS bls = new BusinessLogicWS();
		if (jobj.get("username") != null)
			username = jobj.get("username").getAsString();
		if (jobj.get("idNo") != null)
			edbr = jobj.get("idNo").getAsString();
		if (jobj.get("password") != null)
			password = jobj.get("password").getAsString();
		if (jobj.get("feHost") != null)
			feHost = jobj.get("feHost").getAsString();
		password = password.toUpperCase();
		try (SvSecurity svs = new SvSecurity(svCore)) {
			dbo = svs.getUser(username);
			if (dbo != null && dbo.getVal("PIN").equals(edbr) && dbo.getStatus().equals("PENDING")) {
				sendActivationEmail(dbo, feHost);
			} else {
				throw new SvException(("user.farmer_notFound"), svCore.getInstanceUser());
			}
		}
		return retval;
	}

	public JsonObject changeEmail(JsonObject jobj, SvCore svc) throws SvException {
		cleanUpRecoveryData();
		JsonObject jso = new JsonObject();
		String fic = "";
		String idNo = "";
		String email = "";
		String feHost = "";

		if (jobj.get("username") != null)
			fic = jobj.get("username").getAsString();
		if (jobj.get("idNo") != null)
			idNo = jobj.get("idNo").getAsString();
		if (jobj.get("eMail") != null)
			email = jobj.get("eMail").getAsString();
		if (jobj.get("feHost") != null)
			feHost = jobj.get("feHost").getAsString();
		if (fic != "" && idNo != "" && email != "") {
			jso = changeEmailImpl(fic, idNo, email, feHost, svc);
		}
		return jso;

	}

	private boolean sendActivationEmail(DbDataObject dboUser, String feHost) throws SvException {
		String mailBody = I18n.getLongText("mail.body_activation");

		try (SvSecurity svs = new SvSecurity()) {
			WsSecurityActions wsc = new WsSecurityActions();

			String uri = feHost + "#/home/activate?uuid=" + dboUser.getVal("USER_UID");
			HashMap<String, String> extParams = new HashMap<String, String>();
			extParams.put("{NAME}", dboUser.getVal("FIRST_NAME")
					+ (dboUser.getVal("LAST_NAME") != null ? " " + dboUser.getVal("LAST_NAME") : ""));
			extParams.put("{ACTIVATION_LINK}", uri);

			PerunUtil.sendMail((String) dboUser.getVal("E_MAIL"), I18n.getText("mail.subject_activation"), mailBody,
					extParams);
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
								String businessObjectName = SvParameter.getSysParam("BUSINESS_OBJECT_NAME", DEFAULT_NAME);
								JsonObject jrhFieldListFarmer = getTableFiledList(token, businessObjectName);
								JsonArray fieldListFarmer = (JsonArray) jrhFieldListFarmer.get("data");
								combo.add("farmer", farmer);
								combo.addProperty("redirect", "/"+DEFAULT_NAME);
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
				String businessObjectName = SvParameter.getSysParam("BUSINESS_OBJECT_NAME", DEFAULT_NAME);
				DbDataObject databaseTypeFarmer = svr.getObjectById(SvCore.getTypeIdByName(businessObjectName),
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
				tablesUsedArray[1] = (businessObjectName);
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

	private Boolean isUserAdministrator(String token) {
		// TODO when roles are implemented we should do this check for admin
		// groups
		Boolean is = false;
		DbDataObject userObj = null;
		SvReader svr = null;
		try {
			svr = new SvReader(token);
			userObj = SvCore.getUserBySession(token);
		} catch (SvException e) {
		} finally {
			if (svr != null) {
				svr.release();
				svr.close();
			}
		}
		if (userObj != null && "admin".equalsIgnoreCase(userObj.getVal("USER_NAME").toString())) {
			is = true;
		}
		return is;
	}

	private Boolean isUserInGroup(DbDataObject userObj, String groupName, SvReader svr) {
		Boolean is = false;
		DbDataArray user = null;
		if (userObj != null && groupName != null && groupName != "")
			try {
				user = svr.getUserGroups();
				if (user != null && !user.getItems().isEmpty())
					for (DbDataObject groupNameObj : user.getItems())
						if (groupName.equalsIgnoreCase(groupNameObj.getVal("GROUP_NAME").toString()))
							is = true;
			} catch (SvException e) {
				is = false;
			}
		return is;
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
		if (isUserAdministrator(token) || isUserInGroup(userDbo, "ADVANCED_ADM_CTRL_USERS", svr)) {
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

		if (isUserAdministrator(token) || isUserInGroup(userDbo, "SVAROG_REPORTS_USERS", svr)) {
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

	/**
	 * procedure to clear all tokens that are expired, exited in Tomcat more than 10
	 * minutes
	 * 
	 * @param null
	 * 
	 * @return null
	 */
	private void cleanUpRecoveryData() {
		DateTime now = new DateTime();
		try {
			for (Iterator<Entry<String, Object[]>> it = passRecoveryTokens.entrySet().iterator(); it.hasNext();) {
				Entry<String, Object[]> ent = it.next();
				Object[] recoveryData = ent.getValue();
				DateTime ts = (DateTime) recoveryData[1];
				Duration duration = new Duration(ts, now);
				if (duration.toStandardMinutes().getMinutes() > 10) {
					it.remove();
				}
			}
		} catch (Exception ex) {
			log4j.error("Error cleaning up recovery data", ex);
		}
	}

	public JsonObject loginIacsUser(JsonObject jobj, SvCore svc) throws SvException {
		String username = null, password = null;
		if (jobj.get("username") != null)
			username = jobj.get("username").getAsString();
		if (jobj.get("password") != null)
			password = jobj.get("password").getAsString();

		ResponseHandler jrh = new ResponseHandler();
		JsonObject combo = new JsonObject();

		DbDataObject dboUserG = null;
		DbDataObject dbUser = null;
		try (SvSecurity svsec = new SvSecurity(svc);) {

			dboUserG = new DbDataObject();
			if (!(username == null || password == null)) {
				String token = svsec.logon(username.toUpperCase(), password.toUpperCase());

				// SvarogLogin.systemUnderMaintenanceCheck(svr);
				/* add token for login */
				combo.addProperty("token", token);
				try (SvReader svr = new SvReader(token)) {
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
								}
							}
						}

					}
				}
			}
		}
		return combo;
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
					String businessObjectName = SvParameter.getSysParam("BUSINESS_OBJECT_NAME", DEFAULT_NAME);
					DbDataArray farmData = svc.getPOAObjects(dbUser.getObjectId(), businessObjectName);
					/* remove extra created obj/person f.r */

					Iterator<DbDataObject> itp = personData.getItems().iterator();
					while (itp.hasNext()) {
						DbDataObject dbp = itp.next();
						if (farmData == null)
							throw new SvException("no_farm_found_for_POA_use", dbUser);
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
					// String businessObjectName = SvParameter.getSysParam("BUSINESS_OBJECT_NAME",
					// "FARMER");
					tablesUsedArray[0] = (businessObjectName);
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

}
