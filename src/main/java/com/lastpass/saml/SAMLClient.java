/*
 * SAMLClient - Main interface module for service providers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * Copyright (c) 2014-2015 LastPass, Inc.
 */
package com.lastpass.saml;

import org.opensaml.xmlsec.signature.support.SignatureValidator;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;
import net.shibboleth.utilities.java.support.xml.XMLParserException;

import org.joda.time.DateTime;

import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.XMLObjectBuilder;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.core.xml.io.UnmarshallingException;

import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.Audience;
import org.opensaml.saml.saml2.core.AudienceRestriction;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.EncryptedAssertion;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.LogoutResponse;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.SessionIndex;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;
import org.opensaml.saml.saml2.encryption.Decrypter;

import org.opensaml.security.SecurityException;
import org.opensaml.security.credential.BasicCredential;
import org.opensaml.security.x509.BasicX509Credential;

import org.opensaml.xmlsec.SignatureSigningParameters;
import org.opensaml.xmlsec.encryption.support.DecryptionException;
import org.opensaml.xmlsec.encryption.support.InlineEncryptedKeyResolver;
import org.opensaml.xmlsec.keyinfo.KeyInfoGenerator;
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver;
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.impl.SignatureBuilder;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureSupport;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.opensaml.xmlsec.signature.support.Signer;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

import org.opensaml.security.credential.BasicCredential;
import org.opensaml.security.credential.CredentialSupport;

/**
 * A SAMLClient acts as on behalf of a SAML Service Provider to generate
 * requests and process responses.
 *
 * To integrate a service, one must generally do the following:
 *
 * 1. Change the login process to call generateAuthnRequest() to get a request
 * and link, and then GET/POST that to the IdP login URL.
 *
 * 2. Create a new URL that acts as the AssertionConsumerService -- it will call
 * validateResponse on the response body to verify the assertion; on success it
 * will use the subject as the authenticated user for the web application.
 *
 * The specific changes needed to the application are outside the scope of this
 * SDK.
 */
public class SAMLClient {

	private SPConfig spConfig;
	private IdPConfig idpConfig;
	private BasicParserPool parsers;
	private X509Certificate entityCertificate;
	private boolean requireSignedAssertion = true;
	private BasicCredential idpCredential;

	public boolean isRequireSignedAssertion() {
		return requireSignedAssertion;
	}

	public void setRequireSignedAssertion(boolean requireSignedAssertion) {
		this.requireSignedAssertion = requireSignedAssertion;
	}

	/* do date comparisons +/- this many seconds */
	private static int slack = (int) TimeUnit.MINUTES.toSeconds(5L);

	public static int getSlack() {
		return slack;
	}

	public static void setSlack(int slack) {
		SAMLClient.slack = slack;
	}

	/**
	 * Create a new SAMLClient, using the IdPConfig for endpoints and validation.
	 */
	public SAMLClient(SPConfig spConfig, IdPConfig idpConfig) throws SAMLException {
		this.spConfig = spConfig;
		this.idpConfig = idpConfig;

		BasicCredential cred = CredentialSupport.getSimpleCredential(idpConfig.getCert().getPublicKey(), null);
		cred.setEntityId(idpConfig.getEntityId());

		parsers = new BasicParserPool();
		parsers.setNamespaceAware(true);

		this.idpCredential = CredentialSupport.getSimpleCredential(idpConfig.getCert().getPublicKey(), null);
		this.idpCredential.setEntityId(idpConfig.getEntityId());

		try {
			parsers.initialize();
		} catch (ComponentInitializationException e) {
			throw new SAMLException(e);
		}
	}

	public X509Certificate getEntityCertificate() {
		return entityCertificate;
	}

	public void setEntityCertificate(X509Certificate entityCertificate) {
		this.entityCertificate = entityCertificate;
	}

