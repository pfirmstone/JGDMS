/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.security.policy;

import java.security.AccessControlException;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.Security;
import java.util.Iterator;
//import java.util.ServiceLoader;
import java.util.logging.Logger;
import java.util.logging.Level;
import sun.misc.Service;
import org.apache.river.security.policy.spi.RevokeablePolicy;
import org.apache.river.security.policy.spi.RevokeableDynamicPolicySpi;

/**
 * This class replaces the existing DynamicPolicyProvider, the existing 
 * implementation has been modified to partially
 * implement RevokableDynamiPolicySpi but doing so in a manner compatible with
 * Java 1.4.  In that implementation it will throw an exception for the revoke
 * method unless the additional work required has sufficient
 * demand, in which case it may be implemented for java 1.4 also.
 * 
 * I would have liked to use the java 6 ServiceLoader in preference
 * to the Java 1.4 and cdc (java 1.4) foundation profile 1.1.2 compatible
 * forbidden sun.misc.Service implementation, since ServiceLoader doesn't exist
 * in Java 1.4, this might be better as an OSGi service.  A dependency could
 * be resolved automatically based on the platform.
 * 
 * @author Peter Firmstone
 */
public class DynamicPolicyProvider extends Policy implements RevokeablePolicy {
  
    //Java 1.4 compatible
    @SuppressWarnings("unchecked")
    private synchronized static RevokeableDynamicPolicySpi getDynamicPolicy()
    throws AccessControlException {
        RevokeableDynamicPolicySpi rdps;
        rdps = (RevokeableDynamicPolicySpi) AccessController.doPrivileged( 
                new PrivilegedAction(){
            public Object run(){       
                Iterator sp = Service.providers(RevokeableDynamicPolicySpi.class);
                    while (sp.hasNext()){
                        RevokeableDynamicPolicySpi inst = 
                                (RevokeableDynamicPolicySpi) sp.next();
                        if (inst != null){
                            return inst;
                    }               
                }
                return null;
            }
        });
        return rdps;
    }
   
    private static final Logger logger = 
            Logger.getLogger("net.jini.security.policy");
// Debugging can be done with an SPI implementation    
//    /* If true, always grant permission */
//    @SuppressWarnings("unchecked")
//    private static volatile boolean grantAll =
//	((Boolean) AccessController.doPrivileged(
//	    new PrivilegedAction() {
//		public Object run() {
//		    return Boolean.valueOf(
//			Security.getProperty(
//			    "net.jini.security.policy.grantAllandLog"));
//		}
//	    })).booleanValue();
            
    private static final String basePolicyClassProperty =
	"net.jini.security.policy." +
	"DynamicPolicyProvider.basePolicyClass";
    
    private static Policy getBasePolicy() throws PolicyInitializationException {
        String cname = "net.jini.security.policy.PolicyFileProvider";
        Policy basePolicy = null;
        try {
            String bpc = Security.getProperty(basePolicyClassProperty);
            if (bpc != null) cname = bpc;
            basePolicy = (Policy) Class.forName(cname).newInstance();
        } catch (InstantiationException ex) {
            if (logger.isLoggable(Level.SEVERE)){
                logger.log(Level.SEVERE, null, ex);
            }
            throw new PolicyInitializationException(
                    "Unable to create a new instance of: " +
                    cname, ex);
        } catch (IllegalAccessException ex) {
            if (logger.isLoggable(Level.SEVERE)){
                logger.logp(Level.SEVERE, "DynamicPolicyProviderImpl",
                        "initialize()", "Unable to create a new instance of: " +
                        cname, ex);
            }
            throw new PolicyInitializationException(
                    "Unable to create a new instance of: " +
                    cname, ex);
        } catch (ClassNotFoundException ex) {
            if (logger.isLoggable(Level.SEVERE)){
                logger.log(Level.SEVERE, "Check " + cname + " is accessable" +
                        "from your classpath", ex);
            }
            throw new PolicyInitializationException(
                    "Unable to create a new instance of: " +
                    cname, ex);
//        } catch (SecurityException ex) {
//            if (logger.isLoggable(Level.SEVERE)){
//                logger.log(Level.SEVERE,
//                        "You don't have sufficient permissions to create" +
//                        "a new instance of" + cname, ex);
//            }
//            throw new PolicyInitializationException(
//                    "Unable to create a new instance of: " +
//                    cname, ex);
        } 
        return basePolicy;
    }
   
    // Try using an enum here for optional loading?  Or just get whatever is available?
    private RevokeableDynamicPolicySpi instance; //= getDynamicPolicy();
    
    
    // The original implementation wraps a base Policy we still need that
    // don't return until the instance has been created properly.
    // This is undesireable for revokeable permissions.
     /**
     * Creates a new <code>DynamicPolicyProvider</code> instance that wraps a
     * default underlying policy.  The underlying policy is created as follows:
     * if the
     * <code>net.jini.security.policy.DynamicPolicyProvider.basePolicyClass</code>
     * security property is set, then its value is interpreted as the class
     * name of the base (underlying) policy provider; otherwise, a default
     * class name of
     * <code>"net.jini.security.policy.PolicyFileProvider"</code>
     * is used.  The base policy is then instantiated using the no-arg public
     * constructor of the named class.  If the base policy class is not found,
     * is not instantiable via a public no-arg constructor, or if invocation of
     * its constructor fails, then a <code>PolicyInitializationException</code>
     * is thrown.
     * <p>
     * Note that this constructor requires the appropriate
     * <code>"getProperty"</code> {@link java.security.SecurityPermission} to
     * read the
     * <code>net.jini.security.policy.DynamicPolicyProviderImpl.basePolicyClass</code>
     * security property, and may require <code>"accessClassInPackage.*"</code>
     * {@link RuntimePermission}s, depending on the package of the base policy
     * class.
     *
     * @throws  PolicyInitializationException if unable to construct the base
     *          policy
     * @throws  SecurityException if there is a security manager and the
     *          calling context does not have adequate permissions to read the
     *          <code>net.jini.security.policy.DynamicPolicyProviderImpl.basePolicyClass</code>
     *          security property, or if the calling context does not have
     *          adequate permissions to access the base policy class
     */
    public DynamicPolicyProvider() throws PolicyInitializationException {
        this(getBasePolicy());
    }
    
