package com.prtech.perun.services.ws;

import java.util.ArrayList;

import com.prtech.menu.manager.CC;
import com.prtech.svarog.Sv;
import com.prtech.svarog_common.DbDataField;
import com.prtech.svarog_common.DbDataField.DbFieldType;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbDataTable;
import com.prtech.svarog_common.IDbInit;

public class DbInit implements IDbInit {

	static final String CONST_GUI_FIL_VIS_RES_RW = "{\"react\":{\"filterable\":true,\"visible\":true,\"resizable\":true,\"editable\":true}}";
	static final String CONST_MASTER_REPO = "{MASTER_REPO}";
	static final String CONST_DEFAULT_SCHEMA = "{DEFAULT_SCHEMA}";

	private static DbDataTable addSortOrder(DbDataTable dbtt) {
		Integer order = 100;
		if (dbtt.getDbTableFields() != null)
			for (DbDataField dbf : dbtt.getDbTableFields()) {
				if (dbf != null && dbf.getSort_order() == null) {
					dbf.setSort_order(order);
					order = order + 100;
				}
			}
		return dbtt;
	}

	public static DbDataTable form_wizard() {
		DbDataTable dbe = new DbDataTable();
		dbe.setDbTableName("form_wizard");
		dbe.setDbRepoName(CONST_MASTER_REPO);
		dbe.setDbSchema(CONST_DEFAULT_SCHEMA);
		dbe.setIsSystemTable(false);
		dbe.setIsRepoTable(false);
		dbe.setLabel_code("master_repo.form_wizard");
		dbe.setUse_cache(false);

		DbDataField dbe1 = new DbDataField();
		dbe1.setDbFieldName("PKID");
		dbe1.setIsPrimaryKey(true);
		dbe1.setDbFieldType(DbFieldType.NUMERIC);
		dbe1.setDbFieldSize(18);
		dbe1.setDbFieldScale(0);
		dbe1.setIsNull(false);
		dbe1.setLabel_code("form_wizard.pkid");

		DbDataField dbe2 = new DbDataField();
		dbe2.setDbFieldName("MULTISTEP_KEY");
		dbe2.setDbFieldType(DbFieldType.NVARCHAR);
		dbe2.setDbFieldSize(50);
		dbe1.setIsNull(false);
		dbe2.setLabel_code("form_wizard.multistep_key");

		DbDataField dbe3 = new DbDataField();
		dbe3.setDbFieldName("FORM_NAME");
		dbe3.setDbFieldType(DbFieldType.NVARCHAR);
		dbe3.setDbFieldSize(150);
		dbe1.setIsNull(false);
		dbe3.setLabel_code("form_wizard.form_name");

		DbDataField dbe4 = new DbDataField();
		dbe4.setDbFieldName("EXEC_ORDER");
		dbe4.setDbFieldType(DbFieldType.NUMERIC);
		dbe4.setDbFieldSize(10);
		dbe1.setIsNull(false);
		dbe4.setLabel_code("form_wizard.exec_order");

		DbDataField[] dbTableFields = new DbDataField[4];
		dbTableFields[0] = dbe1;
		dbTableFields[1] = dbe2;
		dbTableFields[2] = dbe3;
		dbTableFields[3] = dbe4;
		dbe.setDbTableFields(dbTableFields);
		return dbe;
	}