	/**
	 * Get the configured IdpConfig.
	 *
	 * @return the IdPConfig associated with this client
	 */
	public IdPConfig getIdPConfig() {
		return idpConfig;
	}

	/**
	 * Get the configured SPConfig.
	 *
	 * @return the SPConfig associated with this client
	 */
	public SPConfig getSPConfig() {
		return spConfig;
	}

	private Response parseResponse(String authnResponse) throws SAMLException {
		try {
			Document doc = parsers.getBuilder().parse(new InputSource(new StringReader(authnResponse)));
			Element root = doc.getDocumentElement();

			UnmarshallerFactory unmarshallerFactory = XMLObjectProviderRegistrySupport.getUnmarshallerFactory();

			return (Response) unmarshallerFactory.getUnmarshaller(root).unmarshall(root);
		} catch (XMLParserException | UnmarshallingException | SAXException | IOException e) {
			throw new SAMLException(e);
		}
	}

	private LogoutResponse parseLogoutResponse(String authnResponse) throws SAMLException {
		try {
			Document doc = parsers.getBuilder().parse(new InputSource(new StringReader(authnResponse)));
			Element root = doc.getDocumentElement();

			UnmarshallerFactory unmarshallerFactory = XMLObjectProviderRegistrySupport.getUnmarshallerFactory();

			return (LogoutResponse) unmarshallerFactory.getUnmarshaller(root).unmarshall(root);
		} catch (XMLParserException | UnmarshallingException | SAXException | IOException e) {
			throw new SAMLException(e);
		}
	}

	private LogoutRequest parseLogoutRequest(String authnRequest) throws SAMLException {
		try {
			Document doc = parsers.getBuilder().parse(new InputSource(new StringReader(authnRequest)));
			Element root = doc.getDocumentElement();

			UnmarshallerFactory unmarshallerFactory = XMLObjectProviderRegistrySupport.getUnmarshallerFactory();

			return (LogoutRequest) unmarshallerFactory.getUnmarshaller(root).unmarshall(root);
		} catch (XMLParserException | UnmarshallingException | SAXException | IOException e) {
			throw new SAMLException(e);
		}
	}

	/**
	 * Decrypt an assertion using the privkey stored in SPConfig.
	 */
	private Assertion decrypt(EncryptedAssertion encrypted) throws DecryptionException {
		if (spConfig.getPrivateKey() == null) {
			throw new DecryptionException("Encrypted assertion found but no SP key available");
		}
		BasicCredential cred = CredentialSupport.getSimpleCredential(idpConfig.getCert().getPublicKey(), null);
		cred.setEntityId(idpConfig.getEntityId());
		cred.setPrivateKey(spConfig.getPrivateKey());

		StaticKeyInfoCredentialResolver resolver = new StaticKeyInfoCredentialResolver(cred);
		Decrypter decrypter = new Decrypter(null, resolver, new InlineEncryptedKeyResolver());
		decrypter.setRootInNewDocument(true);

		return decrypter.decrypt(encrypted);
	}

	/**
	 * Retrieve all supplied assertions, decrypting any encrypted assertions if
	 * necessary.
	 */
	private List<Assertion> getAssertions(Response response) throws DecryptionException {
		List<Assertion> assertions = new ArrayList<Assertion>();
		assertions.addAll(response.getAssertions());

		for (EncryptedAssertion e : response.getEncryptedAssertions()) {
			assertions.add(decrypt(e));
		}

		return assertions;
	}

