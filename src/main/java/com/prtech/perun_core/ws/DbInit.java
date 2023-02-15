package com.prtech.perun_core.ws;

import java.util.ArrayList;

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
		dbe.setDbRepoName("{MASTER_REPO}");
		dbe.setDbSchema("{DEFAULT_SCHEMA}");
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

	@Override
	public ArrayList<DbDataTable> getCustomObjectTypes() {
		DbDataTable dbtt = null;
		ArrayList<DbDataTable> dbtList = new ArrayList<DbDataTable>();
		dbtt = DbInit.form_wizard();
		dbtList.add(addSortOrder(dbtt));
		dbtt = DbInit.cardConf();
		dbtList.add(addSortOrder(dbtt));
		return dbtList;
	}

	@Override
	public ArrayList<DbDataObject> getCustomObjectInstances() {
		DbDataObject dbtt = null;
		ArrayList<DbDataObject> dbtList = new ArrayList<DbDataObject>();
		return dbtList;
	}

}
