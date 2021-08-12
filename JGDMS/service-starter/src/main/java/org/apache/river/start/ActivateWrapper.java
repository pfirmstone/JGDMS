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

package org.apache.river.start;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.activation.arg.ActivationDesc;
import net.jini.activation.arg.ActivationException;
import net.jini.activation.arg.ActivationGroupID;
import net.jini.activation.arg.ActivationID;
import net.jini.activation.arg.ActivationSystem;
import java.security.AccessController;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.activation.ActivationDescImpl;
import net.jini.export.ProxyAccessor;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.loader.LoadClass;
import net.jini.loader.pref.PreferredClassLoader;
import net.jini.security.policy.DynamicPolicy;
import net.jini.security.policy.DynamicPolicyProvider;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.Valid;
import org.apache.river.api.net.Uri;
import org.apache.river.api.util.Startable;

/**
 * A wrapper for activatable objects, providing separation of the import
 * codebase (where the server classes are loaded from by the activation
 * group) from the export codebase (where clients should load classes from
 * for stubs, etc.). This functionality allows multiple 
 * activatable objects to be placed in the same activation group, with each 
 * object maintaining a distinct codebase.
 * <p>
 * This wrapper class is assumed to be available directly in the activation
 * group VM; that is, it is assumed to be in the application classloader,
 * the extension classloader, or the boot classloader, rather than being
 * downloaded. Since this class also needs considerable permissions, the
 * easiest thing to do is to make it an installed extension.
 * <p>
 * An example of how to use this wrapper:
 * <pre>
 * URL[] importURLs = new URL[] {new URL("http://myhost:8080/service.jar")};
 * URL[] exportURLs = new URL[] {new URL("http://myhost:8080/service-dl.jar")};
 * ActivationID aid 
 *     = ActivateWrapper.register(
 *		gid,
 *		new ActivateWrapper.ActivateDesc(
 *			"foo.bar.ServiceImpl",
 *			importURLs,
 *			exportURLs,
 *			"http://myhost:8080/service.policy",
 *			new MarshalledInstance(
 *                          new String[] { "/tmp/service.config" })
 *              ),
 *		true,
 *              activationSystem);
 * </pre>
 * <A NAME="serviceConstructor"></A>
 * Clients of this wrapper service need to implement the following "activation
 * constructor":
 * <blockquote><pre>
 * &lt;impl&gt;(ActivationID activationID, MarshalledInstance data)
 * </pre></blockquote>
 * where,
 * <UL>
 * <LI>activationID - is the object's activation identifier
 * <LI>data         - is the object's activation data
 * </UL>
 *
 * Clients of this wrapper service can also implement 
 * {@link net.jini.export.ProxyAccessor}, which allows the service 
 * implementation to provide a remote reference of its choosing.
 * <P>
 * <A NAME="configEntries"></A>
 * This implementation of <code>ActivateWrapper</code>
 * supports the
 * following {@link java.security.Security} property:
 *
 *   <table summary="Describes the org.apache.river.start.servicePolicyProvider
 *          security property"
 *          border="0" cellpadding="2">
 *     <tr valign="top">
 *       <th scope="col"> <font size="+1">&#X2022;</font>
 *       <th scope="col" align="left" colspan="2"> <font size="+1"><code>
 *       org.apache.river.start.servicePolicyProvider</code></font>
 *     <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *       Default: <td> <code>
 *         "net.jini.security.policy.DynamicPolicyProvider"
 *         </code>
 *     <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *       Description: <td> The fully qualified class name of a
 *       dynamic policy provider (see {@link net.jini.security.policy.DynamicPolicy})
 *       which will be used to "wrap" all service policy files. 
 *       The implementation class needs to:
 *       <UL>
 *       <LI> implement the following constructor:
 *           <blockquote><pre>
 *   public &lt;impl&gt;(Policy servicePolicy)
 *           </pre></blockquote>
 *           where,
 *           <LI>servicePolicy - is the service policy object to be wrapped
 *           
 *       <LI> implement {@link net.jini.security.policy.DynamicPolicy}
 *       <LI> be a public, non-interface, non-abstract class
 *       </UL>
 *       
 *       <P>
 *       A custom service policy provider can be very useful when trying to
 *       debug security related issues.
 *       <code>org.apache.river.tool.DebugDynamicPolicyProvider</code> is an example
 *       policy provider that provides this functionality and can be located
 *       via the following URL:
 *       <A HREF="http://starterkit-examples.jini.org/">
 *           http://starterkit-examples.jini.org/
 *       </A><BR>
 *       <I>Note:</I>The custom policy implementation is assumed to be
 *       available from the system classloader of the virtual machine
 *       hosting the service.
 *   </table>
 *
 * @see net.jini.activation.arg.ActivationID
 * @see net.jini.io.MarshalledInstance
 * @see java.rmi.Remote
 * @see java.security.CodeSource
 * @see net.jini.export.ProxyAccessor
 *
 * @author Sun Microsystems, Inc.
 *
 */
 