   /**
     * Creates a new <code>DynamicPolicyProvider</code> instance that wraps
     * around the given non-<code>null</code> base policy object.
     *
     * @param   basePolicy base policy object containing information about
     *          non-dynamic grants
     * @throws  NullPointerException if <code>basePolicy</code> is
     * 		<code>null</code>
     */
    public DynamicPolicyProvider(Policy basePolicy) {
            if (basePolicy == null) {
                throw new NullPointerException();
            }
        try {
            instance = getDynamicPolicy();
        } catch (AccessControlException ex) {
            if (logger.isLoggable(Level.CONFIG)) {
               logger.logp(Level.CONFIG,
                   DynamicPolicyProvider.class.toString(), 
                   "DynamicPolicyProvider(Policy basePolicy) constructor",
                   "If you see this message, it means that you need to grant" +
                   "the java.lang.RuntimePermission accessClassInPackage.sun.misc" +
                   "in your Policy file in order to take advantage of " +
                   "the RevokeableDynamicPolicyProviderSpi", ex);           
            }
        }           
        if (instance == null) {            
            instance = new DynamicPolicyProviderImpl();            
        }
        try {
            instance.basePolicy(basePolicy);
            instance.initialize();
        } catch (PolicyInitializationException ex) {
            logger.log(Level.SEVERE, "This should never happen, since" +
                    "basePolicy is not null", ex);
        }
        instance.ensureDependenciesResolved();    
    }

    public boolean grantSupported() {
        return instance.grantSupported();
    }

    public void grant(Class cl, Principal[] principals, Permission[] permissions) {
        instance.grant(cl, principals, permissions);
    }

    public Permission[] getGrants(Class cl, Principal[] principals) {
        return instance.getGrants(cl, principals);
    }

    public void revoke(Class cl, Principal[] principals, Permission[] permissions) {
        instance.revoke(cl, principals, permissions);
    }
    
    @Override
    public PermissionCollection getPermissions(CodeSource codesource) {
	return instance.getPermissions(codesource);
    }
    
    /**
     * Return a PermissionCollection object containing the set of
     * permissions granted to the specified ProtectionDomain.
     *
     * <p> Applications are discouraged from calling this method
     * since this operation may not be supported by all policy implementations.
     * Applications should rely on the <code>implies</code> method
     * to perform policy checks.
     *
     * <p> The default implementation of this method first retrieves
     * the permissions returned via <code>getPermissions(CodeSource)</code>
     * (the CodeSource is taken from the specified ProtectionDomain),
     * as well as the permissions located inside the specified ProtectionDomain.
     * All of these permissions are then combined and returned in a new
     * PermissionCollection object.  If <code>getPermissions(CodeSource)</code>
     * returns Policy.UNSUPPORTED_EMPTY_COLLECTION, then this method
     * returns the permissions contained inside the specified ProtectionDomain
     * in a new PermissionCollection object.
     *
     * <p> This method can be overridden if the policy implementation
     * supports returning a set of permissions granted to a ProtectionDomain.
     *
     * @param domain the ProtectionDomain to which the returned
     *		PermissionCollection has been granted.
     *
     * @return a set of permissions granted to the specified ProtectionDomain.
     *		If this operation is supported, the returned
     *		set of permissions must be a new mutable instance
     *		and it must support heterogeneous Permission types.
     *		If this operation is not supported,
     *		Policy.UNSUPPORTED_EMPTY_COLLECTION is returned.
     *
     * @since 1.4
     */
    @Override
    public PermissionCollection getPermissions(ProtectionDomain domain) {
        return instance.getPermissions(domain);
    }
    
    /**
     * Evaluates the global policy for the permissions granted to
     * the ProtectionDomain and tests whether the permission is 
     * granted.
     *
     * @param domain the ProtectionDomain to test
     * @param permission the Permission object to be tested for implication.
     *
     * @return true if "permission" is a proper subset of a permission
     * granted to this ProtectionDomain.
     *
     * @see java.security.ProtectionDomain
     * @since 1.4
     */
    @Override
    public boolean implies(ProtectionDomain domain, Permission permission) {
          return instance.implies(domain, permission);
// Debugging can be done with an SPI implementation.
//        if (grantAll == false) {
//            return instance.implies(domain, permission);
//        }
//        boolean result = instance.implies(domain, permission);
//        if (result == false){
//            logger.logp(Level.INFO, "instance.getClass().getName()",
//                    "implies(ProtectionDomain domain, Permission permission",
//                    "domain.toString(), permission.toString() returned false");
//            return true;
//        }
//        return result;
    }
    
    @Override
    public void refresh() {
        instance.refresh();
    }

    public boolean revokeSupported() {
        return instance.revokeSupported();
    }

    public Object parameters() throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
   
}