	private void validateLogout(LogoutResponse response) throws SAMLException, SignatureException {
		// response signature must match IdP's key, if present
		Signature sig = response.getSignature();
		if (sig != null)
			SignatureValidator.validate(sig, idpCredential);

		// response must be successful
		if (response.getStatus() == null || response.getStatus().getStatusCode() == null
				|| !(StatusCode.SUCCESS.equals(response.getStatus().getStatusCode().getValue()))) {
			throw new SAMLException("Response has an unsuccessful status code");
		}

		// response destination must match ACS
		if (!spConfig.getLogoutResult().equals(response.getDestination()))
			throw new SAMLException("Response is destined for a different endpoint");

		DateTime now = DateTime.now();

		// issue instant must be within a day
		DateTime issueInstant = response.getIssueInstant();

		if (issueInstant != null) {
			if (issueInstant.isBefore(now.minusSeconds(slack)))
				throw new SAMLException("Response IssueInstant is in the past");

			if (issueInstant.isAfter(now.plusSeconds(slack)))
				throw new SAMLException("Response IssueInstant is in the future");
		}

	}

	private void validateLogoutRequest(LogoutRequest request) throws SAMLException, SignatureException {
		// response signature must match IdP's key, if present
		Signature sig = request.getSignature();
		if (sig != null)
			SignatureValidator.validate(sig, idpCredential);

		// response destination must match ACS
		if (!spConfig.getLogoutRequest().equals(request.getDestination()))
			throw new SAMLException("Response is destined for a different endpoint");

		DateTime now = DateTime.now();

		// issue instant must be within a day
		DateTime issueInstant = request.getIssueInstant();

		if (issueInstant != null) {
			if (issueInstant.isBefore(now.minusSeconds(slack)))
				throw new SAMLException("Response IssueInstant is in the past");

			if (issueInstant.isAfter(now.plusSeconds(slack)))
				throw new SAMLException("Response IssueInstant is in the future");
		}

	}

