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

package org.apache.river.tool;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilePermission;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.SocketPermission;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLPermission;
import java.security.CodeSource;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.PrivateCredentialPermission;
import net.jini.security.policy.PolicyInitializationException;
import org.apache.river.api.io.Replace;
import org.apache.river.api.net.Uri;
import org.apache.river.api.security.CombinerSecurityManager;
import org.apache.river.api.security.ConcurrentPolicyFile;
import org.apache.river.api.security.PermissionComparator;

/**
 * This SecurityManager can be used in a simulation environment to generate
 * policy files that conform to the principle of least privilege.
 * <p>
 * It should be installed from the command line using the following system property:<br>
 * <code>-Djava.security.manager=org.apache.river.tool.SecurityPolicyWriter</code>
 * <p>
 * The policy file generated will be specific to the runtime where it is generated.
 * Users should edit the files after generation if they wish to deploy the policy
 * else where, replacing file path characters etc.
 * <p>
 * Note that the file generated will not contain the <code>ProtectionDomain</code>
 * of the jar file containing this SecurityManager, neither will it contain
 * Java platform jars.
 * <p>
 * 
 * The following properties should be set to obtain CodeSource signer 
 * Certificate Aliases.<br>
 * <table><caption>System Properties</caption>
 * <tr>
 * <td>System Property</td>
 * <td>Explanation</td>
 * </tr><tr>
 * <td>java.security.policy</td>
 * <td>The policy file location.</td>
 * </tr><tr>
 * <td>javax.net.ssl.trustStore</td>
 * <td>The location of the KeyStore,  if no location is specified, then the 
 * cacerts file in the lib/security subdirectory of the JDK installation 
 * directory is used. If specified, the location is treated as a URL.
 * If no protocol is specified in the URL or it is an unknown protocol, 
 * then, the location is treated as a file name.</td>
 * </tr><tr>
 * <td>javax.net.ssl.trustStoreType</td>
 * <td>The KeyStore type, If no keystore type is specified, then the type 
 * returned by KeyStore.getDefaultType() is used.</td>
 * </tr><tr>
 * <td>javax.net.ssl.trustStorePassword</td>
 * <td>The KeyStore password, if no password is specified, then no password
 * is used when loading the keystore.</td>
 * </tr>
 * </table>
 * <p>
 * A property file may be specified to replace paths with properties to
 * allow these to be customized for later policy file expansion in deployment for
 * CodeSource url and FilePermission path's, by setting the following property:
 * <code>-DSecurityPolicyWriter.path.properties=</code> Properties defined in this
 * property file shouldn't reference other properties declared in this file.
 * <p>
 * If a policy file already exists, only additional permission grants
 * will be added during subsequent test runs.
 * <p>
 * Generated policy files should be edited after running each integration test
 * when widening
 * permission scope, such as SocketPermission which will typically specify a local
 * IP address. Subsequent integration tests append missing permissions, so it
 * is important to widen scope prior to adding more permission grants.
 * <p>
 * Note that the logger <code>net.jini.security.policy</code> should be set to
 * <code>Level.CONFIG</code> to
 * log any syntax errors in edited policy files.  If a policy file contains
 * a syntax error, all permissions granted in the new policy file will be ignored, 
 * this results in duplication of permissions in the new policy file as they will be 
 * re-granted until the syntax error is addressed.  For this reason the log 
 * output should be checked following each manual edit.
 * 
 * @see KeyStore
 * @author Peter Firmstone
 * @since 3.0.0
 */
public class SecurityPolicyWriter extends CombinerSecurityManager{
    
    private static Logger logger;
    private static final Object loggerLock = new Object();
    private final Policy POLICY;
    private final File policyFile;

    /**
     * @return the logger
     */
    private static Logger getLogger() {
        synchronized (loggerLock){
            if (logger != null) return logger;
            logger = 
            Logger.getLogger("net.jini.security.policy");
            return logger;
        }
    }
    // Use concurrent collections to avoid deadlock from recursive calls.
    private final ConcurrentMap<ProtectionDomain, Collection<Permission>> domainPermissions;
    private static KeyStore keyStore = null;
    private static final Object keyStoreLock = new Object();
    private final ConcurrentMap<Certificate,String> aliases;
    private final Cert certFunc;
    private final Map<String,String> pathReplacements;
    private final String hostname;
    
