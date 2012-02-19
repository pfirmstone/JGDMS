/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//import com.dstc.security.pki.ConsoleCATool;
//import com.dstc.security.provider.DSTC;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder;
import org.bouncycastle.jce.PKCS10CertificationRequest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.openssl.PasswordFinder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestHolder;

/*
 * HISTORICAL:
 * Run the DSTC Certificate Authority console after installing the provider.
 * Install the provider here, rather than in the java.security file, since it
 * conflicts with the RSAJCA provider that comes with the JDK 1.3.
 */
/**
 * args must be one of two arguments:
 * 
 * -CA Generate Certificate Authority.
 * -CR Process Certification Requests.
 * 
 * @author peter
 */
public class CA {
    
    public static void main(String[] args) {
        // The original implementation only consisted of these two calls.
	//Security.insertProviderAt(new DSTC(), 1);
	//com.dstc.security.pki.ConsoleCATool.main(args);
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
        try {
            if (args[0].equals("-CA")) {
                generateCertificateAuthorityCerts();
                return;
            } else
            if (args[0].equals("-CR")) {
                signCertificationRequests();
                return;
            } else {
                throw new IllegalArgumentException("Argument required either -CA or -CR");
            }
        }catch (Exception ex){
            ex.printStackTrace(System.err);
        }
    }
    
    private static void generateCertificateAuthorityCerts() throws Exception{
        Properties p = readProperties();
        
        // Generate CA key pair
        KeyPairGenerator keyGen = null;
        String algorithm = p.getProperty("jcsi.ca.keyAlg", "DSA");
        int keyLen = Integer.parseInt(p.getProperty("jcsi.ca.keyLength", "512"));
        keyGen = KeyPairGenerator.getInstance(algorithm, "BC");
        SecureRandom random = new SecureRandom();
        keyGen.initialize(keyLen, random);
        KeyPair keys = keyGen.generateKeyPair();
        PublicKey publicKey = keys.getPublic();
        PrivateKey privKey = keys.getPrivate(); // The key used to sign our Certificate.
        
        String issuerDN = p.getProperty("jcsi.ca.issuerDN");
        long validDays 
          = Integer.parseInt(p.getProperty("jcsi.ca.validityPeriod"));
        String signerAlgorithm = p.getProperty("jcsi.ca.sigAlg", "SHA1withDSA");
        
        // Generate root certificate
        ContentSigner sigGen = new JcaContentSignerBuilder(signerAlgorithm).setProvider("BC").build(privKey);
        X500Principal issuer = new X500Principal(issuerDN);
        
        X500Principal subject = issuer; // Self signed.
        long time = System.currentTimeMillis();
        BigInteger serial = BigInteger.valueOf(time);
        Date notBefore = new Date(time - 50000);
        Date notAfter = new Date(time + validDays* 86400000L);
        Certificate rootCert = build(sigGen,issuer,serial, notBefore, notAfter, subject, publicKey);
        
        //Write Private key and Certificate to file.
        writePrivateKey(privKey, p, random);
        writeRootCertificate(rootCert, p);
        
//        // Pasword Protect the private key in preparate to write to file.
//        String password = p.getProperty("jcsi.ca.privKey.password", "changeit");
//        byte[] salt = "salt and pepper shakers &*@".getBytes();
//        int iterationCount = 2048;
//        PBEKeySpec pbeSpec = new PBEKeySpec(password.toCharArray(), salt, iterationCount);
//        Cipher cipher = null;
//        SecretKeyFactory skf = null;
//        byte [] wrappedPrivKey = null;
//        cipher = Cipher.getInstance("PBEWithSHA1AndDES", "BC");
//        skf = SecretKeyFactory.getInstance("PBEWithSHA1AndDES", "BC");
//        cipher.init(Cipher.WRAP_MODE, skf.generateSecret(pbeSpec));
//        wrappedPrivKey = cipher.wrap(privKey);
//        
//        String directory = p.getProperty("jcsi.ca.key.dir", ".");
//        
//        String keyFileName = p.getProperty("jcsi.ca.privKey", "private.key");
//        String certFileName = p.getProperty("jcsi.ca.cert", "user.cert");
//        
//        File keyFile = new File(directory + "/" + keyFileName);
//        keyFile.canWrite();
//        File certFile = new File (directory + "/" + certFileName);
//        certFile.canWrite();
//        writeFile(certFile, rootCert.getEncoded());
//        writeFile(keyFile, wrappedPrivKey);
    }
    