	private void validate(Response response) throws SAMLException, SignatureException {
		// response signature must match IdP's key, if present
		Signature sig = response.getSignature();
		if (sig != null)
			SignatureValidator.validate(sig, idpCredential);

		// response must be successful
		if (response.getStatus() == null || response.getStatus().getStatusCode() == null
				|| !(StatusCode.SUCCESS.equals(response.getStatus().getStatusCode().getValue()))) {
			throw new SAMLException("Response has an unsuccessful status code");
		}

		// response destination must match ACS
		if (!spConfig.getAcs().equals(response.getDestination()))
			throw new SAMLException("Response is destined for a different endpoint");

		DateTime now = DateTime.now();

		// issue instant must be within a day
		DateTime issueInstant = response.getIssueInstant();

		if (issueInstant != null) {
			if (issueInstant.isBefore(now.minusSeconds(slack)))
				throw new SAMLException("Response IssueInstant is in the past");

			if (issueInstant.isAfter(now.plusSeconds(slack)))
				throw new SAMLException("Response IssueInstant is in the future");
		}

		List<Assertion> assertions = null;
		try {
			assertions = getAssertions(response);
		} catch (DecryptionException e) {
			throw new SAMLException(e);
		}

		for (Assertion assertion : assertions) {

			// Assertion must be signed correctly
			if (requireSignedAssertion && !assertion.isSigned())
				throw new SAMLException("Assertion must be signed");

			sig = assertion.getSignature();
			if (sig != null)
				SignatureValidator.validate(sig, idpCredential);

			// Assertion must contain an authnstatement
			// with an unexpired session
			if (assertion.getAuthnStatements().isEmpty()) {
				throw new SAMLException("Assertion should contain an AuthnStatement");
			}
			for (AuthnStatement as : assertion.getAuthnStatements()) {
				DateTime sessionTime = as.getSessionNotOnOrAfter();
				if (sessionTime != null) {
					DateTime exp = sessionTime.plusSeconds(slack);
					if (exp != null && (now.isEqual(exp) || now.isAfter(exp)))
						throw new SAMLException("AuthnStatement has expired");
				}
			}

			if (assertion.getConditions() == null) {
				throw new SAMLException("Assertion should contain conditions");
			}

			// Assertion IssueInstant must be within a day
			DateTime instant = assertion.getIssueInstant();
			if (instant != null) {
				if (instant.isBefore(now.minusSeconds(slack)))
					throw new SAMLException("Response IssueInstant is in the past");

				if (instant.isAfter(now.plusSeconds(slack)))
					throw new SAMLException("Response IssueInstant is in the future");
			}

			// Conditions must be met by current time
			Conditions conditions = assertion.getConditions();
			DateTime notBefore = conditions.getNotBefore();
			DateTime notOnOrAfter = conditions.getNotOnOrAfter();

			if (notBefore == null || notOnOrAfter == null)
				throw new SAMLException("Assertion conditions must have limits");

			notBefore = notBefore.minusSeconds(slack);
			notOnOrAfter = notOnOrAfter.plusSeconds(slack);

			if (now.isBefore(notBefore))
				throw new SAMLException("Assertion conditions is in the future");

			if (now.isEqual(notOnOrAfter) || now.isAfter(notOnOrAfter))
				throw new SAMLException("Assertion conditions is in the past");

			// If subjectConfirmationData is included, it must
			// have a recipient that matches ACS, with a valid
			// NotOnOrAfter
			Subject subject = assertion.getSubject();
			if (subject != null && !subject.getSubjectConfirmations().isEmpty()) {
				boolean foundRecipient = false;
				for (SubjectConfirmation sc : subject.getSubjectConfirmations()) {
					if (sc.getSubjectConfirmationData() == null)
						continue;

					SubjectConfirmationData scd = sc.getSubjectConfirmationData();
					if (scd.getNotOnOrAfter() != null) {
						DateTime chkdate = scd.getNotOnOrAfter().plusSeconds(slack);
						if (now.isEqual(chkdate) || now.isAfter(chkdate)) {
							throw new SAMLException("SubjectConfirmationData is in the past");
						}
					}

					if (spConfig.getAcs().equals(scd.getRecipient()))
						foundRecipient = true;
				}

				if (!foundRecipient)
					throw new SAMLException("No SubjectConfirmationData found for ACS");
			}

			// audience must include intended SP issuer
			if (conditions.getAudienceRestrictions().isEmpty())
				throw new SAMLException("Assertion conditions must have audience restrictions");

			// only one audience restriction supported: we can only
			// check against the single SP.
			if (conditions.getAudienceRestrictions().size() > 1)
				throw new SAMLException("Assertion contains multiple audience restrictions");

			AudienceRestriction ar = conditions.getAudienceRestrictions().get(0);

			// at least one of the audiences must match our SP
			boolean foundSP = false;
			for (Audience a : ar.getAudiences()) {
				if (spConfig.getEntityId().equals(a.getAudienceURI()))
					foundSP = true;
			}
			if (!foundSP)
				throw new SAMLException("Assertion audience does not include issuer");
		}
	}

	private static X509Certificate getCert(String filename)
			throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, CertificateException {
		// Read in the key into a String

		CertificateFactory fac = CertificateFactory.getInstance("X509");
		FileInputStream is = new FileInputStream(filename);
		X509Certificate cert = (X509Certificate) fac.generateCertificate(is);
		return cert;
	}

	@SuppressWarnings("unchecked")
	private <T extends XMLObject> XMLObjectBuilder<T> getBuilder(javax.xml.namespace.QName elementName)
			throws SAMLException {
		XMLObjectBuilderFactory builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory();
		XMLObjectBuilder<T> builder = (XMLObjectBuilder<T>) builderFactory.getBuilder(elementName);
		if (builder == null) {
			throw new SAMLException("No builder found for " + elementName);
		}
		return builder;
	}

	private Element marshall(XMLObject xmlObject) throws SAMLException {
		try {
			return XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(xmlObject).marshall(xmlObject);
		} catch (MarshallingException e) {
			throw new SAMLException(e);
		}
	}

