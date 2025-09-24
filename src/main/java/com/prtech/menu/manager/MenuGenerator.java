package com.prtech.menu.manager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.prtech.perun.services.ws.DbReader;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog_common.DbDataObject;

public class MenuGenerator {
	private static final Logger log4j = LogManager.getLogger(MenuGenerator.class);

	private static final String MENU_TYPE = "menu";

	public static DbDataObject saveMenuDefinition(SvReader svr, SvWriter svw, Long menuParent, String menuCode,
			String menuConf) throws Exception {
		DbReader dbr = new DbReader();
		DbDataObject dboMenu = dbr.searchDbObjectBySingleFilter(SvReader.getTypeIdByName(CC.PERUN_MENU), CC.MENU_CODE,
				menuCode, svr);
		if (dboMenu == null) {
			dboMenu = new DbDataObject();
			dboMenu.setObjectType(SvReader.getTypeIdByName(CC.PERUN_MENU));
			if (menuParent != null) {
				dboMenu.setParentId(menuParent);
			}
			dboMenu.setVal(CC.MENU_CODE, menuCode);
			dboMenu.setVal(CC.LABEL_CODE, menuCode);
			dboMenu.setVal(CC.MENU_TYPE, MENU_TYPE);
			dboMenu.setVal(CC.MENU_CONF, menuConf);
			dboMenu.setVal(CC.VERSION, 1L);
			svw.saveObject(dboMenu, false);
			log4j.info("Menu saved: " + menuCode);
		} else {
			log4j.info("Menu exists: " + menuCode);
		}

		return dboMenu;
	}
}