public class ActivateWrapper implements Remote, Serializable {

    /** Configure logger */
    static final Logger logger = Logger.getLogger("org.apache.river.start.wrapper");

    /**
     * The <code>Policy</code> object in effect at startup. 
     */
    private static Policy initialGlobalPolicy;

    /**
     * The "wrapped" activatable object.
     * @serial
     */
    private final Object impl;

    /**
     * The parameter types for the "activation constructor".
     */
    private static final Class[] actTypes = {
	ActivationID.class, String[].class
    };
    
    /** 
     * Fully qualified name of custom, service policy provider 
     */
    private static final String servicePolicyProvider =
	((String) AccessController.doPrivileged(
	    new PrivilegedAction() {
                @Override
		public Object run() {
		    return Security.getProperty(
			    "org.apache.river.start." +
			    "servicePolicyProvider");
		}
	    }));

    /**
     * The parameter types for the 
     * "custom, service policy constructor".
     */
    private static final Class[] policyTypes = {
        Policy.class
    };

    /**
     * Descriptor for registering a "wrapped" activatable object. This
     * descriptor gets stored as  
     * initialization data in the <code>ActivationDesc</code>.
     */
    @AtomicSerial
    public static class ActivateDesc implements Serializable {

        private static final long serialVersionUID = 3L;

	/**
	 * The activatable object's class name.
         * @serial
	 */
	private final String className;
	/**
	 * The codebase where the server classes are loaded from by the
	 * activation group.
         * @serial
	 */
	private final String[] importLocation;
	/**
	 * The codebase where clients should load classes from for stubs, etc.
         * @serial
	 */
	private final String[] exportLocation;
	/**
	 * The security policy filename or URL.
         * @serial
	 */
	private final String policy;
        /**
	 * The activatable object's initialization data.
         * @serial
	 */
        private String[] configurationArguments;
	
	public ActivateDesc(GetArg arg) throws IOException, ClassNotFoundException
	{
	    this(arg.get("className", null, String.class),
		    Valid.nullElement(arg.get("importLocation", null, String[].class),
			    "importLocation cannot contain null elements"),
		    Valid.nullElement(arg.get("exportLocation", null, String[].class),
			    "exportLocation cannot contain null elements"),
		    arg.get("policy", null, String.class),
		    arg.get("configurationArguments", null, String[].class));
	}
        
        public static ActivateDesc parse(String[] args) throws URISyntaxException{
            String delimiter = "|";
            String className = null;
            List<String> importLocation = new ArrayList<String>();
            List<String> exportLocation = new ArrayList<String>();
            String policy = null;
            List<String> serverArgs = new ArrayList<String>();
            int delimeterCount = 0;
            for (int i = 0, l = args.length; i < l; i++){
                if (delimiter.equals(args[i])){
                    delimeterCount++;
                    continue;
                }
                switch (delimeterCount){
                    case 0: className = args[i];
                            continue;
                    case 1: importLocation.add(new Uri(args[i]).toString());
                            continue;
                    case 2: exportLocation.add(new Uri(args[i]).toString());
                            continue;
                    case 3: policy = args[i];
                            continue;
                    case 4: serverArgs.add(args[i]);
                            continue;
                    default : throw new IllegalArgumentException("Delimeter count too high: " + delimeterCount + " Parameters: " + Arrays.asList(args));
                }
            }
            return new ActivateDesc(className,
                                    importLocation.toArray(new String[importLocation.size()]),
                                    exportLocation.toArray(new String[exportLocation.size()]),
                                    policy,
                                    serverArgs.toArray(new String[serverArgs.size()])
            );
        }
        
        /**
         * 
         * @param className
         * @param importLocation
         * @param exportLocation
         * @param policy
         * @param configurationArguments 
         */
        public ActivateDesc(String className,
                            URL[] importLocation,
                            URL[] exportLocation,
                            String policy,
                            String[] configurationArguments)
        {
            this.className = className;
	    this.importLocation = asString(importLocation);
	    this.exportLocation = asString(exportLocation);
	    this.policy = policy;
	    this.configurationArguments = configurationArguments;
        }
        