    private SecurityPolicyWriter(
	    ConcurrentMap<ProtectionDomain,Collection<Permission>> map) 
    {
        super();
        
        domainPermissions = map;
        aliases = new ConcurrentHashMap<Certificate,String>();
        certFunc = new Cert();
        String securityPolicyWriterPropLocation = System.getProperty("SecurityPolicyWriter.path.properties");
        hostname = System.getProperty("HOST");
        Properties p = new Properties();
        if (securityPolicyWriterPropLocation != null){
            try {
                FileReader fr = new FileReader(securityPolicyWriterPropLocation);
                p.load(fr);
                Iterator<Entry<Object,Object>> propsit = p.entrySet().iterator();
                while (propsit.hasNext()){
                    Entry<Object,Object> entry = propsit.next();
                    System.setProperty((String) entry.getKey(), (String) entry.getValue());
                }
            } catch (FileNotFoundException ex) {
                getLogger().log(Level.INFO, "Unable to read properties file", ex);
            } catch (IOException ex) {
                getLogger().log(Level.INFO, "Unable to read properties file", ex);
            }
        }
        p.put("java.io.tmpdir", System.getProperty("java.io.tmpdir"));
        p.put("java.home", System.getProperty("java.home"));
        p.put("HOST", hostname);
        p.put("jsk.home", System.getProperty("jsk.home"));
        p.put("qa.home", System.getProperty("qa.home"));
        Map<String,String> paths = new HashMap<String,String>(p.size());
        Set<Entry<Object,Object>> propSet = p.entrySet();
        Iterator<Entry<Object,Object>> propIt = propSet.iterator();
        while (propIt.hasNext()){
            Entry<Object,Object> props = propIt.next();
            Object key = props.getKey();
            Object value = props.getValue();
            if (key instanceof String && value instanceof String){
                paths.put(((String) value).replace('\\', '/'), (String) key);
            }
        }
        pathReplacements = paths;
        String policy = System.getProperty("java.security.policy");
	Uri polLocation = null;
        try {
            polLocation = new Uri(policy );
        } catch (URISyntaxException ex) {
            throw new RuntimeException("Unable to create URI", ex);
        }
        URI poliLoc = Uri.uriToURI(polLocation);
        policyFile = new File(poliLoc);
        if (!policyFile.exists()){
	    try {
		policyFile.createNewFile();
	    } catch (IOException ex) {
		throw new RuntimeException("Unable to create a policy file: "+ policy, ex);
            }
        }
        Policy polcy = null;
        try {
            polcy = new ConcurrentPolicyFile(new URL[]{policyFile.toURI().toURL()});
        } catch (PolicyInitializationException ex) {
            getLogger().log(Level.CONFIG,"Unable to create Policy instance", ex);
        } catch (MalformedURLException ex) {
            getLogger().log(Level.CONFIG, "Unable to create URL", ex);
        }
        POLICY = polcy;
    } 
    
    public SecurityPolicyWriter() {
        this(new ConcurrentHashMap<ProtectionDomain,Collection<Permission>>());
        Runtime.getRuntime().addShutdownHook(shutdownHook());
    }
    
    private File policyFile() throws URISyntaxException{
        return policyFile;
    }
    
    private static KeyStore keyStore(){
	synchronized (keyStoreLock){
	    if (keyStore == null){
		try {
		    keyStore = initStore();
		} catch (IOException ex) {
		    getLogger().log(Level.FINE, "Unable to create KeyStore instance", ex);
		} catch (GeneralSecurityException ex) {
		    getLogger().log(Level.FINE, "Unable to create KeyStore instance", ex);
		}
	    }
	    return keyStore;
	}
    }
    
