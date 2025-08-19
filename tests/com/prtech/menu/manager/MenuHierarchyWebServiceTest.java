package com.prtech.menu.manager;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.prtech.menu.manager_bck.MenuBuilder;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;

public class MenuHierarchyWebServiceTest {
	private static final Logger log4j = LogManager.getLogger(MenuBuilder.class);
	private static final String BASE_URL = "http://fms.prtech.mk/perun/services/MenuManager"; // Adjust if needed
	private static Client client;

	private static String testSessionId = ""; // Replace with real sessionId

	private static String testMenuCode = "general_holding_menu_t"; // Replace with a known menu_code

	@BeforeAll
	public static void initClient() {
		client = ClientBuilder.newClient();
		try (SvSecurity svc = new SvSecurity()) {
			testSessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			log4j.info("Logged in with session ID: " + testSessionId);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
	}

	@Test
	public void testGetExtendedMenu_DOWN_Default() {
		WebTarget target = client.target(BASE_URL).path("extended").path(testSessionId).path(testMenuCode); // no mode
																											// param
																											// (should
																											// default
																											// to DOWN)

		Response response = target.request(MediaType.APPLICATION_JSON).get();

		Assertions.assertEquals(200, response.getStatus(), "Expected HTTP 200 OK");
		String json = response.readEntity(String.class);
		Assertions.assertTrue(json.contains("buttonArray"), "Response should contain buttonArray");
		System.out.println("Response JSON:\n" + json);
	}

	@Test
	public void testGetExtendedMenu_UP() {
		WebTarget target = client.target(BASE_URL).path("extended").path(testSessionId).path(testMenuCode)
				.queryParam("mode", "UP");

		Response response = target.request(MediaType.APPLICATION_JSON).get();

		Assertions.assertEquals(200, response.getStatus(), "Expected HTTP 200 OK");
		String json = response.readEntity(String.class);
		Assertions.assertTrue(json.contains("buttonArray"), "Response should contain buttonArray");
		System.out.println("UP Response:\n" + json);
	}

	@Test
	public void testGetExtendedMenu_BOTH() {
		WebTarget target = client.target(BASE_URL).path("extended").path(testSessionId).path(testMenuCode)
				.queryParam("mode", "BOTH");

		Response response = target.request(MediaType.APPLICATION_JSON).get();

		Assertions.assertEquals(200, response.getStatus(), "Expected HTTP 200 OK");
		String json = response.readEntity(String.class);
		Assertions.assertTrue(json.contains("buttonArray"), "Response should contain buttonArray");
		System.out.println("BOTH Response:\n" + json);
	}

	@Test
	public void testGetExtendedMenu_InvalidMenu() {
		WebTarget target = client.target(BASE_URL).path("extended").path(testSessionId).path("invalid_menu_code");

		Response response = target.request(MediaType.APPLICATION_JSON).get();

		Assertions.assertEquals(404, response.getStatus(), "Expected HTTP 404 for invalid menu");
	}
}
