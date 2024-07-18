package com.prtech.perun.services.ws;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
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
import javax.servlet.http.Cookie;
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
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.opensaml.xml.security.SecurityException;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lastpass.saml.AttributeSet;
import com.lastpass.saml.SAMLClient;
import com.lastpass.saml.SAMLException;
import com.prtech.perun.PerunUtil;
import com.prtech.saml.Client;
import com.prtech.svarog.I18n;
import com.prtech.svarog.Sv;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvExecManager;
import com.prtech.svarog.SvNote;
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
	private Client samlClient = null;

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

	@Path("/getSAMLAuthRequest/")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response getSAMLAuthRequest(MultivaluedMap<String, String> formVals,
			@Context HttpServletRequest httpRequest) {

		try {
			if (samlClient == null)
				synchronized (WsSecurityActions.class) {
					if (samlClient == null) {
						ResponseHandler jrh = new ResponseHandler();
						JsonObject jso = new JsonObject();
						// check for system parameter PERUN_REGISTER_USER_EXECUTOR, default is
						// PERUN_CORE_EXEC.REGISTER_USER so we always end
						// up with that, if we want to change ways of user registration we change the
						// parameter PERUN_REGISTER_USER_EXECUTOR in DB and create executor with that
						// name, then we call that executor from the enviorment
						String cert;
						String privateKey;
						String samlmetadata;
						String entityId;
						String AuthResponseURL;
						String clientIp = PerunUtil.getClientIpAddress(httpRequest);
						try (SvSecurity svs = new SvSecurity(clientIp);) {
							((SvCore) svs).switchUser(svCONST.serviceUser);
							try (SvNote svx = new SvNote(svs)) {
								entityId = SvParameter.getSysParam(CC.SAML_ENTITY_ID, CC.NOT_CONFIGURED);
								AuthResponseURL = SvParameter.getSysParam(CC.SAML_RESPONSE_URL, CC.NOT_CONFIGURED);
								samlmetadata = svx.getNote(0L, CC.SAML_METADATA);
								if (samlmetadata.equals(Sv.EMPTY_STRING))
									svx.setNote(0L, CC.SAML_METADATA, CC.NOT_CONFIGURED);
								privateKey = svx.getNote(0L, CC.SAML_PRIVATEKEY);
								if (privateKey.equals(Sv.EMPTY_STRING))
									svx.setNote(0L, CC.SAML_PRIVATEKEY, CC.NOT_CONFIGURED);
								cert = svx.getNote(0L, CC.SAML_CERTIFICATE);
								if (cert.equals(Sv.EMPTY_STRING))
									svx.setNote(0L, CC.SAML_CERTIFICATE, CC.NOT_CONFIGURED);

							}
						}

						Client tmpClient = new Client(IOUtils.toInputStream(samlmetadata));
						tmpClient.setSPPrivateKey(IOUtils.toInputStream(privateKey));
						// InputStream targetStream = IOUtils.toInputStream(initialString);
						tmpClient.setCertificate(IOUtils.toInputStream(cert));

						tmpClient.setSPConfigEntityId(entityId);
						tmpClient.setSPConfigAuthResponseURL(AuthResponseURL);

						samlClient = tmpClient;
					}
				}
			if (samlClient != null) {
				String samlRequest = samlClient.getSAMLRequest();
				return Response.ok(samlRequest).build();
			}
		} catch (Exception e) {
			if (log4j.isDebugEnabled()) {
				log4j.debug("Failed generating SAML AuthN Request", e);

			}
		}
		return Response.ok("SAML Not Configured").build();

	}

	@Path("/sso")
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces("application/json")
	public Response ssoRedirect(MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) {
		String clientIp = PerunUtil.getClientIpAddress(httpRequest);
		try (SvSecurity svs = new SvSecurity(clientIp);) {

			String keyName = SvParameter.getSysParam(CC.SSO_POST_KEY, CC.NOT_CONFIGURED);
			List<String> authResponse = formVals.get(keyName);

			samlClient.getSamlClient().setRequireSignedAssertion(false);
			AttributeSet at;

			((SvCore) svs).switchUser(svCONST.serviceUser);
			at = samlClient.getSamlClient().validateResponse(authResponse.get(0));
			String userName = at.getNameId();
			try (SvWriter svw = new SvWriter(svs)) {
				DbDataObject user = svs.getUser(userName);
				svs.saveSessionToken(user, svw, at.getResponse().getInResponseTo());
				return getRedirect(at.getResponse().getInResponseTo());
			} catch (SvException e) {
				if (e.getLabelCode().equals(Sv.Exceptions.NO_USER_FOUND))
					return getRegisterUser(at);

			}

		} catch (SAMLException | SvException e) {
			// TODO Auto-generated catch block
			return PerunUtil.handleException(e, "SSO Authentication Error");
		}
		return Response.ok().build();

	}

	@Path("/sso")
	@GET
	@Produces("application/json")
	public Response ssoRedirectGet(@Context HttpServletRequest httpRequest) {
		String session = httpRequest.getParameter("session");
		return getRedirect(session);

	}

	Response getRegisterUser(AttributeSet at) {
		try {
			String url = SvParameter.getSysParam(CC.SSO_REGISTER_USER, CC.NOT_CONFIGURED);
			JsonObject juser = new JsonObject();
			juser.addProperty(Sv.USER_NAME.toString(), at.getNameId());
			for (Entry<String, List<String>> e : at.getAttributes().entrySet()) {
				List<String> l = e.getValue();
				String val = l.size() > 0 ? l.get(0) : Sv.EMPTY_STRING;
				switch (e.getKey()) {
				case "FirstName":
					juser.addProperty("FIRST_NAME", val);
					break;
				case "LastName":
					juser.addProperty("LAST_NAME", val);
					break;
				case "EmailAddress":
					juser.addProperty("E_MAIL", val);
					break;
				case "IDNO":
					juser.addProperty("PIN", val.equals(Sv.EMPTY_STRING) ? at.getNameId() : val);
					break;
				}
			}
			return Response.temporaryRedirect(URI.create(url.replace(CC.USERDATA_PLACEHOLDER, juser.toString())))
					.build();
		} catch (Exception e) {
			return PerunUtil.handleException(e, "SSO Authentication Error");
		}

	}

	Response getRedirectError(String session) {
		try {
			String url = SvParameter.getSysParam(CC.SSO_REDIRECT_URL, CC.NOT_CONFIGURED);
			return Response.temporaryRedirect(URI.create(url.replace(CC.SESSION_PLACEHOLDER, session))).build();
		} catch (Exception e) {
			return PerunUtil.handleException(e, "SSO Authentication Error");
		}

	}

	Response getRedirect(String session) {
		try {
			String url = SvParameter.getSysParam(CC.SSO_REDIRECT_URL, CC.NOT_CONFIGURED);
			return Response.temporaryRedirect(URI.create(url.replace(CC.SESSION_PLACEHOLDER, session))).build();
		} catch (Exception e) {
			return PerunUtil.handleException(e, "SSO Authentication Error");
		}

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
	public Response getI18NLabels(@PathParam("label_group") String labelsGroup, @PathParam("locale") String locale,
			@PathParam("token") String token) {
		try (SvReader svr = new SvReader(token)) {
			svr.setUserLocale(svr.getInstanceUser().getVal("USER_NAME").toString(), locale);
			svr.dbCommit();
		} catch (Exception e) {
			return PerunUtil.handleException(e, "error.perun.failedToGetPersonalInfo");
		}
		return getI18NLabels(labelsGroup, locale);
	}

	@Path("/configuration/getConfiguration/{token}/{componentName}")
	@GET
	@Produces("text/html;charset=utf-8")
	public Response getConfiguration(@PathParam("token") String token, @PathParam("componentName") String componentName,
			@Context HttpServletRequest httpRequest) throws SvException {

		String ssoConfig = SvParameter.getSysParam(CC.SSO_URL, CC.EMPTY_JSON);
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
				+ "\"sso_config\":" + ssoConfig.toString() + ","
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

	public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException,
			CertificateException, SecurityException, SAMLException {

	}

}