	public static DbDataTable cardConf() {
		DbDataTable dbe = new DbDataTable();
		dbe.setDbTableName("card_conf");
		dbe.setDbRepoName("{MASTER_REPO}");
		dbe.setDbSchema("{DEFAULT_SCHEMA}");
		dbe.setIsSystemTable(false);
		dbe.setIsRepoTable(false);
		dbe.setLabel_code("master_repo.card_conf");
		dbe.setUse_cache(false);

		DbDataField dbe1 = new DbDataField();
		dbe1.setDbFieldName("PKID");
		dbe1.setIsPrimaryKey(true);
		dbe1.setDbFieldType(DbFieldType.NUMERIC);
		dbe1.setDbFieldSize(18);
		dbe1.setDbFieldScale(0);
		dbe1.setIsNull(false);
		dbe1.setLabel_code("card_conf.pkid");

		DbDataField dbe2 = new DbDataField();
		dbe2.setDbFieldName("CARD_ID");
		dbe2.setDbFieldType(DbFieldType.NVARCHAR);
		dbe2.setIsUnique(true);
		dbe2.setDbFieldSize(50);
		dbe2.setIsNull(false);
		dbe2.setLabel_code("card_conf.card_id");

		DbDataField dbe3 = new DbDataField();
		dbe3.setDbFieldName("TITLE");
		dbe3.setDbFieldType(DbFieldType.NVARCHAR);
		dbe3.setDbFieldSize(30);
		dbe3.setIsNull(false);
		dbe3.setLabel_code("card_conf.card_title");

		DbDataField dbe4 = new DbDataField();
		dbe4.setDbFieldName("DESCR");
		dbe4.setDbFieldType(DbFieldType.NVARCHAR);
		dbe4.setDbFieldSize(100);
		dbe4.setIsNull(true);
		dbe4.setLabel_code("card_conf.card_descr");

		DbDataField dbe5 = new DbDataField();
		dbe5.setDbFieldName("IMG_PATH");
		dbe5.setDbFieldType(DbFieldType.NVARCHAR);
		dbe5.setDbFieldSize(100);
		dbe5.setIsNull(true);
		dbe5.setLabel_code("card_conf.img_path");

		DbDataField dbe6 = new DbDataField();
		dbe6.setDbFieldName("USER_GROUP_ID");
		dbe6.setDbFieldType(DbFieldType.NUMERIC);
		dbe6.setDbFieldSize(10);
		dbe6.setIsNull(false);
		dbe6.setLabel_code("card_conf.user_group_id");

		DbDataField[] dbTableFields = new DbDataField[6];
		dbTableFields[0] = dbe1;
		dbTableFields[1] = dbe2;
		dbTableFields[2] = dbe3;
		dbTableFields[3] = dbe4;
		dbTableFields[4] = dbe5;
		dbTableFields[5] = dbe6;
		dbe.setDbTableFields(dbTableFields);
		return dbe;
	}

	// perun table for dynamic menus
	private static DbDataTable createPerunMenuTable() {
		DbDataTable dbe = new DbDataTable();
		dbe.setDbTableName(CC.PERUN_MENU);
		dbe.setDbRepoName("{MASTER_REPO}");
		dbe.setDbSchema("{DEFAULT_SCHEMA}");
		dbe.setIsSystemTable(false);
		dbe.setIsRepoTable(false);
		dbe.setLabel_code("perun_menu");
		dbe.setUse_cache(false);
		dbe.setIsConfigTable(true);
		dbe.setConfigColumnName(Sv.MENU_CODE);

		DbDataField dbf1 = new DbDataField();
		dbf1.setDbFieldName("PKID");
		dbf1.setIsPrimaryKey(true);
		dbf1.setDbFieldType(DbFieldType.NUMERIC);
		dbf1.setDbFieldSize(18);
		dbf1.setDbFieldScale(0);
		dbf1.setIsNull(false);
		dbf1.setLabel_code("perun_menu.pkid");

		DbDataField dbf2 = new DbDataField();
		dbf2.setDbFieldName(CC.MENU_CODE);
		dbf2.setDbFieldType(DbFieldType.NVARCHAR);
		dbf2.setDbFieldSize(50);
		dbf2.setIsUnique(true);
		dbf2.setIsNull(false);
		dbf2.setLabel_code("perun_menu.menu_code");
		dbf2.setGui_metadata(
				"{\"react\":{\"filterable\":true,\"visible\":true,\"resizable\":true,\"editable\":true,\"width\":300}}");

		// codelist - enum MENU_TYPES
		DbDataField dbf4 = new DbDataField();
		dbf4.setDbFieldName(CC.MENU_TYPE);
		dbf4.setDbFieldType(DbFieldType.NVARCHAR);
		dbf4.setDbFieldSize(50);
		dbf4.setIsNull(true);
		dbf4.setCode_list_user_code("MENU_TYPES");
		dbf4.setLabel_code("perun_menu.menu_type");

		// json
		DbDataField dbf5 = new DbDataField();
		dbf5.setDbFieldName(CC.MENU_CONF);
		dbf5.setDbFieldType(DbFieldType.TEXT);
		dbf5.setIsNull(true);
		dbf5.setLabel_code("perun_menu.menu_conf");

		// label_code of svarog_acl
		DbDataField dbf8 = new DbDataField();
		dbf8.setDbFieldName(CC.SVAROG_ACL_LBL);
		dbf8.setDbFieldType(DbFieldType.NVARCHAR);
		dbf8.setDbFieldSize(100);
		dbf8.setIsNull(true);
		dbf8.setLabel_code("perun_menu.svarog_acl_label");
		dbf8.setGui_metadata(
				"{\"react\":{\"filterable\":true,\"visible\":true,\"resizable\":true,\"editable\":true,\"uischema\":{\"ui:widget\":\"hidden\"}}}");

		// internal category
		DbDataField dbf9 = new DbDataField();
		dbf9.setDbFieldName(CC.INTERNAL_CAT);
		dbf9.setDbFieldType(DbFieldType.NVARCHAR);
		dbf9.setDbFieldSize(200);
		dbf9.setIsNull(true);
		dbf9.setLabel_code("perun_menu.internal_cat");

		DbDataField dbf10 = new DbDataField();
		dbf10.setDbFieldName(CC.VERSION);
		dbf10.setDbFieldType(DbFieldType.NUMERIC);
		dbf10.setDbFieldSize(3);
		dbf10.setDbFieldScale(0);
		dbf10.setIsNull(true);
		dbf10.setLabel_code("perun_menu.version");
		dbf10.setGui_metadata(
				"{\"react\":{\"filterable\":true,\"visible\":true,\"resizable\":true,\"editable\":true,\"uischema\":{\"ui:widget\":\"hidden\"}}}");

		DbDataField[] dbTableFields = new DbDataField[7];
		dbTableFields[0] = dbf1;
		dbTableFields[1] = dbf2;
		dbTableFields[2] = dbf4;
		dbTableFields[3] = dbf5;
		dbTableFields[4] = dbf8;
		dbTableFields[5] = dbf9;
		dbTableFields[6] = dbf10;
		dbe.setDbTableFields(dbTableFields);
		return dbe;
	}

