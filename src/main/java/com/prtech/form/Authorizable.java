package com.prtech.form;

import java.util.List;

/**
 * Interface for objects that require authorization checks based on user group
 * membership or ACL (Access Control List) permissions
 */
public interface Authorizable {

	/**
	 * Returns the list of user group names that have read permission.
	 * TODO: Read from DB
	 * 
	 * @return list of group names
	 */
	List<String> getReadPermissions();

	/**
	 * Returns the list of user group names that have write permission.
	 * TODO: Read from DB
	 * 
	 * @return list of group names
	 */
	List<String> getWritePermissions();

	/**
	 * Returns the ACL key for read operations. If both group permissions and ACL
	 * key are defined, user must satisfy either condition.
	 * 
	 * @return ACL key string
	 */
	default String getReadAclKey() {
		return null;
	}

	/**
	 * Returns the ACL key for write operations. If both group permissions and ACL
	 * key are defined, user must satisfy either condition.
	 * 
	 * @return ACL key string
	 */
	default String getWriteAclKey() {
		return null;
	}
}
