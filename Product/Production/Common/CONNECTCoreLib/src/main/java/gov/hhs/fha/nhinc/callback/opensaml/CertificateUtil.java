/*
 * Copyright (c) 2009-2019, United States Government, as represented by the Secretary of Health and Human Services.
 * All rights reserved.
 *  
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above
 *       copyright notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *     * Neither the name of the United States Government nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE UNITED STATES GOVERNMENT BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package gov.hhs.fha.nhinc.callback.opensaml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.activation.DataHandler;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import org.apache.commons.codec.binary.Hex;
import org.apache.wss4j.common.crypto.DERDecoder;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jassmit
 */
public class CertificateUtil {

    private static final Logger LOG = LoggerFactory.getLogger(CertificateUtil.class);
    public static final String PKCS11_TYPE = "PKCS11";
    public static final String KEYSTORE_ERROR_MSG = "Error initializing KeyStore: {}";
    private static final String AUTHORITY_KEY_ID = "2.5.29.35"; // NOSONAR
    private static final String SUBJECT_KEY_ID = "2.5.29.14"; // NOSONAR
    private static final String COMMON_NAME = "CN";

    private CertificateUtil() {
    }

    public static X509Certificate createCertificate(DataHandler data) throws CertificateManagerException {
        X509Certificate cert = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteArrayInputStream bais = null;
        try {
            data.writeTo(baos);
            bais = new ByteArrayInputStream(baos.toByteArray());
            cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(bais);
        } catch (CertificateException | IOException ex) {
            LOG.error("Unable to extract a valid X509 certificate {}", ex.getLocalizedMessage(), ex);
            throw new CertificateManagerException(ex.getLocalizedMessage(), ex);
        } finally {
            closeStream(baos);
            closeStream(bais);
        }
        return cert;
    }

    private static void closeStream(Closeable stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException ex) {
            LOG.warn("Unable to close the stream {}", ex.getLocalizedMessage(), ex);
        }
    }

    public static KeyStore loadKeyStore(final String storeType, final String password, final String storeLoc)
        throws CertificateManagerException {
        InputStream is = null;
        KeyStore secretStore = null;
        try {
            secretStore = KeyStore.getInstance(storeType);
            if (!PKCS11_TYPE.equalsIgnoreCase(storeType)) {
                is = new FileInputStream(storeLoc);
            }
            secretStore.load(is, password.toCharArray());
        } catch (final IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException ex) {
            LOG.error(KEYSTORE_ERROR_MSG, ex.getLocalizedMessage(), ex);
            throw new CertificateManagerException(ex.getMessage(), ex);
        } finally {
            closeStream(is);
        }
        return secretStore;
    }

    public static Map<String, Certificate> getKeystoreMap(final KeyStore keystore) throws KeyStoreException {
        Map<String, Certificate> retObj = new HashMap<>();
        Enumeration<String> aliases = keystore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            Certificate cert = keystore.getCertificate(alias);
            retObj.put(getCertKeyIdSubject(cert), cert);
        }
        return retObj;
    }

    public static List<Certificate> getChain(Certificate cert, final KeyStore keystore) throws KeyStoreException {
        return getChain((X509Certificate) cert, getKeystoreMap(keystore));
    }

    public static List<Certificate> getChain(X509Certificate cert, Map<String, Certificate> keyCache) {
        List<Certificate> chain = new ArrayList<>();
        if (null != cert) {
            chain.add(cert);

            String aki = getCertKeyIdAuthority(cert); // if-root: expected-null
            while (null != aki) {
                Certificate certLoop = keyCache.get(aki);
                if (null != certLoop) {
                    chain.add(certLoop);
                }
                aki = getCertKeyIdAuthority(certLoop);
            }
        }

        return chain;
    }

    public static String getCertKeyIdAuthority(Certificate cert) {
        return getCertKeyIdAuthority((X509Certificate) cert);
    }
    public static String getCertKeyIdAuthority(X509Certificate cert) {
        String aki = null;
        if (null != cert) {
            byte[] akiBytes = cert.getExtensionValue(AUTHORITY_KEY_ID);
            try {
                if (akiBytes != null) {
                    DERDecoder extValA = new DERDecoder(akiBytes);
                    extValA.skip(6);
                    aki = Hex.encodeHexString(extValA.getBytes(20));
                }
            } catch (WSSecurityException e) {
                LOG.error("Unable to decode certificate authority {}", e.getLocalizedMessage(), e);
            }
        }
        return aki;
    }

    public static String getCertKeyIdSubject(Certificate cert) {
        return getCertKeyIdSubject((X509Certificate) cert);
    }
    public static String getCertKeyIdSubject(X509Certificate cert) {
        String ski = null;
        if (null != cert) {
            byte[] subjectKeyID = cert.getExtensionValue(SUBJECT_KEY_ID);
            try {
                if (subjectKeyID != null) {
                    // this logic extracts from CryptoBase class inside wss4j
                    DERDecoder extVal = new DERDecoder(subjectKeyID);
                    extVal.expect(DERDecoder.TYPE_OCTET_STRING); // ExtensionValue OCTET STRING
                    extVal.getLength(); // leave this method alone. getlength modify array position.
                    extVal.expect(DERDecoder.TYPE_OCTET_STRING); // KeyIdentifier OCTET STRING
                    int keyIDLen = extVal.getLength();
                    byte[] hexValue = extVal.getBytes(keyIDLen);
                    ski = Hex.encodeHexString(hexValue);
                }
            } catch (WSSecurityException e) {
                LOG.error("Unable to decode certificate subject: {}", e.getLocalizedMessage(), e);
            }
        }
        return ski;
    }

    public static String getCertSubjectCN(Certificate cert) {
        return getCertSubjectCN((X509Certificate) cert);
    }
    public static String getCertSubjectCN(X509Certificate cert) {
        if (null == cert) {
            return null;
        }

        try {
            LdapName ldapDN = new LdapName(cert.getSubjectX500Principal().getName());
            for (Rdn rdn : ldapDN.getRdns()) {
                if (rdn.getType().equals(COMMON_NAME)) {
                    return (String) rdn.getValue();
                }
            }
        } catch (InvalidNameException ex) {
            LOG.error("{}", ex.getMessage(), ex);
        }
        return null;
    }

    public static boolean isInChain(Certificate cert, List<Certificate> chain) {
        String lookSKI = getCertKeyIdSubject(cert);
        if (null != lookSKI) {
            for (Certificate link : chain) {
                String foundSKI = getCertKeyIdSubject(link);
                if (lookSKI.equals(foundSKI)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static long getDaysOfExpiration(Date dateExpire) {
        return (dateExpire.getTime() - new Date().getTime()) / (24 * 60 * 60 * 1000);
    }

    public static DataHandler getDataHandlerFrom(Certificate cert) throws CertificateEncodingException {
        return new DataHandler(new CertificateSource(cert.getEncoded()));
    }

    public static byte[] getByteCodeFrom(DataHandler handler) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        handler.writeTo(buffer);
        return buffer.toByteArray();
    }

}