    private static void signCertificationRequests() throws Exception{
        Properties p = readProperties();
        ContentSigner sigGen = getContentSigner(p);
        Certificate rootCert = readRootCertificate(p);
        X500Principal issuer = getIssuer(p);
        long time = System.currentTimeMillis();
        Date notBefore = new Date(time - 50000);
        long validDays 
          = Integer.parseInt(p.getProperty("jcsi.ca.validityPeriod"));
        Date notAfter = new Date(time + validDays * 86400000L);
        /* 
         * Get certificate requests and write chains to file.
         */
        String reqDir = p.getProperty("ca.requests", "requests");
        String pattern = p.getProperty("ca.regex.pattern", "request");
        File requests = new File(reqDir);
        if ( requests.isDirectory()){
            Filter filter = new Filter(pattern);
            File [] certRequests = requests.listFiles(filter);
            int l = certRequests.length;
            for (int i = 0; i < l; i++){
                String fileName = certRequests[i].getName();
                String chainName = fileName.replaceAll("request", "chain");
                Reader input = new InputStreamReader(
                        new BufferedInputStream(
                        new FileInputStream(certRequests[i]))
                        );
                PEMReader pemRead = new PEMReader(input);
                PKCS10CertificationRequest certReq = 
                        (PKCS10CertificationRequest) pemRead.readObject();
                JcaPKCS10CertificationRequestHolder holder = 
                        new JcaPKCS10CertificationRequestHolder(certReq);
                PublicKey publicKey1 = holder.getPublicKey();
                X500Name x500Name = holder.getSubject();
                X500Principal subject1 = new X500Principal(x500Name.toString());
                BigInteger ser = BigInteger.valueOf(System.currentTimeMillis());
                Certificate issuedCert = build(sigGen, issuer, ser,
                        notBefore, notAfter, subject1, publicKey1);
                File f = new File(reqDir + "/" + chainName);
                OutputStreamWriter out = new OutputStreamWriter(
                        new BufferedOutputStream(new FileOutputStream(f)));
                PEMWriter pemWrt = new PEMWriter(out);
                pemWrt.writeObject(issuedCert);
                pemWrt.writeObject(rootCert);
                pemWrt.close();
            }
            
        }
    }
    
    private static Properties readProperties() throws Exception {
        Properties systemProperties = System.getProperties();
        String userHome = systemProperties.getProperty("user.home", "");
        String configFile = systemProperties.getProperty("jcsi.ca.conf", userHome + "{/}.jcsi${/}ca.properties");
        Properties p = new Properties();
        File conf = new File(configFile);
        conf.canRead();
        InputStream in = new FileInputStream(conf);
        p.load(in);
        expand(p, systemProperties);
        return p;
    }
    
