package com.prtech.form;

import java.util.List;

import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;

public class AuthorizationHelper {
	public static boolean canRead(Authorizable authorizable, SvReader svr) throws SvException {
		return checkPermission(authorizable, svr, true);
	}

	public static boolean canWrite(Authorizable authorizable, SvReader svr) throws SvException {
		return checkPermission(authorizable, svr, false);
	}

	private static boolean checkPermission(Authorizable authorizable, SvReader svr, boolean isRead) throws SvException {
		List<String> groupPermissions = isRead ? authorizable.getReadPermissions() : authorizable.getWritePermissions();
		String aclKey = isRead ? authorizable.getReadAclKey() : authorizable.getWriteAclKey();

		if ((groupPermissions == null || groupPermissions.isEmpty()) && (aclKey == null || aclKey.isBlank())) {
			return true;
		}

		boolean hasGroupPermission = false;
		boolean hasAclPermission = false;

		hasGroupPermission = groupPermissions!=null && checkUserGroupAnyMembership(svr, groupPermissions);

		if (!hasGroupPermission && aclKey!=null) {
			hasAclPermission = svr.hasPermission(aclKey);
		}

		return hasGroupPermission || hasAclPermission;
	}

	public static boolean checkUserGroupAnyMembership(SvReader svr, List<String> groups) throws SvException {
		boolean result = false;
		if (groups == null) {
			return result;
		}
		DbDataArray userGroups = svr.getUserGroups();

		for (DbDataObject group : userGroups.getItems()) {
			String groupName = group.getVal(CC.GROUP_NAME).toString();
			if (groups.contains(groupName)) {
				result = true;
				break;
			}
		}

		return result;
	}
}
