package com.lastpass.saml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.PrivateKey;

import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;
import net.shibboleth.utilities.java.support.xml.XMLParserException;

import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * SPConfig contains basic information about the service that is asking for
 * authorization. This information is put into the auth request sent to the IdP.
 */
public class SPConfig {
    /** From whom requests are sent */
    private String entityId;

    /** Where the assertions are sent */
    private String acs;

    /** Where the logout results are sent */
    private String logoutResult;

    /** Where the logout request are sent */
    private String logoutRequest;

    /** Private key used for decrypting assertions */
    private PrivateKey privateKey;

    public String getLogoutResult() {
        return logoutResult;
    }

    public void setLogoutResult(String logoutResult) {
        this.logoutResult = logoutResult;
    }

    public String getLogoutRequest() {
        return logoutRequest;
    }

    public void setLogoutRequest(String logoutRequest) {
        this.logoutRequest = logoutRequest;
    }

    /**
     * Construct a new, empty SPConfig.
     */
    public SPConfig() {
    }

    /**
     * Construct a new SPConfig from a metadata XML file.
     *
     * @param metadataFile File where the metadata lives
     * @throws SAMLException if an error occurs while parsing the metadata
     */
    public SPConfig(File metadataFile) throws SAMLException {
        FileInputStream inputStream;
        try {
            inputStream = new FileInputStream(metadataFile);
        } catch (java.io.IOException e) {
            throw new SAMLException(e);
        }

        try {
            init(inputStream);
        } finally {
            try {
                inputStream.close();
            } catch (java.io.IOException e) {
                // Ignore
            }
        }
    }

    /**
     * Construct a new SPConfig from a metadata XML input stream.
     *
     * @param inputStream An input stream containing a metadata XML document
     * @throws SAMLException if an error occurs while parsing the metadata
     */
    public SPConfig(InputStream inputStream) throws SAMLException {
        init(inputStream);
    }

    private void init(InputStream inputStream) throws SAMLException {
        BasicParserPool parsers = new BasicParserPool();
        parsers.setNamespaceAware(true);

        try {
            parsers.initialize();
        } catch (ComponentInitializationException e) {
            throw new SAMLException(e);
        }

        EntityDescriptor edesc;

        try {
            Document doc = parsers.parse(inputStream);
            Element root = doc.getDocumentElement();

            UnmarshallerFactory unmarshallerFactory =
                    XMLObjectProviderRegistrySupport.getUnmarshallerFactory();

            edesc = (EntityDescriptor) unmarshallerFactory
                    .getUnmarshaller(root)
                    .unmarshall(root);

        } catch (XMLParserException e) {
            throw new SAMLException(e);
        } catch (UnmarshallingException e) {
            throw new SAMLException(e);
        }

        SPSSODescriptor spDesc =
                edesc.getSPSSODescriptor(SAMLConstants.SAML20P_NS);

        if (spDesc == null) {
            throw new SAMLException("No SP SSO descriptor found");
        }

        String acsUrl = null;
        for (AssertionConsumerService svc : spDesc.getAssertionConsumerServices()) {
            if (SAMLConstants.SAML2_REDIRECT_BINDING_URI.equals(svc.getBinding())
                    || SAMLConstants.SAML2_POST_BINDING_URI.equals(svc.getBinding())) {
                acsUrl = svc.getLocation();
                break;
            }
        }

        if (acsUrl == null) {
            throw new SAMLException("No acceptable Assertion Consumer Service found");
        }

        this.setEntityId(edesc.getEntityID());
        this.setAcs(acsUrl);
    }

    /**
     * Set the SP Entity Id.
     */
    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    /**
     * Get the SP Entity Id.
     */
    public String getEntityId() {
        return this.entityId;
    }

    /**
     * Set the SP ACS URL. Auth responses are posted here.
     */
    public void setAcs(String acs) {
        this.acs = acs;
    }

    /**
     * Get the SP ACS URL.
     */
    public String getAcs() {
        return this.acs;
    }

    /**
     * Set private key used for decrypting assertions.
     */
    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    /**
     * Get private key used for decrypting assertions.
     */
    public PrivateKey getPrivateKey() {
        return this.privateKey;
    }
}