	private String toXmlString(Element elem) {
		Document document = elem.getOwnerDocument();
		DOMImplementationLS domImplLS = (DOMImplementationLS) document.getImplementation();
		LSSerializer serializer = domImplLS.createLSSerializer();
		serializer.getDomConfig().setParameter("xml-declaration", false);
		return serializer.writeToString(elem);
	}

	private Signature buildSignature(BasicX509Credential cred) throws SAMLException {
		try {
			SignatureBuilder signFactory = new SignatureBuilder();
			Signature signature = signFactory.buildObject(Signature.DEFAULT_ELEMENT_NAME);

			SignatureSigningParameters signingParameters = new SignatureSigningParameters();
			signingParameters.setSigningCredential(cred);
			signingParameters.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
			signingParameters.setSignatureCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

			SignatureSupport.prepareSignatureParams(signature, signingParameters);

			X509KeyInfoGeneratorFactory keyInfoFactory = new X509KeyInfoGeneratorFactory();
			keyInfoFactory.setEmitEntityCertificate(true);

			KeyInfoGenerator keyInfoGenerator = keyInfoFactory.newInstance();
			signature.setKeyInfo(keyInfoGenerator.generate(cred));

			return signature;
		} catch (SecurityException e) {
			throw new SAMLException(e);
		}
	}

	private BasicX509Credential buildSigningCredential() {
		BasicX509Credential cred = new BasicX509Credential(entityCertificate);
		cred.setPrivateKey(spConfig.getPrivateKey());
		cred.setEntityId(spConfig.getEntityId());
		cred.setPrivateKey(spConfig.getPrivateKey());
		cred.setEntityCertificate(entityCertificate);
		return cred;
	}

	private String createLogoutRequest(String requestId, String nameId, String sessionIndex) throws SAMLException,
			NoSuchAlgorithmException, InvalidKeySpecException, CertificateException, IOException, SecurityException {

		XMLObjectBuilder<LogoutRequest> builder = getBuilder(LogoutRequest.DEFAULT_ELEMENT_NAME);
		XMLObjectBuilder<Issuer> issuerBuilder = getBuilder(Issuer.DEFAULT_ELEMENT_NAME);
		XMLObjectBuilder<NameID> nameIdBuilder = getBuilder(NameID.DEFAULT_ELEMENT_NAME);
		XMLObjectBuilder<SessionIndex> sesIndexBuilder = getBuilder(SessionIndex.DEFAULT_ELEMENT_NAME);

		LogoutRequest request = builder.buildObject(LogoutRequest.DEFAULT_ELEMENT_NAME);
		request.setDestination(idpConfig.getLogoutUrl().toString());
		request.setIssueInstant(new DateTime());
		request.setID(requestId);

		NameID n = nameIdBuilder.buildObject(NameID.DEFAULT_ELEMENT_NAME);
		n.setValue(nameId);
		request.setNameID(n);

		SessionIndex s = sesIndexBuilder.buildObject(SessionIndex.DEFAULT_ELEMENT_NAME);
		s.setSessionIndex(sessionIndex);
		request.getSessionIndexes().add(s);

		Issuer issuer = issuerBuilder.buildObject(Issuer.DEFAULT_ELEMENT_NAME);
		issuer.setValue(spConfig.getEntityId());
		request.setIssuer(issuer);

		BasicX509Credential cred = buildSigningCredential();
		Signature signature = buildSignature(cred);
		request.setSignature(signature);

		Element elem = marshall(request);

		try {
			Signer.signObject(signature);
		} catch (SignatureException e) {
			throw new SAMLException(e);
		}

		return toXmlString(elem);
	}

