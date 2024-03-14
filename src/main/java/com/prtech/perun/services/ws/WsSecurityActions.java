package com.prtech.perun.services.ws;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.UUID;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.prtech.perun.PerunUtil;
import com.prtech.svarog.I18n;
import com.prtech.svarog.Sv;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvExecManager;
import com.prtech.svarog.SvParameter;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog.SvarogInstall;
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.ResponseHandler;
import com.prtech.svarog_common.ResponseHandler.MessageType;

@Path("/SvSecurity")
public class WsSecurityActions {
	static final Logger log4j = SvConf.getLogger(WsSecurityActions.class);

//	 * POST version <br/>
//	 * procedure to create new user to log in to the system if he already is
//	 * farmer in the system
//	 * 
//	 * @param formVals
//	 *            Form with data: <br/>
//	 *            username : String username to log in to the system (FIC) or
//	 *            name <br/>
//	 *            idNo : String company registration or person birth number<br/>
//	 *            eMail : String e-mail of the user<br/>
//	 *            password : String password<br/>
//	 *            repeatPassword : String repeat password in case we miss one letter, we check if passwords are the same
//	 * 
//	 * @return String message if user was created or not
//	 */
	@Path("/register")
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces("application/json")
	public Response doRegister(MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) {
		// just forward to external executor named "PERUN_CORE_EXEC.REGISTER_USER"
		return PerunUtil.callExternalExecutor(formVals, httpRequest, "PERUN_REGISTER_USER_EXECUTOR",
				"PERUN_CORE_EXEC.REGISTER_USER", "createUser.success.incomplete", "Error creating user");
	}

	@Path("/login")
	@POST
	@Produces("text/html;charset=utf-8")
	public Response doLogin(MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest)
			throws SvException {
		// just forward to external executor named "PERUN_CORE_EXEC.REGISTER_USER"
		return PerunUtil.callExternalExecutor(formVals, httpRequest, "PERUN_LOGIN_EXECUTOR",
				"PERUN_CORE_EXEC.LOGIN_USER", "login.success", "Login error");
	}

