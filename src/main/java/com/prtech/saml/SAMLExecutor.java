package com.prtech.saml;
import java.sql.Connection;
import java.util.Map;

import org.joda.time.DateTime;

import com.google.gson.JsonObject;
import com.prtech.svarog.SvException;
import com.prtech.svarog_interfaces.ISvConfiguration;
import com.prtech.svarog_interfaces.ISvCore;
import com.prtech.svarog_interfaces.ISvExecutor;

public class SAMLExecutor implements ISvExecutor {

	@Override
	public long versionUID() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Class<?> getReturningType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getCategory() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTime getStartDate() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTime getEndDate() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object execute(Map<String, Object> params, ISvCore svCore) throws SvException {
		JsonObject o =  new JsonObject();
		// TODO Auto-generated method stub
		return o;
	}
}