    /**
     * Initializes trust store and cert stores based on system property values.
     */
    private static KeyStore initStore() throws IOException, GeneralSecurityException {
	String path, type, passwd;
	if ((path = System.getProperty("javax.net.ssl.trustStore")) != null) {
	    type = System.getProperty(
		"javax.net.ssl.trustStoreType", KeyStore.getDefaultType());
	    passwd = System.getProperty("javax.net.ssl.trustStorePassword");
	} else {
	    path = System.getProperty("java.home") + "/lib/security/cacerts";
	    type = KeyStore.getDefaultType();
	    passwd = null;
	}
	KeyStore kstore = KeyStore.getInstance(type);
	InputStream in;
	URL url = null;
	try {
	    url = new Uri(path).toURL();
	} catch (MalformedURLException e) {
	    getLogger().log(Level.SEVERE, null, e);
	} catch (URISyntaxException ex) {
	    getLogger().log(Level.SEVERE, null, ex);
	}
	if (url != null) {
	    in = url.openStream();
	} else {
	    in = new FileInputStream(path);
	}
	try {
	    kstore.load(in, (passwd != null) ? passwd.toCharArray() : null);
	} finally {
	    in.close();
	}
//	if (getLogger().isLoggable(Level.FINEST)) {
//	    getLogger().log(Level.FINEST, "loaded trust store from {0} ({1})",
//		       new Object[]{ path, type });
//	}
        return kstore;
    }
    
    @Override
    protected boolean checkPermission(ProtectionDomain pd, Permission p){
        pd = new ProtectionDomainKey(pd);
	Collection<Permission> perms = domainPermissions.get(pd);
	    if (perms == null) {
		perms = new ConcurrentSkipListSet<Permission>(new PermissionComparator());
                // The following code would include the static permissions from
                // the ProtectionDomain.  This doesn't seem like a good idea,
                // as these permissions may be granted by a ClassLoader at
                // creation time, so commented out.
//                PermissionCollection pc = pd.getPermissions();
//                if (pc != null) {
//                    Enumeration<Permission> e = pc.elements();
//                    while (e.hasMoreElements()){
//                        perms.add(e.nextElement());
//                    }
//                }
		Collection<Permission> existed = domainPermissions.putIfAbsent(pd, perms);
		if (existed != null) perms = existed;
	    }
	perms.add(p);
        return true;
    }
    