    private static void writePrivateKey( PrivateKey k, Properties p, SecureRandom r) throws Exception {
        // Pasword Protect the private key in preparate to write to file.
        String password = p.getProperty("jcsi.ca.privKey.password", "changeit");
        byte[] salt = "salt and pepper shakers &*@".getBytes();
        int iterationCount = 2048;
        PBEParameterSpec pbeParamSpec = new PBEParameterSpec(salt, iterationCount);
       
        PBEKeySpec pbeKeySpec = new PBEKeySpec(password.toCharArray(), salt, iterationCount);
        String pbeAlgorithm = "PBEwithSHA1AndDESede";
        Cipher cipher = Cipher.getInstance(pbeAlgorithm);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(pbeAlgorithm);
        cipher.init(Cipher.WRAP_MODE, skf.generateSecret(pbeKeySpec));
        byte [] wrappedPrivKey = cipher.wrap(k); 
        // Info to enable later retreival.  cipher.getParameters() returns null.
//        AlgorithmParameters algParam = AlgorithmParameters.getInstance(pbeAlgorithm);
//        algParam.init(pbeParamSpec);
        EncryptedPrivateKeyInfo pInfo = new EncryptedPrivateKeyInfo(cipher.getParameters(), wrappedPrivKey);
        String directory = p.getProperty("jcsi.ca.key.dir", ".");
        String keyFileName = p.getProperty("jcsi.ca.privKey", "private.key");
        File keyFile = new File(directory + "/" + keyFileName);
        keyFile.canWrite();
        writeFile(keyFile, pInfo.getEncoded());
        
//        PKCS8Generator generator = new PKCS8Generator(k, "PBEWithSHA1AndDES", "BC");
//        String password = p.getProperty("jcsi.ca.privKey.password", "changeit");
//        String directory = p.getProperty("jcsi.ca.key.dir", ".");
//        String keyFileName = p.getProperty("jcsi.ca.privKey", "private.key");
//        generator.setIterationCount(2048);
//        generator.setPassword(password.toCharArray());
//        generator.setSecureRandom(r);
//        File f = new File(directory +"/"+ keyFileName);
//        Writer out = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(f)));
//        PEMWriter pemWriter = new PEMWriter(out, "BC");
//        pemWriter.writeObject(generator);
//        pemWriter.flush();
//        pemWriter.close();
    }
    
    private static PrivateKey readPrivateKey( Properties p ) throws Exception {
        // Retrieve property strings
        String secretKeyAlgorithm = p.getProperty("jcsi.ca.keyAlg", "DSA");
        String password = p.getProperty("jcsi.ca.privKey.password", "changeit");
        String directory = p.getProperty("jcsi.ca.key.dir", ".");
        String keyFileName = p.getProperty("jcsi.ca.privKey", "private.key");
        // Read ASN.1 Encoded byte[] from file.
        File keyFile = new File(directory + "/" + keyFileName);
        InputStream in = new BufferedInputStream(new FileInputStream(keyFile));
        int len = (int) keyFile.length();
        byte [] bytes = new byte[len];
        in.read(bytes);
        // Reconstruct ASN.1 encoded bytes.
        EncryptedPrivateKeyInfo pInfo = new EncryptedPrivateKeyInfo(bytes);
        // Get the wrapper key algorithm.
        String wrapKeyAlgorithm = pInfo.getAlgName();
        // Factory to generate the wrapper key.
        SecretKeyFactory secretKeyFact = SecretKeyFactory.getInstance(wrapKeyAlgorithm);
        // Get the cipher.
        Cipher cipher = Cipher.getInstance(pInfo.getAlgName());
        // The wrapper key password.
        PBEKeySpec pbeSpec = new PBEKeySpec(password.toCharArray());
        // initialise the cypher with wrapper key in unwrap mode.
        cipher.init(Cipher.DECRYPT_MODE, secretKeyFact.generateSecret(pbeSpec), pInfo.getAlgParameters());
        // Retrieve the private key.
        PKCS8EncodedKeySpec pcks8Spec = pInfo.getKeySpec(cipher);
        KeyFactory keyFact = KeyFactory.getInstance(secretKeyAlgorithm, "BC");
        return keyFact.generatePrivate(pcks8Spec);
        
//        if (rootKey != null ) return rootKey;
//        String password = p.getProperty("jcsi.ca.privKey.password", "changeit");
//        String directory = p.getProperty("jcsi.ca.key.dir", ".");
//        String keyFileName = p.getProperty("jcsi.ca.privKey", "private.key");
//        File f = new File(directory +"/"+ keyFileName);
//        Reader in = new InputStreamReader(new BufferedInputStream(new FileInputStream(f)));
//        PEMReader pemReader = new PEMReader(in, new Pass(password),"BC");
//        rootKey = (PrivateKey) pemReader.readObject();
//        return rootKey;
    }
    
