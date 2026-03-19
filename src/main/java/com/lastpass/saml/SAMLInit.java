package com.lastpass.saml;

import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;

/**
 * Library initialization routines.
 *
 * Applications must call SAMLInit.initialize() before anything else!
 */
public class SAMLInit {

    protected SAMLInit() {
    }

    public static void initialize() throws SAMLException {
        try {
            InitializationService.initialize();
        } catch (InitializationException e) {
            throw new SAMLException(e);
        }
    }
}