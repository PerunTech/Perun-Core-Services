package com.prtech.saml;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import org.opensaml.xml.security.SecurityException;
import java.security.cert.X509Certificate;

import com.lastpass.saml.IdPConfig;
import com.lastpass.saml.SAMLClient;
import com.lastpass.saml.SAMLException;
import com.lastpass.saml.SAMLInit;
import com.lastpass.saml.SAMLUtils;
import com.lastpass.saml.SPConfig;

public class Client {
	SAMLClient samlClient;

	private static PrivateKey getPK(InputStream reader)
			throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
		// Read in the key into a String
		StringBuilder pkcs8Lines = new StringBuilder();
		BufferedReader rdr = new BufferedReader(new InputStreamReader(reader));
		String line;
		while ((line = rdr.readLine()) != null) {
			pkcs8Lines.append(line);
		}

		// Remove the "BEGIN" and "END" lines, as well as any whitespace

		String pkcs8Pem = pkcs8Lines.toString();
		pkcs8Pem = pkcs8Pem.replace("-----BEGIN PRIVATE KEY-----", "");
		pkcs8Pem = pkcs8Pem.replace("-----END PRIVATE KEY-----", "");
		pkcs8Pem = pkcs8Pem.replaceAll("\\s+", "");

		// Base64 decode the result
		byte[] pkcs8EncodedBytes = Base64.getDecoder().decode(pkcs8Pem);

		// extract the private key

		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8EncodedBytes);
		KeyFactory kf = KeyFactory.getInstance("RSA");
		PrivateKey privKey = kf.generatePrivate(keySpec);

		rdr.close();

		return privKey;
	}

	private static X509Certificate getCert(InputStream is)
			throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, CertificateException {
		CertificateFactory fac = CertificateFactory.getInstance("X509");
		X509Certificate cert = (X509Certificate) fac.generateCertificate(is);
		return cert;
	}

	public String getSAMLRequest() throws NoSuchAlgorithmException, InvalidKeySpecException, CertificateException,
			IOException, SecurityException {
		String samlRequest = "";

		try {
			String requestId = SAMLUtils.generateRequestId();
			samlRequest = samlClient.generateAuthnRequest(requestId);

		} catch (SAMLException | UnsupportedEncodingException e) {
			// response invalid, return to login page...
		}

		return samlRequest;
	}

	public String getLoginURL(String authRequest) throws SAMLException {
		String loginURL;
		try {
			loginURL = samlClient.getIdPConfig().getLoginUrl() + "?SAMLRequest="
					+ URLEncoder.encode(authRequest, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new SAMLException("UTF-8 not supported. Too bad", e);
		}

		return loginURL;
	}

	public Client(InputStream inputStream) throws SAMLException {
		SAMLInit.initialize();

		samlClient = new SAMLClient(new SPConfig(), new IdPConfig(inputStream));
	}

	public void setSPPrivateKey(InputStream inputStream)
			throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
		PrivateKey privateKey = getPK(inputStream);

		SPConfig currentSPConfig = samlClient.getSPConfig();
		currentSPConfig.setPrivateKey(privateKey);
	}

	public void setIdPMetadata(String idpConfigMetadataFileName) throws SAMLException {
		SPConfig spConfig = samlClient.getSPConfig();
		IdPConfig idpConfig = new IdPConfig(new File(idpConfigMetadataFileName));

		samlClient = new SAMLClient(spConfig, idpConfig);
	}

	public void setCertificate(InputStream inputStream)
			throws NoSuchAlgorithmException, InvalidKeySpecException, CertificateException, IOException {
		samlClient.setEntityCertificate(getCert(inputStream));
	}

	public void setSPConfigEntityId(String entityId) {
		samlClient.getSPConfig().setEntityId(entityId);
	}

	public void setSPConfigAuthResponseURL(String url) {
		samlClient.getSPConfig().setAcs(url);
	}

}