	private static DbDataTable createPerunMenuConfTable() {
		DbDataTable dbe = new DbDataTable();
		dbe.setDbTableName(CC.PERUN_MENU_CONF);
		dbe.setDbRepoName("{MASTER_REPO}");
		dbe.setDbSchema("{DEFAULT_SCHEMA}");
		dbe.setIsSystemTable(false);
		dbe.setIsRepoTable(false);
		dbe.setLabel_code("perun_menu_conf");
		dbe.setUse_cache(false);

		DbDataField dbf1 = new DbDataField();
		dbf1.setDbFieldName("PKID");
		dbf1.setIsPrimaryKey(true);
		dbf1.setDbFieldType(DbFieldType.NUMERIC);
		dbf1.setDbFieldSize(18);
		dbf1.setDbFieldScale(0);
		dbf1.setIsNull(false);
		dbf1.setLabel_code("perun_menu.pkid");

		DbDataField dbf2 = new DbDataField();
		dbf2.setDbFieldName(CC.TABLE_NAME);
		dbf2.setDbFieldType(DbFieldType.NVARCHAR);
		dbf2.setDbFieldSize(50);
		dbf2.setIsNull(false);
		dbf2.setLabel_code("perun_menu_conf.table_name");

		DbDataField dbf3 = new DbDataField();
		dbf3.setDbFieldName(CC.FIELD_NAME);
		dbf3.setDbFieldType(DbFieldType.NVARCHAR);
		dbf3.setDbFieldSize(50);
		dbf3.setIsNull(false);
		dbf3.setLabel_code("perun_menu_conf.field_name");

		DbDataField dbf4 = new DbDataField();
		dbf4.setDbFieldName(CC.REF_TABLE_NAME);
		dbf4.setDbFieldType(DbFieldType.NVARCHAR);
		dbf4.setDbFieldSize(50);
		dbf4.setIsNull(true);
		dbf4.setLabel_code("perun_menu_conf.ref_table_name");

		DbDataField dbf5 = new DbDataField();
		dbf5.setDbFieldName(CC.REF_FIELD_NAME);
		dbf5.setDbFieldType(DbFieldType.NVARCHAR);
		dbf5.setDbFieldSize(50);
		dbf5.setIsNull(true);
		dbf5.setLabel_code("perun_menu_conf.ref_field_name");

		DbDataField dbf6 = new DbDataField();
		dbf6.setDbFieldName(CC.CDL_NAME);
		dbf6.setDbFieldType(DbFieldType.NVARCHAR);
		dbf6.setDbFieldSize(50);
		dbf6.setIsNull(true);
		dbf6.setLabel_code("perun_menu_conf.cdl_name");

		DbDataField dbf7 = new DbDataField();
		dbf7.setDbFieldName(CC.CDL_ITEM_NAME);
		dbf7.setDbFieldType(DbFieldType.NVARCHAR);
		dbf7.setDbFieldSize(50);
		dbf7.setIsNull(false);
		dbf7.setLabel_code("perun_menu_conf.cdl_item_name");

		DbDataField dbf8 = new DbDataField();
		dbf8.setDbFieldName(CC.MENU_CODE);
		dbf8.setDbFieldType(DbFieldType.NVARCHAR);
		dbf8.setDbFieldSize(50);
		dbf8.setIsNull(true);
		dbf8.setLabel_code("perun_menu_conf.menu_code");

		DbDataField[] dbTableFields = new DbDataField[8];
		dbTableFields[0] = dbf1;
		dbTableFields[1] = dbf2;
		dbTableFields[2] = dbf3;
		dbTableFields[3] = dbf4;
		dbTableFields[4] = dbf5;
		dbTableFields[5] = dbf6;
		dbTableFields[6] = dbf7;
		dbTableFields[7] = dbf8;
		dbe.setDbTableFields(dbTableFields);
		return dbe;
	}