    private Thread shutdownHook(){
        Thread t = new Thread ( new Runnable(){
            @Override
            public void run (){
                PrintWriter pw = null;
                try {
		    File policyFile = policyFile();
		    pw = new PrintWriter(new BufferedWriter(new FileWriter(policyFile, true)));
                } catch (IOException ex) {
                    getLogger().log(Level.SEVERE, "unable to write to policy file ", ex);
                    return;
                } catch (URISyntaxException ex) {
		    getLogger().log(Level.SEVERE, "unable to write to policy file ", ex);
		    return;
                } 
//		catch (PolicyInitializationException ex) {
//		    getLogger().log(Level.SEVERE, "unable to parse to policy file ", ex);
//		    return;
//		}
                //REMIND: keystore "some_keystore_url", "keystore_type", "keystore_provider";
                //        keystorePasswordURL "some_password_url";
                
//                grant signedBy "signer_names", codeBase "URL",
//                    principal principal_class_name "principal_name",
//                    principal principal_class_name "principal_name",
//                    ... {
//
//                      permission permission_class_name "target_name", "action", 
//                          signedBy "signer_names";
//                      permission permission_class_name "target_name", "action", 
//                          signedBy "signer_names";
//                      ...
//                  };

                
                Iterator<Entry<ProtectionDomain,Collection<Permission>>> it 
                        = domainPermissions.entrySet().iterator();
                while (it.hasNext()){
                    Entry<ProtectionDomain,Collection<Permission>> entry = it.next();
                    ProtectionDomain pd = entry.getKey();
		    Collection<Permission> perms = entry.getValue();
                    Collection<Permission> permsToPrint = new ArrayList<Permission>();
                    PermissionCollection pc = new Permissions();
                    Iterator<Permission> pIt = perms.iterator();
                    while (pIt.hasNext()){
                        Permission p = pIt.next();
                        if (POLICY != null && POLICY.implies(pd, p) || pc.implies(p)) continue;
                        pc.add(p);
                        permsToPrint.add(p);
                    }
		    if (permsToPrint.isEmpty()) continue;
                    CodeSource cs = null; 
                    Principal [] principals = null;
		    try {
			cs = pd.getCodeSource();
			principals = pd.getPrincipals();
		    } catch (NullPointerException e){
			// On some occassions ProtectionDomain hasn't been
			// safely published.
			System.err.println(
			    "ProtectionDomain wasn't safely published: " 
			    + pd.toString()
			);
		    }
		    // Delegate tasks to an executor so we don't cause recursive calls / stack overflow.		    
		    if (cs != null){
			Certificate [] signers = cs.getCertificates();
			if (signers != null && signers.length > 0){
			     if (keyStore() != null){
				for (int i=0, l=signers.length; i<l; i++){
				    aliases.computeIfAbsent(signers[i], certFunc);
				}
			    }
			}
		    }
                    if (cs != null || (principals != null && principals.length > 0)){
                        URL codebase = cs != null ? cs.getLocation() : null;
                        pw.print("grant ");
                        if (keyStore != null){
                            Certificate [] signers = cs != null ? cs.getCertificates() : null;
                            if (signers != null && signers.length > 0) {
                                List<String> alia = new ArrayList<String>(signers.length);
                                for (int i=0, l=signers.length; i<l; i++){
                                    alia.add(aliases.get(signers[i]));
                                }
                                if (!alia.isEmpty()){
                                    pw.print("signedBy \"");
                                    for (int i=0, l=alia.size(); i<l; i++){
                                       if (i != 0) pw.print(",");
                                       pw.print(alia.get(i));
                                    }
                                    pw.print("\", ");
                                }
                            }
                        }
			if (codebase != null){
			    pw.print("codebase \"");
			    String codebaseStr = replaceValuesWithProperties(codebase.toString());
			    pw.print(codebaseStr);
			    pw.print("\"");
			    if (principals != null && principals.length >0) pw.print(",\n");
			}
                        if (principals != null && principals.length > 0){
                            for (int i=0, l=principals.length; i<l; i++){
                                if (i!=0 || codebase != null) pw.print("    ");
                                pw.print("principal ");
                                pw.print(principals[i].getClass().getCanonicalName());
                                pw.print(" \"");
                                pw.print(principals[i].getName());
                                if (i<l-1) pw.print("\",\n");
                                else pw.print("\"\n");
                            }
                        } else {
                            pw.print("\n");
                        }
                        pw.print("{\n");

                        Iterator<Permission> permIt = permsToPrint.iterator();
                        while (permIt.hasNext()){
                            Permission p = permIt.next();
                            pw.print("    permission ");
                            pw.print(p.getClass().getCanonicalName());
                            pw.print(" \"");
			    if (p instanceof PrivateCredentialPermission){
				PrivateCredentialPermission pcp = (PrivateCredentialPermission) p;
				String credential = pcp.getCredentialClass();
				String [][] princpals = pcp.getPrincipals();
				StringBuilder sb = new StringBuilder();
				sb.append(credential);
				sb.append(" ");
				for (int i=0,l=princpals.length; i<l; i++){
				    String [] pals = princpals [i];
				    for (int j=0,m=pals.length; j<m; j++){
					sb.append(pals[j]);
					if (j < m-1) sb.append(" \\\"");
					else sb.append("\\\"");
				    }
				    if (i < l-1) sb.append(" ");
				}
				pw.print(sb.toString());
			    } else {
				/* Some complex permissions have quoted strings embedded or
				literal carriage returns that must be escaped.  */
				String name = p.getName();
				if (p instanceof FilePermission){
                                    name = replaceValuesWithProperties(name);
                                    name = name.replace("/", "${/}");
				} else if (p instanceof SocketPermission || p instanceof URLPermission){
                                    name = name.replace(hostname, "${HOST}");
                                } else {
				    name = name.replace("\\\"", "\\\\\"").replace("\"","\\\"").replace("\r","\\\r");
				}
				pw.print(name);
			    }
                            String actions = p.getActions();
                            if (actions != null && !"".equals(actions)){
                                pw.print("\", \"");
                                pw.print(actions);
                                pw.print("\"");
                            } else {
                                pw.print("\"");
                            }
                            // REMIND signedBy?
                            pw.print(";\n");
                        }
                        pw.print("};\n\n");
                    }
                }
                pw.flush();
                pw.close();
            }
        },"SecurityPolicyWriter policy creation thread");
        if (t.isDaemon()) t.setDaemon(false);
        /**
         * See jtreg sun bug ID:4404702
         * This ensures that this thread doesn't unnecessarily hold 
         * a strong reference to a ClassLoader, thus preventing
         * it from being garbage collected.
         */ 
        t.setContextClassLoader(ClassLoader.getSystemClassLoader());
        return t;
    }
    
