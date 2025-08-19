package com.prtech.menu.manager_bck;

import com.prtech.perun.services.ws.DbReader;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MenuGenerator {
	private static final Logger log4j = LogManager.getLogger(MenuGenerator.class);

	private static final String GENERAL_HOLDING_MENU_CODE = "general_holding_menu_t";
	private static final String WAREHOUSE_MENU_CODE = "warehouse_registry_menu_t";
	private static final String PHARMACY_MENU_CODE = "pharmacies_registry_menu_t";
	private static final String MENU_TYPE = "menu";

	public static void main(String[] args) {
		String sid = null;
		try (SvSecurity svc = new SvSecurity()) {
			sid = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			log4j.info("Session ID: " + sid);
			log4j.info("Logged in with session: " + sid);
		} catch (Exception e) {
			log4j.error("Logon error", e);
		}

		if (sid != null)
			createAllMenus(sid);

	}

	public static void createAllMenus(String sid) {
		try (SvReader svr = new SvReader(sid); SvWriter svw = new SvWriter(svr)) {
			DbDataObject holdingGeneralMenu = saveMenuDefinition(svr, svw, 0L, GENERAL_HOLDING_MENU_CODE,
					getGeneralJson());
			DbDataObject warehouseMenu = saveMenuDefinition(svr, svw, holdingGeneralMenu.getObjectId(),
					WAREHOUSE_MENU_CODE, getWarehouseExtensionJson());
			DbDataObject pharmacyMenu = saveMenuDefinition(svr, svw, holdingGeneralMenu.getObjectId(),
					PHARMACY_MENU_CODE, getPharmacyExtensionJson());

			svw.dbCommit();
			log4j.info("All menus created successfully.");
		} catch (Exception e) {
			log4j.error("Failed to create menus", e);
		}
	}

	private static DbDataObject saveMenuDefinition(SvReader svr, SvWriter svw, Long menuParent, String menuCode,
			String menuConf) throws Exception {
		DbDataObject dboMenu = new DbDataObject();
		DbReader dbr = new DbReader();
		dboMenu = dbr.searchDbObjectBySingleFilter(svCONST.OBJECT_TYPE_MENU, "MENU_CODE", menuCode, svr);
		if (dboMenu != null) {
			dboMenu.setObjectType(svCONST.OBJECT_TYPE_MENU);
			if (menuParent != null) {
				dboMenu.setParentId(menuParent);
			}
			dboMenu.setVal("MENU_CODE", menuCode);
			dboMenu.setVal("LABEL_CODE", menuCode);
			dboMenu.setVal("MENU_TYPE", MENU_TYPE);
			dboMenu.setVal("MENU_CONF", menuConf);
			dboMenu.setVal("VERSION", 1L);
			// dboMenu.setVal("ACL", menuCode + "_isAllowed");
			svw.saveObject(dboMenu, false);
			log4j.info("Menu saved: " + menuCode);
		} else {
			log4j.info("Menu exists: " + menuCode);
		}

		return dboMenu;
	}

	private static String getGeneralJson() {
		return """
				{
				  "buttonArray": [
				    {"menu_name": "farm-summary", "type": "summary", "TABLE_NAME": "HOLDING"},
				    {"menu_name": "table-custom-person", "TABLE_NAME": "PERSON", "READONLY": true, "PERSON_TYPE": "%PERSON_TYPE%", "LABEL": "perun.person_registry.person"},
				    {"menu_name": "table-form-holding", "TABLE_NAME": "HOLDING", "READONLY": false, "SCENARIO": 1, "insert": {"objectConfiguration": {"readOnly": false, "wrapper": true, "refreshSummary": true}}},
				    {"menu_name": "table-grid", "PARENT_NAME": "HOLDING", "TABLE_NAME": "ADDRESS", "READONLY": false, "SCENARIO": 1, "%TABLE_NAME_LABEL_CODE%": "perun.farm_registry.contact_info"},
				    {"menu_name": "table-grid", "PARENT_NAME": "HOLDING", "TABLE_NAME": "FARM_MEMBERS", "READONLY": false, "SCENARIO": 1, "insert": {"objectConfiguration": {"wrapper": true}}},
				    {"menu_name": "table-grid-pers", "TABLE_NAME": "BANKACC", "READONLY": false, "SCENARIO": 1},
				    {"menu_name": "farm-attachments"},
				    {"menu_name": "table-grid", "TABLE_NAME": "DOCUMENTS", "PARENT_NAME": "HOLDING", "SCENARIO": 1, "READONLY": false, "insert": {"objectConfiguration": {"wrapper": true}}},
				    {"menu_name": "table-grid", "TABLE_NAME": "FEES", "PARENT_NAME": "HOLDING", "SCENARIO": 1, "READONLY": false, "insert": {"objectConfiguration": {"wrapper": true}}},
				    {"menu_name": "table-grid", "PARENT_NAME": "HOLDING", "TABLE_NAME": "VMP_DISTRIBUTION", "READONLY": false, "SCENARIO": 7},
				    {"menu_name": "table-grid", "PARENT_NAME": "HOLDING", "TABLE_NAME": "VMP_SHIPMENT", "READONLY": false, "SCENARIO": 7},
				    {"menu_name": "table-grid", "PARENT_NAME": "HOLDING", "TABLE_NAME": "VMP_I_SHIPMENT", "READONLY": false, "SCENARIO": 7},
				    {"menu_name": "table-grid", "PARENT_NAME": "HOLDING", "TABLE_NAME": "PRESCRIPTION_MEDICINE", "READONLY": false, "SCENARIO": 7, "insert": {"objectConfiguration": {"wrapper": true}}},
				    {"menu_name": "table-grid", "PARENT_NAME": "HOLDING", "TABLE_NAME": "APPLICATION", "READONLY": false, "insert": {"objectConfiguration": {"customRowClick": {"type": "route", "route": "/main/applications/%TABLE_NAME%/{rowObjectId}/summary"}}}, "SCENARIO": 7},
				    {"menu_name": "warehouse-holding-prints", "SCENARIO": 2}
				  ]
				}
				""";
	}

	private static String getWarehouseExtensionJson() {
		return """
				{
				  "buttonArray": [
				    {
				      "label": "warehouse.control_chart",
				      "data": [
				        {"menu_name": "single-form-item-custom", "TABLE_NAME": "OFFICIAL_CONTROL_CHART", "PARENT_NAME": "HOLDING", "READONLY": false, "FORM_DATA_WS": "/ReactElements/getFormDataByParentId/%TOKEN%/%OBJECT_ID%/OFFICIAL_CONTROL_CHART"},
				        {"menu_name": "single-form-item-custom", "TABLE_NAME": "PROCED_PERS_QUALITY_REC", "PARENT_NAME": "HOLDING", "READONLY": false, "FORM_DATA_WS": "/ReactElements/getFormDataByParentId/%TOKEN%/%OBJECT_ID%/PROCED_PERS_QUALITY_REC"},
				        {"menu_name": "single-form-item-custom", "TABLE_NAME": "BUILD_FAC_STORAGE", "PARENT_NAME": "HOLDING", "READONLY": false, "FORM_DATA_WS": "/ReactElements/getFormDataByParentId/%TOKEN%/%OBJECT_ID%/BUILD_FAC_STORAGE"},
				        {"menu_name": "single-form-item-custom", "TABLE_NAME": "EMERG_RETURN_PRODUCT", "PARENT_NAME": "HOLDING", "READONLY": false, "FORM_DATA_WS": "/ReactElements/getFormDataByParentId/%TOKEN%/%OBJECT_ID%/EMERG_RETURN_PRODUCT"},
				        {"menu_name": "table-grid", "TABLE_NAME": "VMP_LIST", "PARENT_NAME": "HOLDING", "READONLY": false}
				      ],
				      "ID": "WAREHOUSE_CONTROL_CHART_%OBJECT_ID%",
				      "SCENARIO": 2
				    }
				  ]
				}
				""";
	}

	private static String getPharmacyExtensionJson() {
		return """
				{
				  "buttonArray": [
				    {
				      "label": "warehouse.sell_vmp",
				      "data": [
				        {"menu_name": "single-form-item-custom", "TABLE_NAME": "OFFICIAL_CONTROL_CHART", "PARENT_NAME": "HOLDING", "READONLY": false, "FORM_DATA_WS": "/ReactElements/getFormDataByParentId/%TOKEN%/%OBJECT_ID%/OFFICIAL_CONTROL_CHART"},
				        {"menu_name": "single-form-item-custom", "TABLE_NAME": "PHARM_SELL_VMP", "PARENT_NAME": "HOLDING", "READONLY": false, "FORM_DATA_WS": "/ReactElements/getFormDataByParentId/%TOKEN%/%OBJECT_ID%/PHARM_SELL_VMP"}
				      ],
				      "ID": "PHARMACY_SELLING_VMP_CONTROL_CHART_%OBJECT_ID%"
				    }
				  ]
				}
				""";
	}
}