        /**
         * 
         * @param className
         * @param importLocation
         * @param exportLocation
         * @param policy
         * @param configurationArguments 
         */
        public ActivateDesc(String className,
                            String[] importLocation,
                            String[] exportLocation,
                            String policy,
                            String[] configurationArguments)
        {
            this.className = className;
	    this.importLocation = Valid.copy(importLocation);
	    this.exportLocation = Valid.copy(exportLocation);
	    this.policy = policy;
	    this.configurationArguments = configurationArguments;
        }
        
        @Override
        public boolean equals(Object o){
            if (this == o) return true;
            if (!(o instanceof ActivateDesc)) return false;
            ActivateDesc that = (ActivateDesc) o;
            if (!Objects.equals(this.className, that.className)) return false;
            if (!Objects.equals(this.policy, that.policy)) return false;
            if (!Arrays.equals(this.importLocation, that.importLocation)) return false;
            if (!Arrays.equals(this.exportLocation, that.exportLocation)) return false;
            return Arrays.equals(this.configurationArguments, that.configurationArguments);
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 89 * hash + Objects.hashCode(this.className);
            hash = 89 * hash + Arrays.deepHashCode(this.importLocation);
            hash = 89 * hash + Arrays.deepHashCode(this.exportLocation);
            hash = 89 * hash + Objects.hashCode(this.policy);
            hash = 89 * hash + Arrays.deepHashCode(this.configurationArguments);
            return hash;
        }
        // Javadoc inherited from supertype
        @Override
	public String toString() {
            StringBuilder builder = new StringBuilder(260);
	    return builder.append("[className=").append(className).append(",")
	        .append("importLocation=" )
                .append( ((importLocation == null) 
                    ? null : Arrays.asList(importLocation)) )
                .append(",")
	        .append("exportLocation=")
                .append( ((exportLocation == null) 
                    ? null : Arrays.asList(exportLocation)) )
                .append(",")                    
	        .append("policy=").append(policy).append( ",")
	        .append("configurationArguments=").append( configurationArguments == null ? null 
                        : Arrays.asList(configurationArguments)).append("]").toString();
	}
        
        public String[] asArguments(){
            String delimiter = "|";
            List<String> result = new ArrayList<String>();
            result.add(className);
            result.add(delimiter);
            if (importLocation != null) result.addAll(Arrays.asList(importLocation));
            result.add(delimiter);
            if (exportLocation != null ) result.addAll(Arrays.asList(exportLocation));
            result.add(delimiter);
            result.add(policy);
            result.add(delimiter);
            if (configurationArguments !=null ) 
                result.addAll(Arrays.asList(configurationArguments));
            return result.toArray(new String[result.size()]);
        }
        
        public URL[] importLocation() throws MalformedURLException{
            return asURL(importLocation);
        }
        
        public URL[] exportLocation() throws MalformedURLException{
            return asURL(exportLocation);
        }
        
        public String className(){
            return className;
        }
        
        public String policy(){
            return policy;
        }
        
        public String [] configurationArguments(){
            return configurationArguments != null ? configurationArguments.clone() : null;
        }
        
        private static URL[] asURL(String[] url) throws MalformedURLException{
            if (url == null) return null;
            URL[] result = new URL[url.length];
            for (int i = 0, l = url.length; i < l; i++){
                result[i] = new URL(url[i]);
            }
            return result;
        }
        
        private static String[] asString(URL[] url) {
            if (url == null) return null;
            String[] result = new String[url.length];
            for (int i = 0, l = url.length; i < l; i++){
                result[i] = url[i].toString();
            }
            return result;
        }
    }

    /**
     * A simple subclass of <code>PreferredClassLoader</code> that overrides 
     * <code>getURLs</code> to
     * return the <code>URL</code>s of the provided export codebase. 
     * <code>getURLs</code>
     * is called by the RMI subsystem in order to annotate objects
     * leaving the virtual machine. 
     */
    /* 
     * Implementation note. Subclasses of this class that override 
     * getClassAnnotation might need to override getURLs because getURLs
     * uses a "cached" version of the export annotation.
     */
    static class ExportClassLoader extends PreferredClassLoader 
    {
        /** Cached value of the provided export codebase <code>URL</code>s */
	private final URL[] exportURLs;

        /** Id field used to make toString() unique */
	private final Uuid id = UuidFactory.generate();