	private String createLogoutResponse(String requestId, String inResponseTo, String statusCode) throws SAMLException,
			NoSuchAlgorithmException, InvalidKeySpecException, CertificateException, IOException, SecurityException {

		XMLObjectBuilder<LogoutResponse> builder = getBuilder(LogoutResponse.DEFAULT_ELEMENT_NAME);
		XMLObjectBuilder<Issuer> issuerBuilder = getBuilder(Issuer.DEFAULT_ELEMENT_NAME);
		XMLObjectBuilder<Status> statusBuilder = getBuilder(Status.DEFAULT_ELEMENT_NAME);
		XMLObjectBuilder<StatusCode> statusCodeBuilder = getBuilder(StatusCode.DEFAULT_ELEMENT_NAME);

		LogoutResponse response = builder.buildObject(LogoutResponse.DEFAULT_ELEMENT_NAME);
		response.setDestination(spConfig.getLogoutRequest().toString());
		response.setIssueInstant(new DateTime());
		response.setID(requestId);
		response.setInResponseTo(inResponseTo);

		// Status
		Status s = statusBuilder.buildObject(Status.DEFAULT_ELEMENT_NAME);
		StatusCode code = statusCodeBuilder.buildObject(StatusCode.DEFAULT_ELEMENT_NAME);
		code.setValue(statusCode);
		s.setStatusCode(code);
		response.setStatus(s);

		// Issuer
		Issuer issuer = issuerBuilder.buildObject(Issuer.DEFAULT_ELEMENT_NAME);
		issuer.setValue(spConfig.getEntityId());
		response.setIssuer(issuer);

		// Credential
		BasicX509Credential cred = buildSigningCredential();

		// Signature
		Signature signature = (Signature) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(Signature.DEFAULT_ELEMENT_NAME).buildObject(Signature.DEFAULT_ELEMENT_NAME);

		SignatureSigningParameters signingParameters = new SignatureSigningParameters();
		signingParameters.setSigningCredential(cred);
		signingParameters.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
		signingParameters.setSignatureCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

		try {
			SignatureSupport.prepareSignatureParams(signature, signingParameters);

			X509KeyInfoGeneratorFactory keyInfoFactory = new X509KeyInfoGeneratorFactory();
			keyInfoFactory.setEmitEntityCertificate(true);

			KeyInfoGenerator keyInfoGenerator = keyInfoFactory.newInstance();
			signature.setKeyInfo(keyInfoGenerator.generate(cred));

		} catch (org.opensaml.security.SecurityException e) {
			throw new SAMLException(e);
		}

		response.setSignature(signature);

		// Marshall
		Element elem = marshall(response);

		// Sign AFTER marshalling
		try {
			Signer.signObject(signature);
		} catch (SignatureException e) {
			throw new SAMLException(e);
		}

		return toXmlString(elem);
	}

	private String createAuthnRequest(String requestId) throws SAMLException, NoSuchAlgorithmException,
			InvalidKeySpecException, CertificateException, IOException, SecurityException {

		XMLObjectBuilder<AuthnRequest> builder = getBuilder(AuthnRequest.DEFAULT_ELEMENT_NAME);
		XMLObjectBuilder<Issuer> issuerBuilder = getBuilder(Issuer.DEFAULT_ELEMENT_NAME);

		AuthnRequest request = builder.buildObject(AuthnRequest.DEFAULT_ELEMENT_NAME);
		request.setAssertionConsumerServiceURL(spConfig.getAcs().toString());
		request.setDestination(idpConfig.getLoginUrl().toString());
		request.setIssueInstant(new DateTime());
		request.setID(requestId);

		Issuer issuer = issuerBuilder.buildObject(Issuer.DEFAULT_ELEMENT_NAME);
		issuer.setValue(spConfig.getEntityId());
		request.setIssuer(issuer);

		BasicX509Credential cred = buildSigningCredential();

		Signature signature = (Signature) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(Signature.DEFAULT_ELEMENT_NAME).buildObject(Signature.DEFAULT_ELEMENT_NAME);

		SignatureSigningParameters signingParameters = new SignatureSigningParameters();
		signingParameters.setSigningCredential(cred);
		signingParameters.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
		signingParameters.setSignatureCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

		try {
			SignatureSupport.prepareSignatureParams(signature, signingParameters);

			X509KeyInfoGeneratorFactory keyInfoFactory = new X509KeyInfoGeneratorFactory();
			keyInfoFactory.setEmitEntityCertificate(true);

			KeyInfoGenerator keyInfoGenerator = keyInfoFactory.newInstance();
			signature.setKeyInfo(keyInfoGenerator.generate(cred));

		} catch (org.opensaml.security.SecurityException e) {
			throw new SAMLException(e);
		}

		request.setSignature(signature);

		Element elem = marshall(request);

		try {
			Signer.signObject(signature);
		} catch (SignatureException e) {
			throw new SAMLException(e);
		}

		return toXmlString(elem);
	}

