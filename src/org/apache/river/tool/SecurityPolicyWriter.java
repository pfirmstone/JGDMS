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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.CodeSource;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Permission;
import java.security.Permissions;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.PrivateCredentialPermission;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import org.apache.river.api.security.CombinerSecurityManager;
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
 * The following properties should be set to obtain CodeSource signer Certificate Aliases.<br>
 * <table>
 * <tr>
 * <td>System Property</td>
 * <td>Explanation</td>
 * </tr><tr>
 * <td>org.apache.river.tool.SecurityPolicyWriter.Directory</td>
 * <td>Directory to create policy files in.</td>
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
 * 
 * @see KeyStore
 * @author Peter Firmstone
 * @since 3.0.0
 */
public class SecurityPolicyWriter extends CombinerSecurityManager{
    
    private static Logger logger;
    private static final Object loggerLock = new Object();

    /**
     * @return the logger
     */
    private static Logger getLogger() {
        synchronized (loggerLock){
            if (logger != null) return logger;
            logger = 
            Logger.getLogger("org.apache.river.tool.SecurityPolicyProvider");
            return logger;
        }
    }
    // Use concurrent collections to avoid deadlock from recursive calls.
    private final ConcurrentMap<ProtectionDomain, Collection<Permission>> policy;
    private final File policyFile;
    private final KeyStore keyStore;
    private final ExecutorService exec;
    private final ConcurrentMap<Certificate,String> aliases;
    private final Cert certFunc;
    
    private SecurityPolicyWriter(ConcurrentMap<ProtectionDomain,Collection<Permission>> map, File policyFile, KeyStore keyStore){
        super();
        policy = map;
        this.policyFile = policyFile;
        this.keyStore = keyStore;
        exec = Executors.newSingleThreadExecutor();
        aliases = new ConcurrentHashMap<Certificate,String>();
        certFunc = new Cert();
    } 
    
    public SecurityPolicyWriter(){
        this(new ConcurrentHashMap<ProtectionDomain,Collection<Permission>>(), policyFile(), keyStore());
        Runtime.getRuntime().addShutdownHook(shutdownHook());
    }
    
    private static File policyFile(){
        String policyDirectory = System.getProperty("org.apache.river.tool.SecurityPolicyWriter.Directory");
        Uuid timestamp = UuidFactory.generate();
        File policyFile = new File(policyDirectory + File.separator + timestamp + ".policy");
        try {
            policyFile.createNewFile();
        } catch (IOException ex) {
            throw new RuntimeException("Unable to create a policy file, in policy directory: "+ policyDirectory, ex);
        }
        return policyFile;
    }
    
    private static KeyStore keyStore(){
        KeyStore keyStore = null;
        try {
            keyStore = initStore();
        } catch (IOException ex) {
            getLogger().log(Level.FINE, "Unable to create KeyStore instance", ex);
        } catch (GeneralSecurityException ex) {
            getLogger().log(Level.FINE, "Unable to create KeyStore instance", ex);
        }
        return keyStore;
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
	    url = new URL(path);
	} catch (MalformedURLException e) {
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
    
    protected boolean checkPermission(ProtectionDomain pd, Permission p){
        Collection<Permission> perms = policy.get(pd);
        if (perms == null) {
            perms = new ConcurrentSkipListSet<Permission>(new PermissionComparator());
            Collection<Permission> existed = policy.putIfAbsent(pd, perms);
            if (existed != null) perms = existed;
        }
	CodeSource cs = null;
	try {
	    cs = pd.getCodeSource();
	    pd.getPrincipals();
	} catch (NullPointerException e){
	    // On some occassions ProtectionDomain hasn't been
	    // safely published.
	    System.err.println(
		"ProtectionDomain wasn't safely published." 
	    );
	    e.printStackTrace(System.err);
	}
        if (cs != null){
            Certificate [] signers = cs.getCertificates();
            if (signers != null && signers.length > 0){
                exec.submit(new Certs(signers));
            }
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
                    pw = new PrintWriter(new BufferedWriter(new FileWriter(policyFile)));
                } catch (IOException ex) {
                    getLogger().log(Level.SEVERE, "unable to write to policy file ", ex);
                    return;
                }
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
                        = policy.entrySet().iterator();
                while (it.hasNext()){
                    Entry<ProtectionDomain,Collection<Permission>> entry = it.next();
                    ProtectionDomain pd = entry.getKey();
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
                    if (cs != null || (principals != null && principals.length > 0)){
                        URL codebase = cs.getLocation();
                        pw.print("grant ");
                        if (keyStore != null){
                            Certificate [] signers = cs.getCertificates();
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
                        pw.print("codebase \"");
                        pw.print(codebase);
                        pw.print("\"");
                    
                        if (principals != null && principals.length > 0){
                            pw.print(",\n");
                            for (int i=0, l=principals.length; i<l; i++){
                                pw.print("    principal ");
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

                        Iterator<Permission> permIt = entry.getValue().iterator();
                        Permissions permissions = new Permissions();
                        while (permIt.hasNext()){
                            Permission p = permIt.next();
                            if (permissions.implies(p)) continue;
                            permissions.add(p);
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
				String name = p.getName().replace("\"","\\\"").replace("\r","\\\r");
    //                            if (p instanceof FilePermission){
    //                                    name = name.replace(File.separator, "${/}");
    //                            }
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
    
    private class Certs implements Runnable {
        
        private final Certificate [] signers;
        
        Certs(Certificate [] signers){
            this.signers = signers;
        }

        @Override
        public void run() {
            if (keyStore != null){
                for (int i=0, l=signers.length; i<l; i++){
                    aliases.computeIfAbsent(signers[i], certFunc);
                }
            }
        }
        
    }
    
    private class Cert implements Function<Certificate,String> {

        @Override
        public String apply(Certificate t) {
            try {
                return keyStore.getCertificateAlias(t);
            } catch (KeyStoreException ex) {
                getLogger().log(Level.WARNING, "Alias not found in keystore", ex);
            }
            return null;
        }
        
    }
}