	private static DbDataTable createPerunMenuPlaceholderConfTable() {
		DbDataTable dbe = new DbDataTable();
		dbe.setDbTableName(CC.PERUN_MENU_PH_CONF);
		dbe.setDbRepoName("{MASTER_REPO}");
		dbe.setDbSchema("{DEFAULT_SCHEMA}");
		dbe.setIsSystemTable(false);
		dbe.setIsRepoTable(false);
		dbe.setLabel_code("perun_menu_ph_conf");
		dbe.setUse_cache(false);
		dbe.setIsConfigTable(true);
		dbe.setConfigColumnName(CC.PLACEHOLDER_NAME);

		DbDataField dbf1 = new DbDataField();
		dbf1.setDbFieldName("PKID");
		dbf1.setIsPrimaryKey(true);
		dbf1.setDbFieldType(DbFieldType.NUMERIC);
		dbf1.setDbFieldSize(18);
		dbf1.setDbFieldScale(0);
		dbf1.setIsNull(false);
		dbf1.setLabel_code("perun_menu_ph_conf.pkid");

		DbDataField dbf2 = new DbDataField();
		dbf2.setDbFieldName(CC.PLACEHOLDER_NAME);
		dbf2.setDbFieldType(DbFieldType.NVARCHAR);
		dbf2.setDbFieldSize(50);
		dbf2.setIsUnique(true);
		dbf2.setIsNull(false);
		dbf2.setLabel_code("perun_menu_ph_conf.placeholder_name");

		DbDataField dbf3 = new DbDataField();
		dbf3.setDbFieldName(CC.SOURCE_FIELD);
		dbf3.setDbFieldType(DbFieldType.NVARCHAR);
		dbf3.setDbFieldSize(50);
		dbf3.setIsNull(false);
		dbf3.setLabel_code("perun_menu_ph_conf.source_field");

		DbDataField dbf4 = new DbDataField();
		dbf4.setDbFieldName(CC.REF_TABLE_NAME);
		dbf4.setDbFieldType(DbFieldType.NVARCHAR);
		dbf4.setDbFieldSize(50);
		dbf4.setIsNull(false);
		dbf4.setLabel_code("perun_menu_ph_conf.ref_table_name");

		DbDataField dbf5 = new DbDataField();
		dbf5.setDbFieldName(CC.REF_FIELD_NAME);
		dbf5.setDbFieldType(DbFieldType.NVARCHAR);
		dbf5.setDbFieldSize(50);
		dbf5.setIsNull(false);
		dbf5.setLabel_code("perun_menu_ph_conf.ref_field_name");

		DbDataField[] dbTableFields = new DbDataField[5];
		dbTableFields[0] = dbf1;
		dbTableFields[1] = dbf2;
		dbTableFields[2] = dbf3;
		dbTableFields[3] = dbf4;
		dbTableFields[4] = dbf5;
		dbe.setDbTableFields(dbTableFields);
		return dbe;
	}
	
