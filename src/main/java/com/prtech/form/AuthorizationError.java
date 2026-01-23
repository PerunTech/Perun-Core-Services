package com.prtech.form;

public class AuthorizationError extends Exception {
	private static final long serialVersionUID = 1L;

	public AuthorizationError(String message) {
        super(message);
    }
    
    public AuthorizationError(String message, Throwable cause) {
        super(message, cause);
    }
}
