package com.prtech.perun_core.ws;

import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import com.google.gson.JsonObject;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog_interfaces.ISvCore;
import com.prtech.svarog_interfaces.ISvExecutor;

public class LoginExecutor implements ISvExecutor {

	static final Logger log4j = SvConf.getLogger(LoginExecutor.class);
	private final String category = "SECURITY_PERUN";
	private final String name = "LOGIN_PERUN";
	private final String description = "Login";
	private final DateTime start = new DateTime();
	private final DateTime end = new DateTime("9999-12-31T00:00:00+00");
	private final Class<?> type = JsonObject.class;

	@Override
	public Class<?> getReturningType() {
		return type;
	}

	@Override
	public String getCategory() {
		return category;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public DateTime getStartDate() {
		return start;
	}

	@Override
	public DateTime getEndDate() {
		return end;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object execute(Map<String, Object> params, ISvCore svCore) throws SvException {

		JsonObject jsonData = new JsonObject();

		BusinessLogicWS loginMethod = new BusinessLogicWS();
		try (SvReader svr = new SvReader((SvCore) svCore);) {
			// SvarogLogin.systemUnderMaintenanceCheck(svr);
			jsonData = loginMethod.loginIacsUser((String) params.get("username"), (String) params.get("password"));
			// jsonData = loginMethod.loginUser((String) params.get("username"), (String)
			// params.get("password"));
		}
		return jsonData;
	}

	@Override
	public long versionUID() {
		// TODO Auto-generated method stub
		return 2L;
	}

}