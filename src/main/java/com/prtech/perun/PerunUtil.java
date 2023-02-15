package com.prtech.perun;

import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.prtech.svarog.I18n;
import com.prtech.svarog.Sv;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog_common.ResponseHandler;
import com.prtech.svarog_common.ResponseHandler.MessageType;

/**
 * The Util class is superceeded by PerunUtil
 * 
 * @author ristepejov
 *
 */
public class PerunUtil extends SvUtil {
	public static Response handleException(Exception e, ResponseHandler jrh, String message) {
		if (jrh == null)
			jrh = new ResponseHandler();
		int responseCode = 500;
		if (e instanceof SvException) {
			SvException sve = (SvException) e;

			if (sve.getLabelCode().equals(Sv.INVALID_SESSION)) {
				jrh.create(MessageType.ERROR, I18n.getText(Sv.INVALID_SESSION), I18n.getLongText(Sv.INVALID_SESSION),
						new JsonObject());
				responseCode = 401;
			} else if (sve.getLabelCode().equals(Sv.Exceptions.NOT_AUTHORISED)) {
				jrh.create(MessageType.ERROR, I18n.getText(Sv.Exceptions.NOT_AUTHORISED),
						I18n.getLongText(Sv.Exceptions.NOT_AUTHORISED), new JsonObject());
				responseCode = 403;
			} else
				jrh.create(MessageType.ERROR, sve.getLabelCode(), sve.getLabelCode(), sve.getLabelCode());
		} else {
			log4j.error(e.getMessage(), e);
			jrh.create(MessageType.ERROR, I18n.getText(message), I18n.getText(message), new JsonObject());
		}
		return Response.status(responseCode).entity(jrh.getAll().toString()).build();
	}

	public static Response handleException(Exception e, String message) {
		return handleException(e, new ResponseHandler(), message);
	}
	
	public static String getClientIpAddress(HttpServletRequest request) {
	    String xForwardedForHeader = request.getHeader("X-Forwarded-For");
	    if (xForwardedForHeader == null) {
	        return request.getRemoteAddr();
	    } else {
	        // As of https://en.wikipedia.org/wiki/X-Forwarded-For
	        // The general format of the field is: X-Forwarded-For: client, proxy1, proxy2 ...
	        // we only want the client
	        return new StringTokenizer(xForwardedForHeader, ",").nextToken().trim();
	    }
	}
}