	/** Trivial constructor that calls
         * <pre>
         * super(importURLs, parent, urlsToPath(exportURLs), false);
         * </pre>
	 *  and assigns <code>exportURLs</code> to an internal field.
	 */
	public ExportClassLoader(URL[] importURLs, URL[] exportURLs,
	    ClassLoader parent) 
        {
	    super(importURLs, parent, urlsToPath(exportURLs), false);
            // Not safe to call getClassAnnotation() w/i cons if subclassed,
            // so need to redo "super" logic here.
	    if (exportURLs == null) {
                this.exportURLs = importURLs;
            } else {
                this.exportURLs = exportURLs;
            }
	}
	//Javadoc inherited from super type
        @Override
        public URL[] getURLs() {
	    return (URL[])exportURLs.clone();
	}

        // Javadoc inherited from supertype
        @Override
	public String toString() {
            StringBuilder builder = new StringBuilder(200);
            URL[] urls = super.getURLs();
	    return builder
                .append(this.getClass().getName())
		.append( "[importURLs=" )
                .append(urls==null?null:Arrays.asList(urls))
                .append(",")
	        .append("exportURLs=") 
                .append(exportURLs==null?null:Arrays.asList(exportURLs))
                .append(",")                   
	        .append("parent=").append(getParent())
                .append(",")                   
	        .append("id=").append(id)
                .append("]").toString();
	}
    }
    
    /**
     * Activatable constructor. This constructor: 
     * <UL>
     * <LI>Retrieves an <code>ActivateDesc</code> from the 
     *     provided <code>data</code> parameter.
     * <LI>creates an <code>ExportClassLoader</code> using the 
     *     import and export codebases obtained from the provided 
     *     <code>ActivateDesc</code>, 
     * <LI>checks the import codebase(s) for the required 
     *     <code>SharedActivationPolicyPermission</code>
     * <LI>loads the "wrapped" activatable object's class and
     *     calls its activation constructor with the context classloader
     *     set to the newly created <code>ExportClassLoader</code>.
     * <LI> resets the context class loader to the original 
     *      context classloader
     * </UL>
     * The first instance of this class will also replace the VM's 
     * existing <code>Policy</code> object, if any,  
     * with a <code>DynamicPolicyProvider</code> if it is not an instance of
     * <Code>DynamicPolicy</code>. 
     *
     * @param id The <code>ActivationID</code> of this object
     * @param data The activation data for this object
     * @throws java.lang.Exception
     *
     * @see org.apache.river.start.ActivateWrapper.ExportClassLoader
     * @see org.apache.river.start.ActivateWrapper.ActivateDesc
     * @see java.security.Policy
     * @see DynamicPolicy
     *
     */
    public ActivateWrapper(ActivationID id, String[] data)
	throws Exception
    {
         try {
            logger.entering(ActivateWrapper.class.getName(), 
	        "ActivateWrapper", new Object[] {
                    id, data != null ? Arrays.asList(data): null });

	    ActivateDesc desc = ActivateDesc.parse(data);
	    logger.log(Level.FINEST, "ActivateDesc: {0}", desc);

	    ExportClassLoader cl = null;
            try {
	        cl = new ExportClassLoader(desc.importLocation(), 
	                                   desc.exportLocation(),
                                           ClassLoader.getSystemClassLoader());
	        logger.log(Level.FINEST, "Created ExportClassLoader: {0}", cl);
            } catch (Exception e) {
	        logger.throwing(ActivateWrapper.class.getName(), 
	            "ActivateWrapper", e);
	        throw e;
	    }
	
	    synchronized (ActivateWrapper.class) {
                initialGlobalPolicy = Policy.getPolicy();
                if (!(initialGlobalPolicy instanceof DynamicPolicy)) {
                    initialGlobalPolicy = 
                        new DynamicPolicyProvider(initialGlobalPolicy);
                    Policy.setPolicy(initialGlobalPolicy);
                }
	    }
	
	    boolean initialize = false;
	    Class ac = LoadClass.forName(desc.className, initialize, cl);
 	    logger.log(Level.FINEST, "Obtained implementation class: {0}", ac);

            Constructor constructor = null;
            Constructor [] constructors = ac.getConstructors();
            for(int i=0, l=constructors.length; i<l; i++){
                if (constructors[i].getParameterCount() == 2){
                    Class[] params = constructors[i].getParameterTypes();
                    if ("net.jini.activation.arg.ActivationID".equals(params[0].getCanonicalName())
                    && String[].class.equals(params[1])){
                        constructor = constructors[i];
                        if (!ActivationID.class.equals(params[0]))
                        {
                            StringBuilder sb = new StringBuilder();
                            sb.append("Check your PREFERRED.LIST in META-INF: ClassLoader class visibility incompatibilty, prevents type compatiblity of activation constructor parameters.\n");
                            sb.append("net.jini.activation.arg.ActivationID ClassLoader should be: ")
                              .append(ActivationID.class.getClassLoader()).append("\n");
                            sb.append("found: ").append(params[0].getClassLoader()).append("\n");
                            throw new ClassCastException(sb.toString());
                        }
                    break;
                    }
                }
            }
            if (constructor == null){
                throw new NoSuchMethodException("No suitable public activation constructor signature found for class "+ ac);
            }
            logger.log(Level.FINEST, 
                "Obtained implementation constructor: {0}", 
                constructor);
            impl =
                constructor.newInstance(new Object[]{id, desc.configurationArguments()});
            if (impl instanceof Startable) {
                ((Startable) impl).start();
            } else {
                logger.log( Level.FINE,
                    "Service {0} doesn''t implement {1} {2} {3} {4} {5} {6}", 
                    new Object []
                        {
                            impl.getClass().getCanonicalName(),
                            Startable.class.getCanonicalName(),
                            "this service is likely to suffer from race",
                            "conditions caused by export performed during", 
                            "construction, or threads started while ''this''",
                            "has been allowed to escape during construction",
                            "https://www.securecoding.cert.org/confluence/display/java/TSM01-J.+Do+not+let+the+this+reference+escape+during+object+construction"
                        } 
                );
            }
            logger.log(Level.FINEST, 
                "Obtained implementation instance: {0}", impl);
        } catch (Exception e) {
	    logger.throwing(ActivateWrapper.class.getName(), 
	        "ActivateWrapper", e);
            throw e;
        }
        logger.exiting(ActivateWrapper.class.getName(), 
	    "ActivateWrapper");
    }


