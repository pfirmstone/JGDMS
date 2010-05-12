

package org.apache.river.imp.security.policy.se;

import java.security.AccessController;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LoggingPermission;
import net.jini.security.GrantPermission;
import net.jini.security.policy.DynamicPolicy;
import net.jini.security.policy.PolicyInitializationException;
import org.apache.river.api.security.RevokePermission;
import org.apache.river.imp.security.policy.spi.RevokeableDynamicPolicySpi;
import org.apache.river.api.security.RevokeablePolicy;
import org.apache.river.imp.security.policy.util.PolicyEntry;
import org.apache.river.imp.security.policy.util.PolicyUtils;
import org.apache.river.imp.util.ConcurrentWeakIdentityMap;

/**
 * <p>This is a Dynamic Policy Provider that supports concurrent access,
 * for instances where a Policy provider is used for a distributed network
 * of computers, or where there is a large number of ProtectionDomains and
 * hence the opportunity for concurrency exists, concurrency comes with a 
 * cost however, that of increased memory usage.</p>
 * 
 * <p>Due to the Java 2 Security system's static design, a Policy Provider
 * can only augment the policy files utilised, that is it can only relax security
 * by granting additional permissions, this implementation adds an experimental 
 * feature for revoking permissions, however there are some caveats:</p>
 * 
 * <p>Firstly if the Policy.refresh() method is called, followed by the 
 * ProtectionDomain.toString() method, the ProtectionDomain
 * merge the permissions, from the policy with those in the ProtectionDomain, 
 * a ProtectionDomain cannot have Permissions
 * removed, only additional merged. </p>
 * 
 * <p>So in order to prevent dynamic grants from finding
 * their way into a ProtectionDomain's private PermissionCollection,
 * one would have to ensure that no dynamically grantable permissions are 
 * returned via the methods:</p>
 * <p>
 * getPermissions(Codesource source) or
 * getPermissions(ProtectionDomain domain)
 * </p>
 * <p>This is different to the behaviour of the existing Jini 2.0
 * DynamicPolicyProvider implementation where dynamically granted Permissions
 * are added.
 * 
 * However when a Policy is checked via implies(ProtectionDomain d, Permission p)
 * this implementation checks the dynamic grants
 * 
 * This means that if a DynamicPolicy is utilised as the base Policy class
 * and if it returns dynamically granted permissions, then those permissions
 * cannot be revoked.</p>
 * <p>
 * It is thus reccommeded that Static policy files only be used for files
 * where the level of trust is relatively static.  This is the only implementation
 * where a dyanamic grant can be removed.  In the case of Proxy trust, a proxy
 * is no longer trusted when it has lost contact with it's Principal (server)
 * because the server cannot be asked if it trusts it's proxy and the proxy
 * cannot be given a thread of control to find it's server because it has
 * already attained too many Permissions.  In this new implementation it should
 * be possible to revoke AllPermission and grant Permissions dynamically as 
 * trust is gained.</p>
 * <p>
 * This may cause some undesireable side effects in existing programs.
 * </p><p>
 * There is one final reccommendation and that is adopting / utilising an OSGi
 * Framework to enable far greater control over dynamic Permissions than this hack
 * implementation provides.
 * </p><p>
 * To make the best utilisation of this Policy provider, set the System property:
 * </p>,<p>
 * net.jini.security.policy.PolicyFileProvider.basePolicyClass = 
 * org.apache.river.security.concurrent.ConcurrentPolicyFile
 * </p>
 * @author Peter Firmstone
 * @version 1
 * @since 2.2
 * @see ProtectionDomain
 * @see Policy
 * @see ConcurrentPolicyFile
 * @see net.jini.security.policy.PolicyFileProvider
 * @see ConcurrentPermissionCollection
 */

public class DynamicConcurrentPolicyProvider implements RevokeableDynamicPolicySpi {
    private static final ProtectionDomain sysDomain = 
	AccessController.doPrivileged(new PrivilegedAction<ProtectionDomain>() {
	    public ProtectionDomain run() { return Object.class.getProtectionDomain(); }
	});
    
