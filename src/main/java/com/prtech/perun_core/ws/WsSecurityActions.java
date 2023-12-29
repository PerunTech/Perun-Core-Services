package com.prtech.perun_core.ws;

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
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvExecManager;
import com.prtech.svarog.SvParameter;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.ResponseHandler;
import com.prtech.svarog_common.ResponseHandler.MessageType;

@Path("/SvSecurity")
public class WsSecurityActions {
	static final Logger log4j = SvConf.getLogger(WsSecurityActions.class);
	static HashMap<String, Object[]> passRecoveryTokens = new HashMap<String, Object[]>();

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
		ResponseHandler jrh = new ResponseHandler();
		String password1 = null;
		String password2 = null;
		String email = "";
		String fic = "";
		String idNo = "";
		boolean farmer = false;
		JsonObject jso = new JsonObject();
		// check for system parameter REGISTER_USER, default is EDBAR so we always end
		// up with that, if we want to change ways of user registration we change the
		// paramter REGISTER_USER in DB and create executor with that name
		// REGISTER_USER.SOMETHING, then we call that executor from the enviorment
		try (SvSecurity svs = new SvSecurity();) {
			String registerEXE = SvParameter.getSysParam("REGISTER_USER", "EDBAR");
			if (registerEXE.equalsIgnoreCase("no_restictions")) {
				// create user with no restrictions, for new enviorments
				BusinessLogicWS blws = new BusinessLogicWS();
				jso = blws.noRestrictionUser(formVals, svs, httpRequest);
				jrh.create(MessageType.SUCCESS, I18n.getText("createUser.success.incomplete"),
						I18n.getText("createUser.success.incomplete"), new JsonObject());
				jso = jrh.getAllv1();
				formVals = null; // so we dont use the edbar create user
			} else if (!registerEXE.equalsIgnoreCase("EDBAR"))
				try (SvExecManager svx = new SvExecManager(svs)) {
					// call executor for creating user from the project/enviorment
					Map<String, Object> params = new HashMap<String, Object>();
					params.put("formVals", formVals);
					JsonObject configuration = (JsonObject) svx.execute("REGISTER_USER." + registerEXE, params, null);
					jrh.create(MessageType.SUCCESS, I18n.getText("createUser.success.incomplete"),
							I18n.getText("createUser.success.incomplete"), configuration);
					jso = jrh.getAllv1();
					formVals = null; // so we dont use the edbar create user
				}
		} catch (SvException e) {

		}

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
						fic = jobj.get("username").getAsString();
					if (jobj.get("idNo") != null)
						idNo = jobj.get("idNo").getAsString();
					if (jobj.get("farmer") != null)
						farmer = jobj.get("farmer").getAsBoolean();
				}
			}
			if (isValidEmailAddress(email) && password1 != "" && fic != "" && password1.equals(password2)) {
				BusinessLogicWS blws = new BusinessLogicWS();
				jso = blws.doRegister(farmer, fic, idNo, email, password1, httpRequest);
			}
		}
		return Response.status(200).entity(jso.toString()).build();
	}

	@Path("/login")
	@POST
	@Produces("text/html;charset=utf-8")
	public Response doLogin(MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest)
			throws SvException {
		String username = "";
		String password = null;
		JsonObject jso = new JsonObject();
		if (formVals != null) {
			for (Entry<String, List<String>> entry : formVals.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isEmpty()) {
					String key = entry.getKey();
					JsonObject jobj = new JsonObject();
					Gson gs = new Gson();
					jobj = gs.fromJson(key, JsonObject.class);
					if (jobj.get("username") != null)
						username = jobj.get("username").getAsString();
					if (jobj.get("password") != null)
						password = jobj.get("password").getAsString();
				}
			}
			jso = doLogin(username, password);
		}
		return Response.status(200).entity(jso.toString()).build();
	}

	public JsonObject doLogin(String username, String password) {
		JsonObject jbo = null;
		String loginType = null;
		try {
			/*
			 * from svarog.properties read param loginType the parametar should have the
			 * name of the executor as value f.r
			 */
			loginType = SvConf.getParam("loginType");
			if (loginType != null && !loginType.isEmpty()) {
				SvSecurity svs = new SvSecurity();
				String token = svs.logon(username.toUpperCase(), password.toUpperCase());
				try (SvExecManager svsec = new SvExecManager(token); SvReader svr = new SvReader(svsec);) {
					Map<String, Object> params = new HashMap<String, Object>();
					params.put("username", username);
					params.put("password", password);
					jbo = (JsonObject) svsec.execute(loginType, params, null);
				} finally {
					if (svs != null) {
						svs.release();
						svs.close();
					}
				}
			} else {
				jbo = defaultLogin(username, password);
			}
		} catch (SvException e) {
			ResponseHandler jrh = new ResponseHandler();
			if (e.getLabelCode().equals("system.error.db_conn_err")) {
				jrh.create(MessageType.ERROR, I18n.getText("system.error.db_conn_err"),
						I18n.getText("system.error.db_conn_err"), new JsonObject());
			} else {
				jrh.create(MessageType.ERROR, I18n.getText(e.getLabelCode()), I18n.getText(e.getLabelCode()),
						new JsonObject());
			}
			jbo = jrh.getAll();
		}
		return jbo;
	}

	public JsonObject defaultLogin(String username, String password) throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		SvSecurity svsec = null;
		try {
			svsec = new SvSecurity();
			String token = svsec.logon(username.toUpperCase(), password.toUpperCase());
			// SvarogLogin.systemUnderMaintenanceCheck(svr);
			/* add token for login */
			JsonObject combo = new JsonObject();
			combo.addProperty("token", token);
			jrh.create(MessageType.SUCCESS, I18n.getText("login.success"), I18n.getText("login.success"), combo);
		} catch (SvException e) {
			throw (e);
		} finally {
			if (svsec != null) {
				svsec.release();
				svsec.close();
			}
		}
		return jrh.getAll();
	}

	/* heartbeat f.r i.d */
	@Path("/checkSession/{session}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response checkSession(@PathParam("session") String session, @Context HttpServletRequest httpRequest)
			throws SvException {
		SvReader svr = null;
		try {
			if (session != null) {
				svr = new SvReader(session);
			}
		} catch (SvException e) {
			ResponseHandler jrh = new ResponseHandler();
			if (e.getLabelCode().equals("error.invalid_session")) {
				log4j.error(e.getFormattedMessage());
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(401).entity(jrh.getAll().toString()).build();
			}
		} finally {
			if (svr != null) {
				svr.release();
				svr.close();
			}
		}
		return Response.status(200).build();
	}

	@Path("/sendActivationLink")
	@POST
	@Produces("text/html;charset=utf-8")
	public Response sendActivationLink(MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest)
			throws SvException {
		String username = "";
		String password = null;
		String edbr = "";
		JsonObject retval = new JsonObject();
		DbDataObject dbo = new DbDataObject();
		BusinessLogicWS bls = new BusinessLogicWS();
		if (formVals != null) {
			for (Entry<String, List<String>> entry : formVals.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isEmpty()) {
					String key = entry.getKey();
					JsonObject jobj = new JsonObject();
					Gson gs = new Gson();
					jobj = gs.fromJson(key, JsonObject.class);
					if (jobj.get("username") != null)
						username = jobj.get("username").getAsString();
					if (jobj.get("idNo") != null)
						edbr = jobj.get("idNo").getAsString();
					if (jobj.get("password") != null)
						password = jobj.get("password").getAsString();
					password = password.toUpperCase();
				}
			}
			try (SvSecurity svc = new SvSecurity()) {
				dbo = svc.getUser(username);
			}
			retval = bls.checkBeforeSend(dbo, edbr, httpRequest);
		}
		return Response.status(200).entity(retval.toString()).build();
	}

	public void sendMail(String recipientAddress, String mailSubject, String mailBody,
			HashMap<String, String> extParams) throws SvException {

		for (Entry<String, String> ent : extParams.entrySet()) {
			mailBody = mailBody.replace(ent.getKey(), ent.getValue());
		}

		// Sender's email ID needs to be mentioned
		String from = SvParameter.getSysParam("mail.from", "NOT_CONFIGURED");
		String username = SvParameter.getSysParam("mail.username", "NOT_CONFIGURED");
		String password = SvParameter.getSysParam("mail.password", "NOT_CONFIGURED");
		String mailFormat = SvParameter.getSysParam("mail.format", "text/html; charset=UTF-8");
		String host = SvParameter.getSysParam("mail.smtp.host", "NOT_CONFIGURED");
		String port = SvParameter.getSysParam("mail.smtp.port", "465");
		String auth = SvParameter.getSysParam("mail.smtp.auth", "true");
		String tls = SvParameter.getSysParam("mail.smtp.starttls.enable", "true");
		String tls_required = SvParameter.getSysParam("mail.smtp.starttls.required", "true");
		String protocols = SvParameter.getSysParam("mail.smtp.ssl.protocols", "TLSv1.2");
		String sockeFactory = SvParameter.getSysParam("mail.smtp.socketFactory.class",
				"javax.net.ssl.SSLSocketFactory");

		// Assuming you are sending email through relay.jangosmtp.net

		Properties props = new Properties();
		props.put("mail.smtp.host", host);
		props.put("mail.smtp.port", port);
		props.put("mail.smtp.auth", auth);
		props.put("mail.smtp.starttls.enable", tls);
		props.put("mail.smtp.ssl.trust", host);
		props.put("mail.smtp.starttls.required", tls_required);
		props.put("mail.smtp.ssl.protocols", protocols);
		props.put("mail.smtp.socketFactory.class", sockeFactory);

		// Get the Session object.
		Session session = Session.getInstance(props, new javax.mail.Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password);
			}
		});

		// Create a default MimeMessage object.
		MimeMessage message = new MimeMessage(session);

		// Set From: header field of the header.
		try {
			message.setFrom(new InternetAddress(from));

			// Set To: header field of the header.
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientAddress));

			message.setHeader("Content-Type", mailFormat);

			// Set Subject: header field
			message.setSubject(mailSubject, "UTF-8");

			// Now set the actual message
			message.setContent(mailBody, mailFormat);
			Transport.send(message);

		} catch (Exception e) {
			throw (new SvException("mail.send.error", svCONST.systemUser, null, recipientAddress));
		}

		if (log4j.isDebugEnabled())
			log4j.debug("Sent message successfully to " + recipientAddress);

	}

	@Path("/recoverPassword")
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces("application/json")
	public Response recoverPassword(MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) {
		String fic = "";
		JsonObject jso = new JsonObject();
		if (formVals != null) {
			for (Entry<String, List<String>> entry : formVals.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isEmpty()) {
					String key = entry.getKey();
					JsonObject jobj = new JsonObject();
					Gson gs = new Gson();
					jobj = gs.fromJson(key, JsonObject.class);
					if (jobj.get("username") != null)
						fic = jobj.get("username").getAsString();
				}
			}
			if (fic != null && fic != "") {
				Object[] recoveryData = new Object[2];
				recoveryData[0] = UUID.randomUUID().toString();
				recoveryData[1] = new DateTime();
				passRecoveryTokens.put(fic, recoveryData);
				jso = doRecovery(fic, recoveryData[0].toString(), frontEndHost(httpRequest));
			}
		}
		return Response.status(200).entity(jso.toString()).build();
	}

	private static String frontEndHost(HttpServletRequest httpRequest) {
		return httpRequest.getScheme() + "://" + httpRequest.getServerName() + ":" + httpRequest.getServerPort() + "/";
	}

	public JsonObject doRecovery(String fic, String recoveryToken, String feHost) {
		JsonObject jbo = null;
		try {
			/*
			 * TODO this check is temporary to avoid using mail server for testing change in
			 * future f.r
			 */
			if (feHost.equals("http://192.168.100.155:9090/")) {
				jbo = returnLocalUrlChangePass(fic, recoveryToken, feHost);
			} else {
				jbo = recoverPassword(fic, recoveryToken, feHost);
			}
		} catch (Exception e) {
			ResponseHandler jrh = new ResponseHandler();
			jrh.create("EXCEPTION", I18n.getText("error.loading.plugin"), e.getMessage(), new JsonObject());
			jbo = jrh.getAll();
		}
		return jbo;
	}

	public JsonObject returnLocalUrlChangePass(String fic, String recoveryToken, String feHost) {
		ResponseHandler jrh = new ResponseHandler();
		SvSecurity svsec = null;
		try {
			svsec = new SvSecurity();
			DbDataObject dboUser = svsec.getUser(fic.toUpperCase());
			if (dboUser != null) {
				String uri = fronEndHostString(dboUser, feHost) + "#/home/change_pass?username="
						+ dboUser.getVal("USER_NAME") + "&recoveryToken=" + recoveryToken;
				jrh.create(MessageType.SUCCESS, "Линк за промена на лозинка од тест сервер", uri, uri);
			} else
				jrh.create(MessageType.ERROR, I18n.getText("user.farmer_notFound"),
						I18n.getText("user.farmer_notFound"), new JsonObject());
		} catch (SvException e) {
			jrh = ResponseHandler.responseHandlerByException(e);
		} finally {
			if (svsec != null) {
				svsec.release();
				svsec.close();
			}
		}
		return jrh.getAll();
	}

	public JsonObject recoverPassword(String fic, String recoveryToken, String feHost) {
		ResponseHandler jrh = new ResponseHandler();
		SvSecurity svsec = null;
		try {
			svsec = new SvSecurity();
			DbDataObject dboUser = svsec.getUser(fic.toUpperCase());
			if (dboUser != null) {
				sendPassRecoveryEmail(dboUser, recoveryToken, feHost);
				jrh.create(MessageType.SUCCESS, I18n.getText("recovery.email.sent"),
						I18n.getText("user.created.activation"), new JsonObject());
			} else
				jrh.create(MessageType.ERROR, I18n.getText("user.farmer_notFound"),
						I18n.getText("user.farmer_notFound"), new JsonObject());
		} catch (SvException e) {
			jrh = ResponseHandler.responseHandlerByException(e);
		} finally {
			if (svsec != null) {
				svsec.release();
				svsec.close();
			}
		}
		return jrh.getAll();
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
		String uri = fronEndHostString(dboUser, feHost) + "#/home/change_pass?username=" + dboUser.getVal("USER_NAME")
				+ "&recoveryToken=" + recoverToken;
		// if user is internal send flag for internal.jsp in gui no embg needed
		String vUserType = dboUser.getVal("USER_TYPE").toString();
		if ("INTERNAL".equalsIgnoreCase(vUserType) || "ADM".equalsIgnoreCase(vUserType)
				|| "ARCHIVE".equalsIgnoreCase(vUserType) || "CONTROL".equalsIgnoreCase(vUserType)
				|| "BATCH".equalsIgnoreCase(vUserType))
			uri = fronEndHostString(dboUser, feHost) + "#/home/change_pass_internal?username="
					+ dboUser.getVal("USER_NAME") + "&recoveryToken=" + recoverToken;
		HashMap<String, String> extParams = new HashMap<>();
		extParams.put("{NAME}", dboUser.getVal("FIRST_NAME")
				+ (dboUser.getVal("LAST_NAME") != null ? " " + dboUser.getVal("LAST_NAME") : ""));
		extParams.put("{RECOVERY_TOKEN}", uri);
		sendMail((String) dboUser.getVal("E_MAIL"), I18n.getText("mail.subject_revocerpass"), mailBody, extParams);

		return true;
	}

	/**
	 * method to generate frontEnd host-name so we can make web address/link to our
	 * web service, if there is parameter "frontend.gui_host" in "svarog.properties"
	 * we use that, if not we generate the value from the address that was called
	 * 
	 * @param dboUser DbDataObject user that we are processing
	 * @param feHost  String front end host-name generated from calling the web
	 *                service
	 * 
	 * @return String generated host-name variable
	 * 
	 * @throws SvException
	 */
	private String fronEndHostString(DbDataObject dboUser, String feHost) throws SvException {
		String feHost1 = "";
		SvSecurity svs = new SvSecurity();
		String frontEndHostParam = svs.getPublicParam("frontend.gui_host");
		if (frontEndHostParam != null && frontEndHostParam != "" && frontEndHostParam.trim().length() > 4) {
			feHost1 = frontEndHostParam;
		} else {
			if (feHost != null && feHost != "" && feHost.trim().length() > 4) {
				feHost1 = feHost.trim() + "/perun/index.html";
			} else {
				svs.release();
				svs.close();
				throw new SvException("mail.activation.error", dboUser);
			}
		}
		svs.release();
		svs.close();
		return feHost1;
	}

	@Path("/changePassword")
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces("application/json")
	public Response changePassword(MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) {
		cleanUpRecoveryData();
		JsonObject jso = new JsonObject();
		String recoverToken = "";
		String fic = "";
		String idNo = "";
		String newPass = "";
		String repNewPass = "";
		if (formVals != null) {
			for (Entry<String, List<String>> entry : formVals.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isEmpty()) {
					String key = entry.getKey();
					JsonObject jobj = new JsonObject();
					Gson gs = new Gson();
					jobj = gs.fromJson(key, JsonObject.class);
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
				}
			}
		}
		Object[] recoveryData = passRecoveryTokens.get(fic);
		if (recoverToken != "" && fic != "" && newPass != "" && recoveryData != null
				&& recoverToken.equals(recoveryData[0]) && newPass.equals(repNewPass)) {
			jso = changePassword(fic, idNo, newPass);
		}
		return Response.status(200).entity(jso.toString()).build();
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
	 */
	public JsonObject changePassword(String fic, String idNo, String newPass) {
		ResponseHandler jrh = new ResponseHandler();
		SvSecurity svs = null;
		BusinessLogicWS bws = new BusinessLogicWS();
		try {
			svs = new SvSecurity();
			DbDataObject dboUser = svs.getUser(fic.toUpperCase());
			if (dboUser == null)
				jrh.create(MessageType.ERROR, I18n.getText("user.user_not_found"), I18n.getText("user.user_not_found"));
			else {
				Boolean validPerson = false;
				String vUserType = dboUser.getVal("USER_TYPE").toString();
				if ("EXTERNAL".equalsIgnoreCase(vUserType)) {
					validPerson = bws.hasValidFarmers(dboUser, fic.toUpperCase(), idNo, svs);
					if (!validPerson) {
						jrh.create(MessageType.ERROR, I18n.getText("system.farmer_id_not_matched"),
								I18n.getText("system.farmer_id_not_matched"));
					} else {
						svs.createUser((String) dboUser.getVal("USER_NAME"), newPass.toUpperCase(),
								(String) dboUser.getVal("FIRST_NAME"), (String) dboUser.getVal("LAST_NAME"),
								(String) dboUser.getVal("E_MAIL"), (String) dboUser.getVal("PIN"),
								(String) dboUser.getVal("TAX_ID"), (String) dboUser.getVal("USER_TYPE"), null, true);
						jrh.create(MessageType.SUCCESS, I18n.getText("system.password_changed_success"), "  ");
					}
				}

				if ("INTERNAL".equalsIgnoreCase(vUserType) || "ADM".equalsIgnoreCase(vUserType)
						|| "ARCHIVE".equalsIgnoreCase(vUserType) || "CONTROL".equalsIgnoreCase(vUserType)
						|| "BATCH".equalsIgnoreCase(vUserType)) {
					svs.createUser((String) dboUser.getVal("USER_NAME"), newPass.toUpperCase(),
							(String) dboUser.getVal("FIRST_NAME"), (String) dboUser.getVal("LAST_NAME"),
							(String) dboUser.getVal("E_MAIL"), (String) dboUser.getVal("PIN"),
							(String) dboUser.getVal("TAX_ID"), (String) dboUser.getVal("USER_TYPE"), null, true);
					jrh.create(MessageType.SUCCESS, I18n.getText("system.password_changed_success"), "  ");
				}
			}
		} catch (SvException e) {
			jrh = ResponseHandler.responseHandlerByException(e);
		} finally {
			if (svs != null)
				svs.release();
		}
		return jrh.getAll();
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

	@Path("/changeEmail")
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces("application/json")
	public Response changeEmail(MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) {
		cleanUpRecoveryData();
		JsonObject jso = new JsonObject();
		String fic = "";
		String idNo = "";
		String email = "";
		if (formVals != null) {
			for (Entry<String, List<String>> entry : formVals.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isEmpty()) {
					String key = entry.getKey();
					JsonObject jobj = new JsonObject();
					Gson gs = new Gson();
					jobj = gs.fromJson(key, JsonObject.class);
					if (jobj.get("username") != null)
						fic = jobj.get("username").getAsString();
					if (jobj.get("idNo") != null)
						idNo = jobj.get("idNo").getAsString();
					if (jobj.get("eMail") != null)
						email = jobj.get("eMail").getAsString();
				}
			}

			if (fic != "" && idNo != "" && email != "")
				jso = changeEmail(fic, idNo, email, httpRequest);
		}
		return Response.status(200).entity(jso.toString()).build();
	}

	public JsonObject changeEmail(String fic, String idNo, String email, HttpServletRequest httpRequest) {
		JsonObject jbo = null;
		BusinessLogicWS bsw = new BusinessLogicWS();
		try {
			jbo = bsw.changeEmail(fic, idNo, email, httpRequest);
		} catch (Exception e) {
			ResponseHandler jrh = new ResponseHandler();
			jrh.create("EXCEPTION", I18n.getText("error.loading.plugin"),
					" agriPluginManager.PluginLogin " + e.getMessage(), new JsonObject());
			jbo = jrh.getAll();
		}
		return jbo;
	}

	/* change pass */
	@Path("/changePassword/{user_name}/{pin}/{new_pass}/{repeat_pass}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response changePassword(@PathParam("user_name") String user_name, @PathParam("pin") String pin,
			@PathParam("new_pass") String new_pass, @PathParam("repeat_pass") String repeat_pass,
			@Context HttpServletRequest httpRequest) {
		String error = null;
		SvSecurity svs = null;
		try {
			DbDataObject dboUser = null;
			svs = new SvSecurity();
			/* get user info */
			if (new_pass.equals(repeat_pass)) {
				dboUser = svs.getUser(user_name);
				if (dboUser == null) {
					error = I18n.getText("user.user_not_found");
				} else {
					if (!dboUser.getVal("USER_TYPE").equals("EXTERNAL")) {
						DbDataArray person = svs.getPOAObjects(dboUser.getObjectId(), "PERSON");
						if (person != null) {
							error = I18n.getText("This function is not yet active for this type of user");
						}
					} else {
						dboUser.setVal("PASSWORD_HASH", (new_pass.toUpperCase()));
						dboUser.setVal("CONFIRMED_PASSWORD_HASH", (new_pass.toUpperCase()));
					}

					svs.createUser((String) dboUser.getVal("USER_NAME"), (String) dboUser.getVal("PASSWORD_HASH"),
							(String) dboUser.getVal("FIRST_NAME"), (String) dboUser.getVal("LAST_NAME"),
							(String) dboUser.getVal("E_MAIL"), (String) dboUser.getVal("PIN"),
							(String) dboUser.getVal("TAX_ID"), (String) dboUser.getVal("USER_TYPE"), null, true);

					error = I18n.getText("system.password_changed_success");
				}
			} else {
				error = I18n.getText("system.password_did_not_match");
			}
		} catch (SvException e) {
			error = e.getFormattedMessage();
		} finally {
			if (svs != null)
				svs.release();
		}
		return Response.status(200).entity(error).build();
	}

	@Path("/activateExternalUser")
	@POST
	@Produces("text/html;charset=utf-8")
	public Response activateExternalUser(MultivaluedMap<String, String> formVals,
			@Context HttpServletRequest httpRequest) throws SvException {
		JsonObject retval = new JsonObject();
		String userUid = "";
		try {
			for (Entry<String, List<String>> entry : formVals.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isEmpty()) {
					String key = entry.getKey();
					JsonObject jobj = new JsonObject();
					Gson gs = new Gson();
					jobj = gs.fromJson(key, JsonObject.class);
					if (jobj.get("uuid") != null)
						userUid = jobj.get("uuid").getAsString();
				}
			}
			retval = activateExternalUser(userUid);
		} catch (Exception e) {
			ResponseHandler jrh = ResponseHandler
					.responseHandlerByException(new SvException(e.getMessage(), svCONST.systemUser, e));
			log4j.error("User activation raised exception", e);
			retval = jrh.getAll();
		}
		return Response.status(200).entity(retval.toString()).build();
	}

	public JsonObject activateExternalUser(String uuid) {
		ResponseHandler jrh = new ResponseHandler();
		SvSecurity svs = null;
		try {
			svs = new SvSecurity();
			svs.activateExternalUser(uuid);
			jrh.create(MessageType.SUCCESS, I18n.getText("user.activation_successful"), I18n.getText("user.activated"));
		} catch (SvException e) {
			jrh = ResponseHandler.responseHandlerByException(e);
			log4j.error("User activation raised exception", e);
		} finally {
			if (svs != null)
				svs.release();
		}
		return jrh.getAll();
	}

	@Path("/logout/{token}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response doLogout(@PathParam("token") String token, @Context HttpServletRequest httpRequest) {
		ResponseHandler jrh = new ResponseHandler();
		SvSecurity svsec = null;
		try {
			svsec = new SvSecurity();
			svsec.logoff(token);
			jrh.create(MessageType.SUCCESS, I18n.getText("logout.success"), I18n.getText("logout.success"),
					new JsonObject());
		} catch (SvException e) {
			jrh = ResponseHandler.responseHandlerByException(e);
			log4j.error("Session logoff raised exception", e);
		} finally {
			if (svsec != null)
				svsec.release();
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
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
	public Response getPersonalUserInfo(@PathParam("session") String session,
			@PathParam("actionType") String actionType, @Context HttpServletRequest httpRequest) {
		ResponseHandler jrh = new ResponseHandler();
		// JsonArray userInfo = new JsonArray();
		try (SvReader svr = new SvReader(session)) {
			DbDataObject dboUser = null;
			/* get user info */
			dboUser = svr.getInstanceUser();
			if (dboUser == null) {
				jrh.create(MessageType.ERROR, "Грешка", "Корисникот не е пронајден", new JsonObject());
			} else {
				// if (dboUser.getVal("USER_TYPE").equals("EXTERNAL")) {
				jrh.create(MessageType.SUCCESS, I18n.getText("configuration.loaded"),
						"SvarogConfiguration.getConfigComponent ", dboUser.toJson());
				// }
//				} else {
//					jrh.create(MessageType.SUCCESS, "Инфо", "функционалноста е во развој", new JsonObject());
//				}
			}
		} catch (Exception e) {
			return Util.handleException(e, jrh, "error.perun.failedToGetPersonalInfo");
		}
		return Response.status(200).entity(jrh.getAll().toString()).build();
	}

	public JsonArray getUserInfo(DbDataObject dboUser) {
		JsonObject jObj = new JsonObject();
		JsonArray jArray = new JsonArray();

		jObj.addProperty("fieldType", "string");
		jObj.addProperty("fieldId", "FIRST_NAME");
		jObj.addProperty("fieldName", "ИМЕ");
		if (dboUser.getVal("FIRST_NAME") != null) {
			jObj.addProperty("fieldValue", dboUser.getVal("FIRST_NAME").toString());
		} else {
			jObj.addProperty("fieldValue", "");
		}
		jArray.add(jObj);

		jObj = new JsonObject();
		jObj.addProperty("fieldType", "string");
		jObj.addProperty("fieldId", "LAST_NAME");
		jObj.addProperty("fieldName", "ПРЕЗИМЕ");
		if (dboUser.getVal("LAST_NAME") != null) {
			jObj.addProperty("fieldValue", dboUser.getVal("LAST_NAME").toString());
		} else {
			jObj.addProperty("fieldValue", "");
		}
		jArray.add(jObj);

		jObj = new JsonObject();
		jObj.addProperty("fieldType", "numeric");
		jObj.addProperty("fieldId", "PIN");
		jObj.addProperty("fieldName", "МАТИЧЕН/ДАНОЧЕН БРОЈ");
		if (dboUser.getVal("PIN") != null) {
			jObj.addProperty("fieldValue", dboUser.getVal("PIN").toString());
		} else {
			jObj.addProperty("fieldValue", "");
		}
		jArray.add(jObj);

		jObj = new JsonObject();
		jObj.addProperty("fieldType", "string");
		jObj.addProperty("fieldId", "EMAIL");
		jObj.addProperty("fieldName", "Е-пошта");
		if (dboUser.getVal("EMAIL") != null) {
			jObj.addProperty("fieldValue", dboUser.getVal("EMAIL").toString());
		} else {
			jObj.addProperty("fieldValue", "");
		}
		jArray.add(jObj);

		jObj = new JsonObject();
		jObj.addProperty("fieldType", "string");
		jObj.addProperty("fieldId", "USER_NAME");
		jObj.addProperty("fieldName", "КОРИСНИЧКО ИМЕ");
		jObj.addProperty("fieldProp", "disabled");
		jObj.addProperty("fieldValue", dboUser.getVal("USER_NAME").toString());
		jArray.add(jObj);

		jObj = new JsonObject();
		jObj.addProperty("fieldType", "password");
		jObj.addProperty("fieldId", "PASSWORD");
		jObj.addProperty("fieldName", "ЛОЗИНКА");
		jObj.addProperty("fieldValue", "");
		jArray.add(jObj);

		jObj = new JsonObject();
		jObj.addProperty("fieldType", "password");
		jObj.addProperty("fieldId", "PASSWORD");
		jObj.addProperty("fieldName", "ПОВТОРИ ЛОЗИНКА");
		jObj.addProperty("fieldValue", "");
		jArray.add(jObj);

		return jArray;
	}

	@Path("/updatePersonalUserInfo/{session}")
	@POST
	@Produces("text/html;charset=utf-8")
	public Response updatePersonalUserInfo(@PathParam("session") String session,
			MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		SvSecurity svs = null;
		SvReader svr = null;
		String userName = "";
		String firstName = "";
		String lastName = "";
		String password = null;
		String email = "";
		String pin = "";
		Boolean canSave = false;
		DbDataObject dboUser = null;

		try {
			svs = new SvSecurity();
			svr = new SvReader(session);
			dboUser = svr.getInstanceUser();
			if (formVals != null && dboUser != null) {
				for (Entry<String, List<String>> entry : formVals.entrySet()) {
					if (entry.getKey() != null && !entry.getKey().isEmpty()) {
						String key = entry.getKey();
						JsonObject jobj = new JsonObject();
						Gson gs = new Gson();
						jobj = gs.fromJson(key, JsonObject.class);
						if (jobj.get("КОРИСНИЧКО ИМЕ") != null) {
							userName = jobj.get("КОРИСНИЧКО ИМЕ").getAsString();
						} else {
							userName = dboUser.getVal("USER_NAME").toString();
						}
						if (jobj.get("ИМЕ") != null) {
							firstName = jobj.get("ИМЕ").getAsString();
						} else {
							firstName = dboUser.getVal("FIRST_NAME").toString();
						}
						if (jobj.get("ПРЕЗИМЕ") != null) {
							lastName = jobj.get("ПРЕЗИМЕ").getAsString();
						} else {
							lastName = dboUser.getVal("LAST_NAME").toString();
						}
						if (jobj.get("СТАРА ЛОЗИНКА") != null) {
							password = jobj.get("СТАРА ЛОЗИНКА").getAsString();
							if (dboUser.getVal("PASSWORD_HASH").equals(SvUtil.getMD5(password))) {
								canSave = true;
							}
						}
						if (jobj.get("ЛОЗИНКА") != null) {
							password = jobj.get("ЛОЗИНКА").getAsString();
						} else {
							password = dboUser.getVal("PASSWORD_HASH").toString();
						}
						if (jobj.get("Е-ПОШТА") != null) {
							email = jobj.get("Е-ПОШТА").getAsString();
						} else {
							email = dboUser.getVal("E_MAIL").toString();
						}
						if (jobj.get("МАТИЧЕН/ДАНОЧЕН БРОЈ") != null) {
							pin = jobj.get("МАТИЧЕН/ДАНОЧЕН БРОЈ").getAsString();
						} else {
							pin = dboUser.getVal("PIN").toString();
						}
					}
				}
			}
			if (canSave) {
				svs.createUser(userName, SvUtil.getMD5(password).toUpperCase(), firstName, lastName, email, pin, "",
						(String) dboUser.getVal("USER_TYPE"), (String) dboUser.getStatus(), true);
				jrh.create(MessageType.SUCCESS, I18n.getText("perun.succesfully_update_user"),
						"perun.succesfully_update_user");
			} else {
				jrh.create(MessageType.SUCCESS, I18n.getText("perun.pleaseEnterCorrectPassword"),
						"perun.pleaseEnterCorrectPassword");
			}
		} catch (SvException e) {
			jrh = ResponseHandler.responseHandlerByException(e);
		} finally {
			if (svs != null)
				svs.release();
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

	@Path("/configuration/getConfiguration/{token}/{componentName}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response getConfiguration(@PathParam("token") String token, @PathParam("componentName") String componentName,
			@Context HttpServletRequest httpRequest) {
		JsonObject jsonObj = new JsonObject();
		SvReader svr = null;
		if (!token.equals("undefined") && !token.equals("null") && !componentName.equals("LOGIN")) {
			try {
				svr = new SvReader(token);
				jsonObj = getMainMenuEdbar(token, componentName);
			} catch (SvException e) {
				if (e.getLabelCode().equals("error.invalid_session"))
					jsonObj = getConfigLogin();
			}
		} else {
			jsonObj = getConfigLogin();
		}
		if (svr != null)
			svr.release();

		return Response.status(200).entity(jsonObj.toString()).build();
	}

	public JsonObject getMainMenuEdbar(String token, String componentName) {
		JsonObject jsonObj = new JsonObject();
		ResponseHandler jrh = new ResponseHandler();
		SvReader svr = null;
		BusinessLogicWS bls = new BusinessLogicWS();
		try {
			if (!token.equals("undefined") && !token.equals("null")) {
				svr = new SvReader(token);
				switch (componentName.toUpperCase()) {
				case "MAIN_MENU":
					jsonObj = bls.menuMain(token);
					break;
				case "MODULE_MENU":
					jsonObj = bls.menuModule(svr);
					break;
				case "SERVICE_MENU":
					jsonObj = bls.menuService(token);
					break;
				case "SEARCH_FORM":
					jsonObj = bls.menuSearchForm(token);
					break;
				default:
					break;
				}
				jrh.create(MessageType.SUCCESS, I18n.getText("configuration.loaded"),
						"SvarogConfiguration.getConfigComponent " + componentName, jsonObj);
			}
		} catch (SvException e) {
			jrh.create(MessageType.ERROR, I18n.getText("Настана грешка"),
					I18n.getText("Ве молиме контактирајте го администраторот"), new JsonObject());
		} finally {
			if (svr != null)
				svr.release();
		}
		return jrh.getAll();
	}

	public JsonObject getConfigLogin() {
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
		return jrh.getAll();
	}

	public Boolean isUserAdministrator(String token) {
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

	public Boolean isUserInGroup(DbDataObject userObj, String groupName, SvReader svr) {
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

	/*
	 * build json for Module cards for GUI, which are entry point of application or
	 * module f.r
	 */
	@Path("/getConfigModuleCardsEntry/{token}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response getConfigModuleCardsEntry(@PathParam("token") String token,
			@Context HttpServletRequest httpRequest) {
		JsonObject jso;
		ResponseHandler jrh = new ResponseHandler();
		try {
			jso = getCofnigModules(token);
		} catch (SvException e) {
			if (e.getLabelCode().equals("error.invalid_session")) {
				log4j.error(e.getFormattedMessage());
				jrh.create(MessageType.ERROR, I18n.getText("error.invalid_session"),
						I18n.getText("error.invalid_session"), new JsonObject());
				return Response.status(401).entity(jrh.getAll().toString()).build();
			}

			log4j.error(e.getFormattedMessage(), e);
			jrh.create(MessageType.ERROR, I18n.getText("global.error"), I18n.getText("global.error"), new JsonObject());
			return Response.status(200).entity(jrh.getAll().toString()).build();
		}
		return Response.status(200).entity(jso.toString()).build();
	}

	/*
	 * TODO doouble check of usage of this method it should be replaced with
	 * permission ACL f.r
	 */
	public JsonObject getCofnigModules(String token) throws SvException {
		ResponseHandler jrh = new ResponseHandler();
		SvReader svr = null;
		try {
			svr = new SvReader(token);

			JsonArray jArray = new JsonArray();
			JsonObject jObj = new JsonObject();
			DbDataObject userObj = SvCore.getUserBySession(svr.getSessionId());
			DbDataArray userDefaultGroups = svr.getAllUserGroups(userObj, false);

			for (DbDataObject dbo : userDefaultGroups.getItems()) {
				// switch (dbo.getVal("GROUP_TYPE").toString()) {
				switch (dbo.getVal("GROUP_NAME").toString()) {
				case "REF_PRICE_USERS":
					jObj.addProperty("id", "ref_price");
					jObj.addProperty("text", "Модул за референтни цени");
					jObj.addProperty("title", "Референтни цени");
					jObj.addProperty("img", "./img/modulecards/ref_price.jpeg");
					jArray.add(jObj);
					break;
				case "ADMINISTRATORS":
					jObj.addProperty("id", "batch");
					jObj.addProperty("text", "(селекции, калкулации, екстракции, рангирање...)");
					jObj.addProperty("title", "Извршување процеси");
					jObj.addProperty("img", "./img/modulecards/batch.jpeg");
					jArray.add(jObj);

					// jObj = new JsonObject();
					// jObj.addProperty("id", "fr");
					// jObj.addProperty("text", "Модул за регистрација на
					// земјоделски стопанства");
					// jObj.addProperty("title", "Фарм регистар");
					// jObj.addProperty("img", "./img/modulecards/fr.jpg");
					// jArray.add(jObj);

					jObj = new JsonObject();
					jObj.addProperty("id", "fc");
					jObj.addProperty("text", "Модул за контрола на терен");
					jObj.addProperty("title", "Контрола на терен");
					jObj.addProperty("img", "./img/modulecards/fc.jpg");
					jArray.add(jObj);

					jObj = new JsonObject();
					jObj.addProperty("id", "rankingextraction");
					jObj.addProperty("text", "Модул за рангирање и екстракции");
					jObj.addProperty("title", "Рангирање и Екстракции");
					jObj.addProperty("img", "./img/modulecards/rankextraction.jpg");
					jArray.add(jObj);

					jObj = new JsonObject();
					jObj.addProperty("id", "edbar");
					jObj.addProperty("text", "Модул за аплицирање за субвенции");
					jObj.addProperty("title", "Единствено барање");
					jObj.addProperty("img", "./img/modulecards/edbar.jpg");
					jArray.add(jObj);

					jObj = new JsonObject();
					jObj.addProperty("id", "lpis");
					jObj.addProperty("text", "Систем за идентификација на земјишни парцели");
					jObj.addProperty("title", "СИЗП");
					jObj.addProperty("img", "./img/modulecards/lpis.jpg");
					jArray.add(jObj);

					jObj = new JsonObject();
					jObj.addProperty("id", "user_manager");
					jObj.addProperty("text", "(корисници, кориснички групи и системски табели)");
					jObj.addProperty("title", "Системски поставки");
					jObj.addProperty("img", "./img/modulecards/systemsettings.jpg");
					jArray.add(jObj);
					break;
				case "ADM_CONTROL_USERS":
				case "ADVANCED_ADM_CTRL_USERS":
					jObj = new JsonObject();
					jObj.addProperty("id", "fr");
					jObj.addProperty("text", "Модул за регистрација на земјоделски стопанства");
					jObj.addProperty("title", "Фарм регистар");
					jObj.addProperty("img", "./img/modulecards/fr.jpg");
					jArray.add(jObj);

					jObj = new JsonObject();
					jObj.addProperty("id", "lpis");
					jObj.addProperty("text", "Модул за контрола на терен");
					jObj.addProperty("title", "Контрола на терен");
					jObj.addProperty("img", "./img/modulecards/lpis.jpg");
					jArray.add(jObj);
					break;
				case "BATCH_USERS":
					jObj = new JsonObject();
					jObj.addProperty("id", "batch");
					jObj.addProperty("text", "(селекции, калкулации, екстракции, рангирање...)");
					jObj.addProperty("title", "Извршување процеси");
					jObj.addProperty("img", "./img/modulecards/batch.jpeg");
					jArray.add(jObj);
					break;
				case "FIELD_CONTROL_USERS":
					jObj = new JsonObject();
					jObj.addProperty("id", "rankingextraction");
					jObj.addProperty("text", "Модул за рангирање и екстракции");
					jObj.addProperty("title", "Рангирање и Екстракции");
					jObj.addProperty("img", "./img/modulecards/rank.jpg");
					jArray.add(jObj);
					break;
				default:
					break;
				}
			}
			jrh.create(MessageType.SUCCESS, I18n.getText("configuration.loaded") + " PerunConfiguration.moduleCards",
					"", jArray);
		} catch (SvException e) {
			log4j.error(e.getMessage());
			throw e;
		} finally {
			if (svr != null)
				svr.release();
			svr.close();
		}
		return jrh.getAll();
	}

//	/**
//	 * procedure to validate e-mail address, simple validation a@b.c will return
//	 * true
//	 * 
//	 * @param email
//	 *            String e-mail address to be validated
//	 * 
//	 * @return Boolean true if e-mail is in valid format
//	 */
	private static Boolean isValidEmailAddress(String email) {
		boolean result = true;
		try {
			InternetAddress emailAddr = new InternetAddress(email);
			emailAddr.validate();
		} catch (AddressException ex) {
			result = false;
		}
		return result;
	}

}
