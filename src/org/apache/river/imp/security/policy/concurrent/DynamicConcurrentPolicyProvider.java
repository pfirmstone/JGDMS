

package org.apache.river.imp.security.policy.se;

import java.security.AccessController;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
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
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.security.GrantPermission;
import net.jini.security.policy.DynamicPolicy;
import net.jini.security.policy.PolicyInitializationException;
import net.jini.security.policy.UmbrellaGrantPermission;
import org.apache.river.api.security.Denied;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.imp.security.policy.spi.RevokeableDynamicPolicySpi;
import org.apache.river.api.security.PermissionGrantBuilder;
import org.apache.river.api.security.RevokePermission;
import org.apache.river.api.security.RevokeableDynamicPolicy;
import org.apache.river.imp.security.policy.util.PermissionGrantBuilderImp;
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
    private final Set<PermissionGrant> dynamicGrants; // protected by rwl
    private volatile Policy basePolicy; // effectively final looks after its own sync
    private final ConcurrentMap<ProtectionDomain, PermissionCollection> cache;
    private volatile boolean basePolicyIsDynamic; // Don't use cache if true.
    private volatile boolean revokeable;
    private volatile boolean initialized = false;
    private Logger logger;
    private volatile boolean loggable;
    // do something about some domain permissions for this domain so we can 
    // avoid dead locks due to bug 4911907
    /* This lock Protects denied */
    private final ReentrantReadWriteLock drwl;
    private final ReadLock drl;
    private final WriteLock dwl;
    private final Set<Denied> denied;
    private volatile boolean checkDenied;
    
    
    public DynamicConcurrentPolicyProvider(){
        rwl = new ReentrantReadWriteLock();
        rl = rwl.readLock();
        wl = rwl.writeLock();
        dynamicGrants = new HashSet<PermissionGrant>(30);
        basePolicy = null;
        cache = new ConcurrentWeakIdentityMap<ProtectionDomain, PermissionCollection>();
        basePolicyIsDynamic = false;
        revokeable = true;
        logger = Logger.getLogger("net.jini.security.policy");
        loggable = logger.isLoggable(Level.FINEST);
        drwl = new ReentrantReadWriteLock();
        drl = drwl.readLock();
        dwl = drwl.writeLock();
        denied = new HashSet<Denied>(30);
        checkDenied = false;
    }
    
    /**
     * Idempotent method.
     * 
     * Actually we should consider allowing a null base policy so we can choose our own
     * default.  Or pass in a string for the basePolicy to find it itself.
     * 
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
            if (basePolicy instanceof RevokeableDynamicPolicy ) {
                RevokeableDynamicPolicy rp = (RevokeableDynamicPolicy) basePolicy;
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
        basePolicy.implies(sysDomain, new AllPermission());
        PermissionCollection pc = getPermissions(sysDomain);
        pc = PolicyUtils.toConcurrentPermissionsCopy(pc);
        cache.putIfAbsent(sysDomain, pc);
        ProtectionDomain own = this.getClass().getProtectionDomain();
        basePolicy.implies(own, new AllPermission());
        PermissionCollection mypc = getPermissions(own);
        mypc = PolicyUtils.toConcurrentPermissionsCopy(mypc);
        cache.putIfAbsent(own, mypc);
        new GrantPermission(new UmbrellaGrantPermission());
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
    }

    public PermissionCollection getPermissions(ProtectionDomain domain) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        return basePolicy.getPermissions(domain);
    }
    
    /* River-26 Mark Brouwer suggested making UmbrellaPermission's expandable
     * from Dynamic Grants.  Since it is the preferred behaviour of a
     * RevokableDynamicPolicy, to minimise the Permission's granted by
     * and underlying policy, so they can be later revoked, this is
     * desireable behaviour.
     */ 
    private void expandUmbrella(PermissionCollection pc) {
	if (pc.implies(new UmbrellaGrantPermission())) {
	    List<Permission> l = Collections.list(pc.elements());
	    pc.add(new GrantPermission(
		       l.toArray(new Permission[l.size()])));
	}
    }

    public boolean implies(ProtectionDomain domain, Permission permission) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        if (basePolicyIsDynamic){
            // Total delegation revoke and deny not supported.
            return basePolicy.implies(domain, permission);
        }
        // Will this ProtectionDomain and Permission be denied?
        if (checkDenied){
            try {
                drl.lock();
                Iterator<Denied> itr = denied.iterator();
                while (itr.hasNext()){
                    if ( !itr.next().allow(domain, permission)){
                        return false;
                    }
                }
            } finally { drl.unlock(); }
        }
        // First check our cache if the basePolicy is not dynamic.
        PermissionCollection pc = cache.get(domain);
        if ( pc != null ) {
            if (pc.implies(permission)) return true;           
        }
        // Then check the base policy, this will resolve any unresolved
        // permissions, but we should the add that domain's permissions to
        // our cache, to reduce any contention if the underlying policy
        // is single threaded. 
        else if ( basePolicy.implies(domain, permission)) {
            // only fetch it if we don't have it already.
            PermissionCollection bpc = basePolicy.getPermissions(domain);
           /* Don't use the underlying policy permission collection otherwise
            * we can leak grants in to the underlying policy from our cache,
            * this could then be merged into the PermissionDomain's permission
            * cache negating the possiblity of revoking the permission.  This
            * PolicyUtils method defensively copies or creates new if null.
            */
            pc = PolicyUtils.toConcurrentPermissionsCopy(bpc);
            PermissionCollection existed = cache.putIfAbsent(domain, pc); 
            if ( existed != null ){
                pc = existed;
            }
            expandUmbrella(pc); // We need to avoid using PolicyFileProvider
            return true;
        } else {
            // We just called implies, so UnresolvedPermission's will be
            // resolved, lets cache it, so we definitely have it.
            PermissionCollection bpc = basePolicy.getPermissions(domain);
            pc = PolicyUtils.toConcurrentPermissionsCopy(bpc);
            PermissionCollection existed = cache.putIfAbsent(domain, pc); 
            if ( existed != null ){
                pc = existed;
            }
            expandUmbrella(pc);
        }
        // Once we get to here pc is definitely not null and we have the
        // copy referenced in the cache.
        if (loggable){
            logger.log(Level.FINEST, domain + permission.toString() + 
                    ": Base policy is not dynamic and returned false" );
        }
        // If the base policy doesn't imply a Permission then we should check for dynamic grants
        Collection<Permission> dynamicallyGrantedPermissions = new HashSet<Permission>();
        try {
            rl.lock();
            Iterator<PermissionGrant> it = dynamicGrants.iterator();
            while (it.hasNext()) {
                PermissionGrant ge = it.next();
                if ( ge.implies(domain)) {
                    dynamicallyGrantedPermissions.addAll( Arrays.asList(ge.getPermissions()));
                }
            }               
        } finally { rl.unlock(); }
        if (loggable) {
            logger.log(Level.FINEST, "Grants: " + dynamicallyGrantedPermissions.toString());
        }
        if (dynamicallyGrantedPermissions.isEmpty()) {
            // We have no dynamic grants, but we might have an UmbrellaGrant
            // that has just been expanded, the GrantPermission instanceof
            // is just an optimisation.
           if  (permission instanceof GrantPermission &&
		 pc.implies(permission)) {
                 return true;
           }
            return false;
        }
        Iterator<Permission> dgpi = dynamicallyGrantedPermissions.iterator();
        while (dgpi.hasNext()){
            pc.add(dgpi.next());
        }
        // If we get refreshed the cache could be empty, which is more pedantic
        // however the result may still be true so we'll return it anyway.
        if (loggable) {
            logger.log(Level.FINEST, "PermissionCollection: " + pc.toString());
        }
        // We have added dynamic grants, lets expand them
        expandUmbrella(pc);
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
            Iterator<PermissionGrant> it = dynamicGrants.iterator();
            while (it.hasNext()){
                PermissionGrant pe = it.next();
                if ( pe.isVoid()){
                    it.remove();
                }
            }
        } finally {wl.unlock();}
        ensureDependenciesResolved();
    }

    public boolean grantSupported() {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        return true;
    }

    public void grant(Class cl, Principal[] principals, Permission[] permissions) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        if (permissions == null || permissions.length == 0) {return;}
        if (principals == null){ principals = new Principal[0];}
        if (principals.length > 0) {
	    principals = principals.clone();
	    checkNullElements(principals);
	} 
        permissions = permissions.clone();
        checkNullElements(permissions);
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
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    sm.checkPermission(new GrantPermission(permissions));
	}
        PermissionGrantBuilder pgb = new PermissionGrantBuilderImp();
        PermissionGrant pe = pgb.clazz(cl).principals(principals)
                .permissions(permissions)
                .context(PermissionGrantBuilder.CLASSLOADER)
                .build();
        if (loggable){
            logger.log(Level.FINEST, "Granting: " + pe.toString());
        }
        try {
            wl.lock();
            dynamicGrants.add(pe);           
        } finally {wl.unlock();}
    }
    
    // documentation inherited from DynamicPolicy.getGrants
    public Permission[] getGrants(Class cl, Principal[] principals) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        ClassLoader loader = null;
        if( cl != null ) {
            loader = cl.getClassLoader();
        }
        // defensive copy array
        if (principals != null && principals.length > 0) {
	    principals = principals.clone();
	    checkNullElements(principals);
	}
        Collection<Permission> cperms = new HashSet<Permission>();
        try {
            rl.lock();
            Iterator<PermissionGrant> it = dynamicGrants.iterator();
            while (it.hasNext()) {
                PermissionGrant ge = it.next();
                // We want to capture
                // all grants that may be granted by other means.
                // such as ProtectionDomain, Certificates, CodeSource or Principals alone.
                if ( ge.implies(loader, principals)) {
                    cperms.addAll(Arrays.asList(ge.getPermissions()));
                }     
            }
        } finally { rl.unlock(); }
        Permission[] perms = cperms.toArray(new Permission[cperms.size()]);        
        return perms;
    }
    
    private static void checkNullElements(Object[] array) {
        int l = array.length;
	for (int i = 0; i < l; i++) {
	    if (array[i] == null) {
		throw new NullPointerException();
	    }
	}
    }

    public void grant(List<PermissionGrant> grants) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        // because PermissionGrant's are given references to ProtectionDomain's
        // we must check the caller has this permission.
        AccessController.checkPermission(new RuntimePermission("getProtectionDomain"));
        if (revokeable){
            RevokeableDynamicPolicy bp = (RevokeableDynamicPolicy) basePolicy;
            bp.grant(grants);
            return;
        }
        //List<PermissionGrant> allowed = new ArrayList<PermissionGrant>(grants.size());
        Iterator<PermissionGrant> grantsItr = grants.iterator();
        while (grantsItr.hasNext()){
            PermissionGrant grant = grantsItr.next();
            Permission[] perms = grant.getPermissions();
            AccessController.checkPermission(new GrantPermission(perms));
        }
        // If we get to here, the caller has permission.
        try {
            wl.lock();
            dynamicGrants.addAll(grants);
        } finally {wl.unlock();}
    }

    public void revoke(List<PermissionGrant> grants) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        if (revokeable){
            RevokeableDynamicPolicy bp = (RevokeableDynamicPolicy) basePolicy;
            bp.revoke(grants);
            return;
        }
        AccessController.checkPermission(new RevokePermission());
        try {
            wl.lock();
            dynamicGrants.removeAll(grants);
        } finally {wl.unlock();}
        cache.clear();
    }

    public List<PermissionGrant> getPermissionGrants() {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        if (revokeable){
            RevokeableDynamicPolicy bp = (RevokeableDynamicPolicy) basePolicy;
            return bp.getPermissionGrants();
        }
        ArrayList<PermissionGrant> grants;
        try {
            rl.lock();
            grants = new ArrayList<PermissionGrant>(dynamicGrants.size());
            grants.addAll(dynamicGrants);
            return grants;
        }finally {rl.unlock();}
    }

    public void add(List<Denied> denials) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        AccessController.checkPermission(new RuntimePermission("getProtectionDomain"));
        AccessController.checkPermission(new RevokePermission());
        checkDenied = true;
        try{
            dwl.lock();
            denied.addAll(denials);
        } finally { dwl.unlock();}
    }

    public void remove(List<Denied> denials) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        try{
            dwl.lock();
            denied.removeAll(denials);
            if ( denied.isEmpty()) { checkDenied = false; }
        } finally { dwl.unlock();}
    }

    public List<Denied> getDenied() {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        List<Denied> denials;
        try{
            drl.lock();
            denials = new ArrayList<Denied>(denied.size());
            denials.addAll(denied);
            return denials;
        } finally { dwl.unlock();}
    }

}