    // A set of PolicyEntries constituting this Policy.
    // PolicyEntry is lighter weight than PermissionCollection.
    private final ReentrantReadWriteLock rwl;
    private final ReadLock rl;
    private final WriteLock wl;    
    private final Set<PolicyEntry> dynamicGrants; // protected by rwl
    private volatile Policy basePolicy; // effectively final looks after its own sync
    private final ConcurrentMap<ProtectionDomain, PermissionCollection> cache;
    private volatile boolean basePolicyIsDynamic; // Don't use cache if true.
    private volatile boolean revokeable;
    private volatile boolean initialized = false;
    private Logger logger;
    private volatile boolean loggable;
    // do something about some domain permissions for this domain so we can 
    // avoid dead locks due to bug 4911907
    
    
    public DynamicConcurrentPolicyProvider(){
        rwl = new ReentrantReadWriteLock();
        rl = rwl.readLock();
        wl = rwl.writeLock();
        dynamicGrants = new HashSet<PolicyEntry>(30);
        basePolicy = null;
        cache = new ConcurrentWeakIdentityMap<ProtectionDomain, PermissionCollection>();
        basePolicyIsDynamic = false;
        revokeable = true;
        logger = Logger.getLogger("net.jini.security.policy");
        loggable = logger.isLoggable(Level.INFO);
    }
    
    /**
     * Idempotent method.
     * @param basePolicy
     * @return
     */
    public boolean basePolicy(Policy basePolicy) {
        if (initialized == true) return false;
        this.basePolicy = basePolicy;
        return true;
    }

    /** 
     * Idempotent method. 
     * @throws net.jini.security.policy.PolicyInitializationException 
     */
    public void initialize() throws PolicyInitializationException {
        if (basePolicy == null) throw new PolicyInitializationException("Base Policy hasn't " +
                "been set cannot initialize", new Exception("Failed to initialize"));
        if (basePolicy instanceof DynamicPolicy) {
            DynamicPolicy dp = (DynamicPolicy) basePolicy;
            basePolicyIsDynamic = dp.grantSupported();
            revokeable = false;
            if (basePolicy instanceof RevokeablePolicy ) {
                RevokeablePolicy rp = (RevokeablePolicy) basePolicy;
                revokeable = rp.revokeSupported();
            }
        }
        initialized = true;
    }

    /**
     * Ensures that any classes depended on by this policy provider are
     * resolved.  This is to preclude lazy resolution of such classes during
     * operation of the provider, which can result in deadlock as described by
     * bug 4911907.
     */
    public void ensureDependenciesResolved() {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        // Investigate bug 4911907, do we need to do anything more here? Is this sufficient.
        if (sysDomain == null ) System.out.println("System Domain is null");
        implies(sysDomain, new AllPermission());
    }

