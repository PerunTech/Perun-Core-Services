package com.prtech.reports;

import org.joda.time.DateTime;

import com.prtech.svarog.SvException;
import com.prtech.svarog.SvLink;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchExpression;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;

public class HelperMethods {
	public static DbDataArray getLinks(Long objId1, Long objType1, Long objId2, Long objType2, String linkType,
			Boolean useCache, SvReader svr) throws SvException {
		DbDataObject linkDbo = SvLink.getLinkType(linkType, objType1, objType2);
		DbDataArray resultArr = new DbDataArray();
		DbSearchExpression dbse = new DbSearchExpression();
		if (objId1 != null) {
			DbSearchCriterion cr1 = new DbSearchCriterion(CC.LINK_OBJ_ID_1, DbCompareOperand.EQUAL, objId1);
			dbse.addDbSearchItem(cr1);
		}
		if (objId2 != null) {
			DbSearchCriterion cr2 = new DbSearchCriterion(CC.LINK_OBJ_ID_2, DbCompareOperand.EQUAL, objId2);
			dbse.addDbSearchItem(cr2);
		}
		DbSearchCriterion cr3 = new DbSearchCriterion(CC.LINK_TYPE_ID, DbCompareOperand.EQUAL, linkDbo.getObjectId());
		dbse.addDbSearchItem(cr3);
		if (useCache) {
			resultArr = svr.getObjects(dbse, svCONST.OBJECT_TYPE_LINK, null, 0, 0);
		} else {
			resultArr = svr.getObjects(dbse, svCONST.OBJECT_TYPE_LINK, new DateTime(), 0, 0);
		}
		return resultArr;
	}
}