	// Create Sequence Pattern Configuration Table
	private static DbDataTable createSequencePattern() {
		DbDataTable dbe = new DbDataTable();
		dbe.setDbTableName(CC.SV_ID_SEQ_PATTERN);
		dbe.setDbRepoName("{MASTER_REPO}");
		dbe.setDbSchema("{DEFAULT_SCHEMA}");
		dbe.setIsSystemTable(false);
		dbe.setIsRepoTable(false);
		dbe.setLabel_code("sv_id_seq_pattern");
		dbe.setUse_cache(false);

		DbDataField dbf1 = new DbDataField();
		dbf1.setDbFieldName("PKID");
		dbf1.setIsPrimaryKey(true);
		dbf1.setDbFieldType(DbFieldType.NUMERIC);
		dbf1.setDbFieldSize(18);
		dbf1.setDbFieldScale(0);
		dbf1.setIsNull(false);
		dbf1.setLabel_code("sv_id_seq_pattern.pkid");
		
		DbDataField dbf2 = new DbDataField();
		dbf2.setDbFieldName(CC.SEQ_PATTERN_ID);
		dbf2.setDbFieldType(DbFieldType.NVARCHAR);
		dbf2.setDbFieldScale(0);
		dbf2.setDbFieldSize(80);
		dbf2.setIsNull(false);
		dbf2.setIsUnique(true);
		dbf2.setLabel_code("sv_id_seq_pattern.seq_pattern_id");

		DbDataField dbf3 = new DbDataField();
		dbf3.setDbFieldName(CC.CONF_TABLE);
		dbf3.setDbFieldType(DbFieldType.NVARCHAR);
		dbf3.setDbFieldSize(60);
		dbf3.setIsNull(false);
		dbf3.setLabel_code("sv_id_seq_pattern.conf_table");

		DbDataField dbf4 = new DbDataField();
		dbf4.setDbFieldName(CC.DEST_FIELD);
		dbf4.setDbFieldType(DbFieldType.NVARCHAR);
		dbf4.setDbFieldSize(60);
		dbf4.setIsNull(false);
		dbf4.setLabel_code("sv_id_seq_pattern.dest_field");

		DbDataField dbf5 = new DbDataField();
		dbf5.setDbFieldName(CC.SEQ_PATTERN);
		dbf5.setDbFieldType(DbFieldType.NVARCHAR);
		dbf5.setDbFieldSize(500);
		dbf5.setIsNull(false);
		dbf5.setLabel_code("sv_id_seq_pattern.seq_pattern");

		DbDataField dbf6 = new DbDataField();
		dbf6.setDbFieldName(CC.COND_FIELD);
		dbf6.setDbFieldType(DbFieldType.NVARCHAR);
		dbf6.setDbFieldSize(60);
		dbf6.setIsNull(true);
		dbf6.setLabel_code("sv_id_seq_pattern.cond_field");

		DbDataField dbf7 = new DbDataField();
		dbf7.setDbFieldName(CC.COND_OPERATOR);
		dbf7.setDbFieldType(DbFieldType.NVARCHAR);
		dbf7.setDbFieldSize(10);
		dbf7.setIsNull(true);
		dbf7.setCode_list_user_code("COND_OPERATOR");
		dbf7.setLabel_code("sv_id_seq_pattern.cond_operator");

		DbDataField dbf8 = new DbDataField();
		dbf8.setDbFieldName(CC.COND_VALUE);
		dbf8.setDbFieldType(DbFieldType.NVARCHAR);
		dbf8.setDbFieldSize(500);
		dbf8.setIsNull(true);
		dbf8.setLabel_code("sv_id_seq_pattern.cond_value");

		DbDataField dbf9 = new DbDataField();
		dbf9.setDbFieldName(CC.IS_DEFAULT);
		dbf9.setDbFieldType(DbFieldType.BOOLEAN);
		dbf9.setIsNull(false);
		dbf9.setLabel_code("sv_id_seq_pattern.is_default");

		DbDataField[] dbTableFields = new DbDataField[9];
		dbTableFields[0] = dbf1;
		dbTableFields[1] = dbf2;
		dbTableFields[2] = dbf3;
		dbTableFields[3] = dbf4;
		dbTableFields[4] = dbf5;
		dbTableFields[5] = dbf6;
		dbTableFields[6] = dbf7;
		dbTableFields[7] = dbf8;
		dbTableFields[8] = dbf9;
		dbe.setDbTableFields(dbTableFields);
		return dbe;
	}

	@Override
	public ArrayList<DbDataTable> getCustomObjectTypes() {
		DbDataTable dbtt = null;
		ArrayList<DbDataTable> dbtList = new ArrayList<DbDataTable>();
		dbtt = DbInit.form_wizard();
		dbtList.add(addSortOrder(dbtt));
		dbtt = DbInit.cardConf();
		dbtList.add(addSortOrder(dbtt));
		dbtt = DbInit.createPerunMenuTable();
		dbtList.add(addSortOrder(dbtt));
		dbtt = DbInit.createPerunMenuConfTable();
		dbtList.add(addSortOrder(dbtt));
		dbtt = DbInit.createPerunMenuPlaceholderConfTable();
		dbtList.add(addSortOrder(dbtt));
		//dbtt = DbInit.createSequencePattern();
		//dbtList.add(addSortOrder(dbtt));
		return dbtList;
	}

	@Override
	public ArrayList<DbDataObject> getCustomObjectInstances() {
		ArrayList<DbDataObject> dbtList = new ArrayList<DbDataObject>();
		return dbtList;
	}

}