    public void revoke(Class cl, Principal[] principals, Permission[] permissions) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        if (permissions == null) return;
        SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    sm.checkPermission(new RevokePermission());
	}
        if (basePolicyIsDynamic){
            // Revoke not supported unless the base policy supports it.
            if (revokeable){
                RevokeablePolicy rp = (RevokeablePolicy) basePolicy;
                rp.revoke(cl, principals, permissions);
            }
            return;
        }
        /* The removal begins with removal from the dynamicGrants then the cache
         * while dynamicGrants is write locked, otherwise it could be added 
         * again due to a concurrent implies during the removal process.
         * Actually the decision has been made to release the lock as soon as
         * possible to prevent possible deadlocks, increase concurrency at the
         * risk of possible positive implies() the the mean time.
         */       
        ProtectionDomain domain = cl.getProtectionDomain();
        CodeSource codeSource = domain.getCodeSource();
        if (principals == null){
            principals = new Principal[0];
        }
        if (principals.length > 0) {
	    principals = principals.clone();
	    checkNullElements(principals);
	}
        permissions = permissions.clone();
	checkNullElements(permissions);        
        Collection<Permission> permToBeRemoved = Arrays.asList(permissions);
        Collection<Permission> remainingGrants = new HashSet<Permission>();
        try {
            wl.lock();
            Iterator<PolicyEntry> it = dynamicGrants.iterator();
            while (it.hasNext()) {
                PolicyEntry ge = it.next();
                if (ge.impliesPrincipals(domain == null ? null : principals)
                    && ge.impliesProtectionDomain(domain)
                    && ge.impliesCodeSource(domain == null ? null : codeSource)) {
                    remainingGrants.addAll( ge.getPermissions());
                    it.remove();
                }               
            }
            if (remainingGrants.isEmpty()) return; // nothing to do.
        } finally { wl.unlock(); }
        /* Now we can remove the PermissionDomain from the cache.
         * The cache will populate itself again correctly when implies() is
         * called on that PermissionDomain again.
         */
        cache.remove(domain.toString());
        /* We must re-enter the remaining grants if any exist. */
        remainingGrants.removeAll(permToBeRemoved);
        PolicyEntry policyEntry = new PolicyEntry(codeSource, 
                Arrays.asList(principals), remainingGrants);
        try {
            wl.lock();
            dynamicGrants.add(policyEntry);
        } finally { wl.unlock(); }
    }

    public boolean revokeSupported() {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        return revokeable;
    }

    public Provider getProvider() {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public String getType() {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public PermissionCollection getPermissions(CodeSource codesource) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        return basePolicy.getPermissions(codesource);
//      The following code breaks revoke.
//        Collection<Permission> dynamicallyGrantedPermissions = getGrants(codesource, null);
//        PermissionCollection pc = null;
//        pc = basePolicy.getPermissions(codesource);
//        if (pc == null) pc = new Permissions();       
//        Iterator<Permission> dgpi = dynamicallyGrantedPermissions.iterator();
//        while (dgpi.hasNext()){
//            pc.add(dgpi.next());
//        }
//        return pc;      
    }

    public PermissionCollection getPermissions(ProtectionDomain domain) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        return basePolicy.getPermissions(domain);
//      The following code breaks revoke.      
//        CodeSource cs = (domain == null ? null : domain.getCodeSource());
//        Principal[] pals = (domain == null ? null : domain.getPrincipals());
//        Collection<Permission> dynamicallyGrantedPermissions = getGrants(cs, pals);
//        PermissionCollection pc = null;
//        pc = cache.get(domain); // saves new object creation.
//        if (pc == null){
//            pc = basePolicy.getPermissions(domain);
//            //if (pc == null) pc = new ConcurrentPermissions();
//            if (pc == null) pc = new Permissions();
////            if (!(pc instanceof ConcurrentPermissions)) {
////                pc = PolicyUtils.toConcurrentPermissionsCopy(pc);
////            }           
//            PermissionCollection existed = cache.putIfAbsent(domain, pc);
//            if ( !(existed == null) ){ pc = existed;} //Another thread might have just done it!
//        }        
//        Iterator<Permission> dgpi = dynamicallyGrantedPermissions.iterator();
//        while (dgpi.hasNext()){
//            pc.add(dgpi.next());
//        }
//        return pc;
    }

    public boolean implies(ProtectionDomain domain, Permission permission) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        // First check the our cache if the basePolicy is not dynamic.
        if (!basePolicyIsDynamic) {
            PermissionCollection pc = cache.get(domain);
            if ( pc != null){
                if (pc.implies(permission)) return true;
            }
        }
        // Then check the base policy
        if (basePolicy.implies(domain, permission)) return true;
        if (basePolicyIsDynamic) return false;
        if (loggable){
            logger.log(Level.INFO, domain + permission.toString() + 
                    ": Base policy is not dynamic and returned false" );
        }
        // If it doesn't then we should check for dynamic grants
        Principal[] pals = (domain == null ? null : domain.getPrincipals());
        CodeSource cs = ( domain == null ? null : domain.getCodeSource());
        Collection<Permission> dynamicallyGrantedPermissions = getGrants(domain, cs, pals);
        if (loggable) {
            logger.log(Level.INFO, "Grants: " + dynamicallyGrantedPermissions.toString());
        }
        if (dynamicallyGrantedPermissions.isEmpty()) return false;
        // Operation starts to get expensive
        PermissionCollection pc = null;
        pc = cache.get(domain); // saves new object creation.
        if (pc == null){
            pc = basePolicy.getPermissions(domain);
           /* Don't use the underlying policy permission collection otherwise
            * we can leak grants in to the underlying policy from our cache,
            * this could then be merged into the PermissionDomain's permission
            * cache negating the possiblity of revoking the permission.  This
            * PolicyUtils method defensively copies or creates new if null.
            */
            pc = PolicyUtils.toConcurrentPermissionsCopy(pc);                  
            PermissionCollection existed = cache.putIfAbsent(domain, pc);
            if ( (existed != null) ){ pc = existed;} //Another thread might have just done it!
        }        
        Iterator<Permission> dgpi = dynamicallyGrantedPermissions.iterator();
        while (dgpi.hasNext()){
            pc.add(dgpi.next());
        }
        // If we get refreshed the cache could be empty, which is more pedantic
        // however the result may still be true so we'll return it anyway.
        if (loggable) {
            logger.log(Level.INFO, "PermissionCollection: " + pc.toString());
        }
        return pc.implies(permission);
    }
    
    /**
     * Calling refresh doesn't remove any dynamic grant's, it only clears
     * the cache and refreshes the underlying Policy, it also removes any
     * grants for ProtectionDomains that no longer exist.
     */
    public void refresh() {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        cache.clear();
        basePolicy.refresh();
        // Clean up any void grants.
        try {
            wl.lock();
            Iterator<PolicyEntry> it = dynamicGrants.iterator();
            while (it.hasNext()){
                PolicyEntry pe = it.next();
                if ( pe.isVoid()){
                    it.remove();
                }
            }
        } finally {wl.unlock();}
        
    }

    public boolean grantSupported() {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        return true;
    }

    public void grant(Class cl, Principal[] principals, Permission[] permissions) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        if (permissions == null || permissions.length == 0) {
	    return;
	}
        if ( basePolicyIsDynamic ){
            /* Delegate, otherwise, if base policy is an instance of this class, we
             * may have multi combinations of permissions that together should
             * be true but become separated as this implementation will not
             * return any dynamically granted permissions via getPermissions(
             * because doing so would mean loosing revoke ability.
             */
            DynamicPolicy dp = (DynamicPolicy) basePolicy;
            dp.grant(cl, principals, permissions);
            return;
        }
        if (principals == null){
            principals = new Principal[0];
        }
        if (principals.length > 0) {
	    principals = principals.clone();
	    checkNullElements(principals);
	}
        permissions = permissions.clone();
        checkNullElements(permissions);
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    sm.checkPermission(new GrantPermission(permissions));
	}
        Collection<Principal> pal = Arrays.asList(principals);
        Collection<Permission> perm = Arrays.asList(permissions);
        ProtectionDomain domain = null;
        if ( cl != null){
            domain = getDomain(cl);
        }
        PolicyEntry pe = new PolicyEntry(domain, pal, perm);
        if (loggable){
            logger.log(Level.INFO, "Granting: " + pe.toString());
        }
        try {
            wl.lock();
            dynamicGrants.add(pe);           
        } finally {wl.unlock();}
    }
    
    // documentation inherited from DynamicPolicy.getGrants
    public Permission[] getGrants(Class cl, Principal[] principals) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        ProtectionDomain pd = null;
        CodeSource cs = null;
        if ( cl != null ) {
            pd = getDomain(cl);
            cs = pd.getCodeSource();
        }
        // defensive copy array
        if (principals != null && principals.length > 0) {
	    principals = principals.clone();
	    checkNullElements(principals);
	}
        Collection<Permission> cperms = getGrants(pd, cs, principals);
        Permission[] perms = cperms.toArray(new Permission[cperms.size()]);        
        return perms;
    }
    
    private Collection<Permission> getGrants(ProtectionDomain pd, 
            CodeSource cs, Principal[] pals){
        Collection<Permission> dynamicallyGrantedPermissions = new HashSet<Permission>(20);
        try {
            rl.lock();
            Iterator<PolicyEntry> it = dynamicGrants.iterator();
            while (it.hasNext()) {
                PolicyEntry ge = it.next();
                if ( ge.impliesProtectionDomain(pd)
                        && ge.impliesCodeSource(cs)
                        && ge.impliesPrincipals(pals)) {
                    dynamicallyGrantedPermissions.addAll( ge.getPermissions());
                }
            }               
        } finally { rl.unlock(); }
        return dynamicallyGrantedPermissions;
    }
    
    private static void checkNullElements(Object[] array) {
        int l = array.length;
	for (int i = 0; i < l; i++) {
	    if (array[i] == null) {
		throw new NullPointerException();
	    }
	}
    }
       
    private static ProtectionDomain getDomain(final Class cl) {
	ProtectionDomain pd = AccessController.doPrivileged(
	    new PrivilegedAction<ProtectionDomain>() {
		public ProtectionDomain run() { return cl.getProtectionDomain(); }
	    });
	if (pd != sysDomain && pd.getClassLoader() == null) {
	    throw new UnsupportedOperationException(
		"ungrantable protection domain");
	}
            return pd;
    }  

}