    /**
     * Return a reference to service being wrapped in place
     * of this object.
     */
    private Object writeReplace() throws ObjectStreamException {
        Object impl_proxy = impl;
	if (impl instanceof ProxyAccessor) {
	    impl_proxy = ((ProxyAccessor) impl).getProxy();
 	    logger.log(Level.FINEST, 
		"Obtained implementation proxy: {0}", impl_proxy);
	    if (impl_proxy == null) {
		throw new InvalidObjectException(
		    "Implementation's getProxy() returned null");
	    }
	} 
	return impl_proxy;
    }

    /**
     * Register an object descriptor for an activatable remote object so that 
     * is can be activated on demand.For activatable objects that want
     * to use this wrapper mechanism. 
     *
     * @return activation ID of the registered service
     * 
     * @throws ActivationException    if there was a problem registering
     *             the activatable class with the activation system
     * @throws RemoteException        if there was a problem communicating
     *             with the activation system
     */
    public static ActivationID register(ActivationGroupID gid,
				        ActivateDesc desc,
				        boolean restart,
				        ActivationSystem sys)
	throws ActivationException, RemoteException 
    {
        logger.entering(ActivateWrapper.class.getName(), 
	    "register", new Object[] { gid, desc, Boolean.valueOf(restart), sys });
	
	ActivationDesc adesc =
	    new ActivationDescImpl(gid,
		ActivateWrapper.class.getName(),
		null,
		desc.asArguments(),
		restart
	);      
 	logger.log(Level.FINEST, 
	    "Registering descriptor with activation: {0}", adesc);

	ActivationID aid = sys.registerObject(adesc);

        logger.exiting(ActivateWrapper.class.getName(), 
	    "register", aid);
	return aid;
    }
    
    /**
     * Utility method that converts a <code>URL[]</code> 
     * into a corresponding, space-separated string with 
     * the same array elements.
     *
     * Note that if the array has zero elements, the return value is
     * null, not the empty string.
     */
    private static String urlsToPath(URL[] urls) {
//TODO - check if spaces in file paths are properly escaped (i.e.% chars)	
        if (urls == null) {
            return null;
        } else if (urls.length == 0) {
            return "";
        } else if (urls.length == 1) {
            return urls[0].toExternalForm();
        } else {
            StringBuilder path = new StringBuilder(urls[0].toExternalForm());
            for (int i = 1; i < urls.length; i++) {
                path.append(' ');
                path.append(urls[i].toExternalForm());
            }
            return path.toString();
        }
    }
}