    private static void writeRootCertificate( Certificate c, Properties p) throws Exception{
        String directory = p.getProperty("jcsi.ca.key.dir", ".");
        String certFileName = p.getProperty("jcsi.ca.cert", "user.cert");
        File f = new File(directory +"/"+ certFileName);
        Writer out = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(f)));
        PEMWriter pemWriter = new PEMWriter(out, "BC");
        pemWriter.writeObject(c);
        pemWriter.flush();
        pemWriter.close();
    }
    
    private static Certificate readRootCertificate( Properties p ) throws FileNotFoundException, IOException, Exception{
        String directory = p.getProperty("jcsi.ca.key.dir", ".");
        String certFileName = p.getProperty("jcsi.ca.cert", "user.cert");
        File f = new File(directory +"/"+ certFileName);
        Reader in = new InputStreamReader(new BufferedInputStream(new FileInputStream(f)));
        PEMReader pemReader = new PEMReader(in);
        return (Certificate) pemReader.readObject();
    }
    
    private static X500Principal getIssuer( Properties p ){
        String issuerDN = p.getProperty("jcsi.ca.issuerDN");
        return new X500Principal(issuerDN);
    }
    
    private static ContentSigner getContentSigner(Properties p) throws Exception{
        String signerAlgorithm = p.getProperty("jcsi.ca.sigAlg", "SHA1withDSA");
        return new JcaContentSignerBuilder(signerAlgorithm).setProvider("BC").build(readPrivateKey(p));
    }
    
    private static void writeFile(File f, byte[] bytes) throws Exception{
        OutputStream out = new BufferedOutputStream(new FileOutputStream(f));
        out.write(bytes);
        out.flush();
        out.close();
    }
    
    private static Certificate build(
            ContentSigner sigGen,
            X500Principal issuer, 
            BigInteger serial, 
            Date notBefore, 
            Date notAfter,
            X500Principal subject,
            PublicKey publicKey
            ) throws Exception
    {
        X509v1CertificateBuilder certBuilder = 
            new JcaX509v1CertificateBuilder(
                issuer, 
                serial, 
                notBefore, 
                notAfter, 
                subject, 
                publicKey);
        
        X509CertificateHolder certHolder = certBuilder.build(sigGen);
        JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
        Certificate cert = null;
        cert = converter.getCertificate(certHolder);
        return cert;
    }
    
    public static void expand(Properties p, Properties system) throws Exception{
        Set<Entry<Object, Object>> entrySet = p.entrySet();
        Iterator<Entry<Object, Object>> i = entrySet.iterator();
        while (i.hasNext()){
            Entry<Object, Object> entry = i.next();
            Object value = entry.getValue();
            value = expand(value.toString(), system);
            entry.setValue(value);
        }
    }
    /**
     * Substitutes all entries like ${some.key}, found in specified string, 
     * for specified values.
     * If some key is unknown, throws ExpansionFailedException. 
     * @param str the string to be expanded
     * @param properties available key-value mappings 
     * @return expanded string
     * @throws Exception
     */
    public static String expand(String str, Properties properties)
            throws Exception {
        final String START_MARK = "${"; //$NON-NLS-1$
        final String END_MARK = "}"; //$NON-NLS-1$
        final int START_OFFSET = START_MARK.length();
        final int END_OFFSET = END_MARK.length();

        StringBuilder result = new StringBuilder(str);
        int start = result.indexOf(START_MARK);
        while (start >= 0) {
            int end = result.indexOf(END_MARK, start);
            if (end >= 0) {
                String key = result.substring(start + START_OFFSET, end);
                String value = properties.getProperty(key);
                if (value != null) {
                    result.replace(start, end + END_OFFSET, value);
                    start += value.length();
                } else {
                    System.err.println(str + " key not found: " + key);
                    throw new Exception("Failed to expand properties"); //$NON-NLS-1$
                }
            }
            start = result.indexOf(START_MARK, start);
        }
        return result.toString();
    }
    
    private static class Filter implements FilenameFilter {
        private final Pattern regex;
        private Filter(String regex){
            this.regex = Pattern.compile(regex);
        }

        @Override
        public boolean accept(File dir, String name) {
            if (regex.matcher(name).matches()){
                return true;
            }
            return false;
        }
        
    }
    
    private static class Pass implements PasswordFinder {
        private final String password;
        
        private Pass(String password){
            this.password = password;
        }

        @Override
        public char[] getPassword() {
            return password.toCharArray();
        }
        
    }
    
}