    private String replaceValuesWithProperties(String s){
        s = s.replace('\\', '/');
        Set<Entry<String,String>> pathSet = pathReplacements.entrySet();
        Iterator<Entry<String,String>> pathIt = pathSet.iterator();
        while (pathIt.hasNext()){
            Entry<String,String> path = pathIt.next();
            StringBuilder sb = new StringBuilder();
            String value = path.getValue();
            sb.append("${").append(value).append("}");
            String key = path.getKey();
            s = s.replace(key, sb.toString());
            if ("java.io.tmpdir".equals(value) && key.endsWith("/") ){
                getLogger().log(Level.FINE, "temp directory: {0}", s);
                s = s.replace(key.substring(0, key.length()-1), sb.toString());
            }
        }
        return s;
    }
    
    private class Cert implements Function<Certificate,String> {

        @Override
        public String apply(Certificate t) {
            try {
                return keyStore().getCertificateAlias(t);
            } catch (KeyStoreException ex) {
                getLogger().log(Level.WARNING, "Alias not found in keystore", ex);
            }
            return null;
        }
        
    }
    
    /**
     * ProtectionDomainKey identity ignores the ClassLoader.
     */
    private static class ProtectionDomainKey extends ProtectionDomain{
        
        private static UriCodeSource getCodeSource(ProtectionDomain pd){
            CodeSource cs = pd.getCodeSource();
            if (cs != null) return new UriCodeSource(cs);
            return null;
        }

        private final CodeSource codeSource;
        private final Principal[] princiPals;
        private final int hashCode;

        ProtectionDomainKey(ProtectionDomain pd){
            this(getCodeSource(pd), pd.getPermissions(), pd.getPrincipals());
        }
        
        ProtectionDomainKey(UriCodeSource urics, PermissionCollection perms, Principal [] p){
            super(urics, perms, null, p);
            this.codeSource = urics;
            this.princiPals = p;
            int hash = 7;
            hash = 29 * hash + Objects.hashCode(this.codeSource);
            hash = 29 * hash + Arrays.deepHashCode(this.princiPals);
            this.hashCode = hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            final ProtectionDomainKey other = (ProtectionDomainKey) obj;
            if (!Objects.equals(this.codeSource, other.codeSource)) return false;
            return Arrays.deepEquals(this.princiPals, other.princiPals);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
        
    }
    
    /**
     * To avoid CodeSource equals and hashCode methods.
     * 
     * Shamelessly stolen from RFC3986URLClassLoader
     * 
     * CodeSource uses DNS lookup calls to check location IP addresses are 
     * equal.
     * 
     * This class must not be serialized.
     */
    private static class UriCodeSource extends CodeSource implements Replace {
        private static final long serialVersionUID = 1L;
        private final Uri uri;
        private final int hashCode;
        
        UriCodeSource(CodeSource cs){
            this(cs.getLocation(), cs.getCertificates());
        }
        
        UriCodeSource(URL url, Certificate [] certs){
            super(url, certs);
            Uri uRi = null;
            if (url != null){
                try {
                    uRi = Uri.urlToUri(url);
                } catch (URISyntaxException ex) { }//Ignore
            }
            this.uri = uRi;
            int hash = 7;
            hash = 23 * hash + (this.uri != null ? this.uri.hashCode() : 0);
            hash = 23 * hash + (certs != null ? Arrays.hashCode(certs) : 0);
            hashCode = hash;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
        
        @Override
        public boolean equals(Object o){
            if (!(o instanceof UriCodeSource)) return false;
            if (uri == null) return super.equals(o);
            UriCodeSource that = (UriCodeSource) o;
            if ( !uri.equals(that.uri)) return false;
            Certificate [] mine = getCertificates();
            Certificate [] theirs = that.getCertificates();
            if ( mine == null && theirs == null) return true;
            if ( mine == null && theirs != null) return false;
            if ( mine != null && theirs == null) return false;
            return (Arrays.asList(getCertificates()).equals(
                    Arrays.asList(that.getCertificates())));
        }
        
        @Override
        public Object writeReplace() throws ObjectStreamException {
            return new CodeSource(getLocation(), getCertificates());
        }
       
    }
}