	private byte[] deflate(byte[] input) throws IOException {
		// deflate and base-64 encode it
		Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
		deflater.setInput(input);
		deflater.finish();

		byte[] tmp = new byte[8192];
		int count;

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		while (!deflater.finished()) {
			count = deflater.deflate(tmp);
			bos.write(tmp, 0, count);
		}
		bos.close();
		deflater.end();

		return bos.toByteArray();
	}

	/**
	 * Create a new AuthnRequest suitable for sending to an HTTPRedirect binding
	 * endpoint on the IdP. The SPConfig will be used to fill in the ACS and issuer,
	 * and the IdP will be used to set the destination.
	 *
	 * @return a deflated, base64-encoded AuthnRequest
	 * @throws IOException
	 * @throws CertificateException
	 * @throws InvalidKeySpecException
	 * @throws NoSuchAlgorithmException
	 * @throws SecurityException
	 */
	public String generateAuthnRequest(String requestId) throws SAMLException, NoSuchAlgorithmException,
			InvalidKeySpecException, CertificateException, IOException, SecurityException {
		String request = createAuthnRequest(requestId);
		try {
			// byte[] compressed = deflate(request.getBytes("UTF-8"));
			return DatatypeConverter.printBase64Binary(request.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new SAMLException("Apparently your platform lacks UTF-8.  That's too bad.", e);
		} catch (IOException e) {
			throw new SAMLException("Unable to compress the AuthnRequest", e);
		}
	}

	/**
	 * Create a new LogoutRequest suitable for sending to an HTTPRedirect binding
	 * endpoint on the IdP. The SPConfig will be used to fill in the ACS and issuer,
	 * and the IdP will be used to set the destination.
	 *
	 * @return a deflated, base64-encoded AuthnRequest
	 * @throws IOException
	 * @throws CertificateException
	 * @throws InvalidKeySpecException
	 * @throws NoSuchAlgorithmException
	 * @throws SecurityException
	 */
	public String generateLogoutRequest(String requestId, String userName, String sessionId) throws SAMLException,
			NoSuchAlgorithmException, InvalidKeySpecException, CertificateException, IOException, SecurityException {
		String request = createLogoutRequest(requestId, userName, sessionId);
		try {
			// byte[] compressed = deflate(request.getBytes("UTF-8"));
			return DatatypeConverter.printBase64Binary(request.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new SAMLException("Apparently your platform lacks UTF-8.  That's too bad.", e);
		} catch (IOException e) {
			throw new SAMLException("Unable to compress the AuthnRequest", e);
		}
	}

	/**
	 * Check an logoutResponse and return the subject if validation succeeds. The
	 * NameID from the subject in the first valid assertion is returned along with
	 * the attributes.
	 *
	 * @param authnResponse a base64-encoded AuthnResponse from the SP
	 * @throws SAMLException if validation failed.
	 * @return the authenticated subject/attributes as an AttributeSet
	 */
	public LogoutResponse validateLogoutResponse(String authnResponse) throws SAMLException {
		byte[] decoded = DatatypeConverter.parseBase64Binary(authnResponse);
		try {
			authnResponse = new String(decoded, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new SAMLException("UTF-8 is missing, oh well.", e);
		}

		LogoutResponse response = parseLogoutResponse(authnResponse);

		try {
			validateLogout(response);
		} catch (SAMLException | SignatureException e) {
			throw new SAMLException(e);
		}
		return response;

	}

	/**
	 * Check an logoutRequest and validate *
	 * 
	 * @param authnResponse a base64-encoded AuthnResponse from the SP
	 * @throws SAMLException if validation failed.
	 * @return the authenticated subject/attributes as an AttributeSet
	 */
	public LogoutRequest validateLogoutRequest(String authnRequest) throws SAMLException {
		byte[] decoded = DatatypeConverter.parseBase64Binary(authnRequest);
		try {
			authnRequest = new String(decoded, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new SAMLException("UTF-8 is missing, oh well.", e);
		}

		LogoutRequest request = parseLogoutRequest(authnRequest);

		try {
			validateLogoutRequest(request);
		} catch (SAMLException | SignatureException e) {
			throw new SAMLException(e);
		}
		return request;

	}

	/**
	 * Check an authnResponse and return the subject if validation succeeds. The
	 * NameID from the subject in the first valid assertion is returned along with
	 * the attributes.
	 *
	 * @param authnResponse a base64-encoded AuthnResponse from the SP
	 * @throws SAMLException if validation failed.
	 * @return the authenticated subject/attributes as an AttributeSet
	 */
	public AttributeSet validateResponse(String authnResponse) throws SAMLException {
		byte[] decoded = DatatypeConverter.parseBase64Binary(authnResponse);
		try {
			authnResponse = new String(decoded, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new SAMLException("UTF-8 is missing, oh well.", e);
		}

		Response response = parseResponse(authnResponse);

		try {
			validate(response);
		} catch (SAMLException | SignatureException e) {
			throw new SAMLException(e);
		}

		List<Assertion> assertions = null;
		try {
			assertions = getAssertions(response);
		} catch (DecryptionException e) {
			throw new SAMLException(e);
		}

		// we only look at first assertion
		if (assertions.size() != 1) {
			throw new SAMLException("Response should have a single assertion.");
		}
		Assertion assertion = assertions.get(0);

		Subject subject = assertion.getSubject();
		if (subject == null) {
			throw new SAMLException("No subject contained in the assertion.");
		}
		if (subject.getNameID() == null) {
			throw new SAMLException("No NameID found in the subject.");
		}

		String nameId = subject.getNameID().getValue();

		HashMap<String, List<String>> attributes = new HashMap<String, List<String>>();

		for (AttributeStatement atbs : assertion.getAttributeStatements()) {
			for (Attribute atb : atbs.getAttributes()) {
				String name = atb.getName();
				List<String> values = new ArrayList<String>();
				for (XMLObject obj : atb.getAttributeValues()) {
					values.add(obj.getDOM().getTextContent());
				}
				attributes.put(name, values);
			}
		}
		return new AttributeSet(nameId, attributes, response);
	}

	/**
	 * Create a new LogoutResponse suitable for responding to a request by the IDP
	 *
	 * @return a deflated, base64-encoded AuthnRequest
	 * @throws IOException
	 * @throws CertificateException
	 * @throws InvalidKeySpecException
	 * @throws NoSuchAlgorithmException
	 * @throws SecurityException
	 */
	public String generateLogoutResponse(String requestId, String inResponseTo, String statusCode) throws SAMLException,
			NoSuchAlgorithmException, InvalidKeySpecException, CertificateException, IOException, SecurityException {
		String request = createLogoutResponse(requestId, inResponseTo, statusCode);
		try {
			// byte[] compressed = deflate(request.getBytes("UTF-8"));
			return DatatypeConverter.printBase64Binary(request.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new SAMLException("Apparently your platform lacks UTF-8.  That's too bad.", e);
		} catch (IOException e) {
			throw new SAMLException("Unable to compress the AuthnRequest", e);
		}
	}
}