	/* heartbeat f.r i.d */
	@Path("/checkSession/{session}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response checkSession(@PathParam("session") String session, @Context HttpServletRequest httpRequest)
			throws SvException {
		try (SvReader svr = new SvReader(session)) {
			return Response.status(200).build();
		} catch (SvException e) {
			return PerunUtil.handleException(e, "Session expired");
		}

	}

	@Path("/sendActivationLink")
	@POST
	@Produces("text/html;charset=utf-8")
	public Response sendActivationLink(MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest)
			throws SvException {

		// just forward to external executor named "PERUN_CORE_EXEC.REGISTER_USER"
		return PerunUtil.callExternalExecutor(formVals, httpRequest, "PERUN_ACTIVATION_LINK",
				"PERUN_CORE_EXEC.ACTIVATION_LINK", "user.created.activation", "Login error");

	}

	@Path("/recoverPassword")
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces("application/json")
	public Response recoverPassword(MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) {

		// just forward to external executor named "PERUN_CORE_EXEC.REGISTER_USER"
		return PerunUtil.callExternalExecutor(formVals, httpRequest, "PERUN_PASSWORD_RECOVERY",
				"PERUN_CORE_EXEC.PASSWORD_RECOVERY", "recovery.email.sent", "Password recovery error");

	}

	@Path("/changePassword")
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces("application/json")
	public Response changePassword(MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) {

		// just forward to external executor named "PERUN_CORE_EXEC.REGISTER_USER"
		return PerunUtil.callExternalExecutor(formVals, httpRequest, "PERUN_PASSWORD_CHANGE",
				"PERUN_CORE_EXEC.PASSWORD_CHANGE", "system.password_changed_success", "Password change error");
	}

	@Path("/changeEmail")
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces("application/json")
	public Response changeEmail(MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) {
		// just forward to external executor named "PERUN_CORE_EXEC.REGISTER_USER"
		return PerunUtil.callExternalExecutor(formVals, httpRequest, "PERUN_EMAIL_CHANGE",
				"PERUN_CORE_EXEC.EMAIL_CHANGE", "new.email.set", "Password change error");

	}

	@Path("/activateExternalUser")
	@POST
	@Produces("text/html;charset=utf-8")
	public Response activateExternalUser(MultivaluedMap<String, String> formVals,
			@Context HttpServletRequest httpRequest) throws SvException {
		// just forward to external executor named "PERUN_CORE_EXEC.REGISTER_USER"
		return PerunUtil.callExternalExecutor(formVals, httpRequest, "PERUN_ACTIVATE_USER",
				"PERUN_CORE_EXEC.ACTIVATE_USER", "user.activation_successful", "User activation error");

	}

	@Path("/logout/{token}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response doLogout(@PathParam("token") String token, @Context HttpServletRequest httpRequest) {
		// just forward to external executor named "PERUN_CORE_EXEC.REGISTER_USER"
		JsonObject jsonParams = new JsonObject();
		jsonParams.addProperty("token", token);
		return PerunUtil.callExternalExecutor(jsonParams, httpRequest, "PERUN_LOGOFF_USER",
				"PERUN_CORE_EXEC.LOGOFF_USER", "logout.success", "User logoff error");
	}

	/**
	 * get Personal User Info This method returns list of jsonObject
	 * 
	 * @param actionType return user info by action type param
	 *
	 */

	@Path("/getPersonalUserInfo/{session}/{actionType}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response getPersonalUserInfo(@PathParam("session") String session, @Context HttpServletRequest httpRequest,
			@PathParam("actionType") String actionType) {
		ResponseHandler jrh = new ResponseHandler();
		// JsonArray userInfo = new JsonArray();
		try (SvReader svr = new SvReader(session)) {
			DbDataObject dboUser = null;
			/* get user info */
			dboUser = svr.getInstanceUser();
			if (dboUser == null) {
				jrh.create(MessageType.ERROR, I18n.getText("error.perun.tittle"),
						I18n.getText("error.perun.user_not_found"), new JsonObject());
			} else {
				jrh.create(MessageType.SUCCESS, I18n.getText("configuration.loaded"),
						"SvarogConfiguration.getConfigComponent ", dboUser.toJson());
			}
		} catch (Exception e) {
			return PerunUtil.handleException(e, "error.perun.failedToGetPersonalInfo");
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	@Path("/i18n/{locale}/{label_group}")
	@GET
	@Produces("application/json")
	public Response getI18NLabels(@PathParam("label_group") String labelsGroup, @PathParam("locale") String locale) {
		DbDataArray dbaLablels;
		JsonObject jsLabels = new JsonObject();
		try {
			dbaLablels = I18n.getLabels(locale, labelsGroup);
			if (labelsGroup != null && labelsGroup.length() > 3) {
				String lblCode = null;
				for (DbDataObject dbLabel : dbaLablels.getItems()) {
					lblCode = (String) dbLabel.getVal("label_code");
					if (lblCode != null && lblCode.startsWith(labelsGroup)) {
						jsLabels.addProperty(lblCode, (String) dbLabel.getVal("LABEL_TEXT"));
					}
				}
			}
		} catch (SvException e) {
			return Response.status(500).entity(e.getMessage()).build();
		}
		return Response.status(200).entity(jsLabels.toString()).build();
	}
	
	@Path("/i18n/{locale}/{label_group}/{token}")
	@GET
	@Produces("application/json")
	public Response getI18NLabels(@PathParam("label_group") String labelsGroup, @PathParam("locale") String locale, String token) {
		try (SvReader svr = new SvReader(token);
				SvWriter svw = new SvWriter(svr)) {
			DbDataObject dboUser = null;
			dboUser = svr.getInstanceUser();
			if (dboUser != null) {
				dboUser.setVal("LOCALE", locale);
				svw.saveObject(dboUser);
			}
		} catch (Exception e) {
			return PerunUtil.handleException(e, "error.perun.failedToGetPersonalInfo");
		}
		return getI18NLabels(labelsGroup, locale);
	}

	@Path("/configuration/getConfiguration/{token}/{componentName}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response getConfiguration(@PathParam("token") String token, @PathParam("componentName") String componentName,
			@Context HttpServletRequest httpRequest) {

		ResponseHandler jrh = new ResponseHandler();
		String retval = "{\"login\":{\"enabled\":true,\"submit\":\"/SvSecurity/login/{username}/{password}\",\"afterSubmit\":\"/MAIN\",\"methodtype\":\"GET\"},"
				+ "\"login1\":{\"enabled\":true,\"submit\":\"/SvSecurity/login\",\"afterSubmit\":\"/MAIN\",\"methodtype\":\"POST\"},"
				+ "\"register\":{\"enabled\":true,\"submit\":\"/SvSecurity/register/{username}/{idNo}/{e_mail}/{password}/{repeat_password}\",\"afterSubmit\":\"/LOGIN\",\"methodtype\":\"GET\"},"
				+ "\"register1\":{\"enabled\":true,\"submit\":\"/SvSecurity/register\",\"afterSubmit\":\"/LOGIN\",\"methodtype\":\"POST\"},"
				+ "\"recoverPassword\":{\"enabled\":true,\"submit\":\"/SvSecurity/recoverPassword/{username}\",\"afterSubmit\":\"/LOGIN\",\"methodtype\":\"GET\"},"
				+ "\"recoverPassword1\":{\"enabled\":true,\"submit\":\"/SvSecurity/recoverPassword\",\"afterSubmit\":\"/LOGIN\",\"methodtype\":\"POST\"},"
				+ "\"activateUser\":{\"enabled\":true,\"submit\":\"/SvSecurity/activateExternalUser/{uuid}\",\"afterSubmit\":\"/LOGIN\",\"methodtype\":\"GET\"},"
				+ "\"activateUser1\":{\"enabled\":true,\"submit\":\"/SvSecurity/activateExternalUser\",\"afterSubmit\":\"/LOGIN\",\"methodtype\":\"POST\"},"
				+ "\"activateLink\":{\"enabled\":true,\"submit\":\"/SvSecurity/sendActivationLink/{uuid}\",\"afterSubmit\":\"/LOGIN\",\"methodtype\":\"GET\"},"
				+ "\"activateLink1\":{\"enabled\":true,\"submit\":\"/SvSecurity/sendActivationLink\",\"afterSubmit\":\"/LOGIN\",\"methodtype\":\"POST\"},"
				+ "\"changePassword1\":{\"enabled\":true,\"submit\":\"/SvSecurity/changePassword/{recover_token}/{username}/{idNo}/{new_pass}\",\"afterSubmit\":\"/LOGIN\",\"methodtype\":\"GET\"},"
				+ "\"changePassword\":{\"enabled\":true,\"submit\":\"/SvSecurity/changePassword\",\"afterSubmit\":\"/LOGIN\",\"methodtype\":\"POST\"},"
				+ "\"change_email1\":{\"enabled\":true,\"submit\":\"/SvSecurity/changeEmail\",\"afterSubmit\":\"/LOGIN\",\"methodtype\":\"POST\"},"
				+ "\"change_email\":{\"enabled\":true,\"submit\":\"/SvSecurity/changeEmail/{username}/{idNo}/{e_mail}\",\"afterSubmit\":\"/LOGIN\",\"methodtype\":\"GET\"}}";
		JsonObject jsonObj = new JsonObject();
		Gson gson = new Gson();
		try {
			jsonObj = gson.fromJson(retval, JsonObject.class);
			jrh.create(MessageType.SUCCESS,
					I18n.getText("configuration.loaded") + " SvarogConfiguration.getConfigLogin", "", jsonObj);
		} catch (Exception e) {
			jrh.create(MessageType.EXCEPTION,
					I18n.getText("error.loding.configuration") + " SvarogConfiguration.getConfigLogin", e.getMessage(),
					jsonObj);
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

}
