

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 * can only augment the policy files utilised, a Policy can only relax security
 * by granting additional permissions, this implementation adds an experimental 
 * feature for revoking permissions, however there are some caveats:</p>
 * 
 * <p>Background: if ProtectionDomain.toString(), is called a ProtectionDomain will
 * merge Permissions, from the policy with those in the ProtectionDomain,
 * in a new private instance of Permissions, thus a ProtectionDomain cannot have 
 * Permission's removed, only additional merged.  A ProtectionDomain must
 * be created with the dynamic constructor otherwise it will never consult
 * the policy.  The AccessController.checkPermission(Permission) method
 * consults the current AccessControlContext, which contains all
 * ProtectionDomain's on the current thread's stack, before consulting the
 * AccessControllContext.checkPermission(Permission), it calls
 * AccessControllContext.optimize() which  removes all duplicate ProtectionDomains
 * in the ProtectionDomain array[]'s from the
 * enclosing AccessContolContext for the execution domain and the nested
 * AccessControlContext for the privileged domain (the privileged domain is
 * an array of ProtectionDomain's on the stack since the last 
 * AccessController.doPriveleged() call).  The optimize() method also calls
 * the DomainCombiner, which, for example, gives the SubjectDomainCombiner the 
 * opportunity to manipulate the ProtectionDomain's in the privileged array, in the
 * SubjectDomainCombiner's case, it creates new copies of the ProtectionDomain's
 * with new Principal[]'s injected.  The optimize() method returns a new
 * optimized AccessControlContext.
 * </p><p>
 * Now the AccessController calls the new AccessControlContext.checkPermission(Permission),
 * at this stage, each ProtectionDomain, if created with the dynamic constructor
 * consults the Policy, calling Policy.implies(ProtectionDomain, Permission).
 * </p><p>
 * If any calls to the policy return false, the ProtectionDomain then checks its
 * internal Permissions and if they return false, it returns false.  The first
 * ProtectionDomain in the AccessControlContext to return false causes the 
 * AccessController.checkPermission(Permission) to throw an AccessControlException
 * </p><p>
 * To optimise the time taken to check Permission's the ProtectionDomain's
 * should either be static, which excludes the Policy, or dynamic with
 * a null PermissionCollection in it's constructor, </p>
 * 
 * <p>So in order to prevent dynamic grants from finding
 * their way into a ProtectionDomain's private PermissionCollection,
 * one would have to ensure that no dynamically grantable permissions are 
 * returned via the method:</p>
 * <p>
 * getPermissions(ProtectionDomain domain) and
 * getPermissions(Codesource source) as a precaution.
 * </p>
 * <p>This is different to the behaviour of the existing Jini 2.0
 * DynamicPolicyProvider implementation where dynamically granted Permissions
 * are added and can escape into the ProtectionDomain's private PermissionCollection.
 * 
 * However when a Policy is checked via implies(ProtectionDomain d, Permission p)
 * this implementation checks the dynamic grants
 * 
 * This means that if a DynamicPolicy is utilised as the base Policy class
 * and if it returns dynamically granted permissions, then those permissions
 * cannot be revoked.</p>
 * <p>
 * It is thus reccommeded that Static policy files only be used for setting
 * up your priveleged code and use UmbrellaGrantPermission's and grant 
 * all other Permission's using dynamic grants.  This minimises the double 
 * checking of Permission, that occurs when a ProtectionDomain is constructed
 * so it contains a default PermissionCollection that is not null.
 *
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
    
    /* reference update Protected by grantLock, this array reference must only 
     * be copied or replaced, it must never be read directly or operated on 
     * unless holding grantLock.
     * Local methods must first copy the reference before using the array in
     * loops etc in case the reference is updated.
     */
    private volatile PermissionGrant[] pGrants;
    /* This lock protects adding and removal of PermissionGrant's*/
    private final Object grantLock;
    //private final Set<PermissionGrant> dynamicGrants; // protected by rwl
    private volatile Policy basePolicy; // effectively final looks after its own sync
    private final ConcurrentMap<ProtectionDomain, PermissionCollection> cache;
    private final ConcurrentMap<PermissionGrant, Permission[]> grantCache;
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
	pGrants = new PermissionGrant[0];
        basePolicy = null;
        cache = new ConcurrentWeakIdentityMap<ProtectionDomain, PermissionCollection>();
	grantCache = new ConcurrentWeakIdentityMap<PermissionGrant, Permission[]>();
        basePolicyIsDynamic = false;
        revokeable = true;
        logger = Logger.getLogger("net.jini.security.policy");
        loggable = logger.isLoggable(Level.FINEST);
        drwl = new ReentrantReadWriteLock();
        drl = drwl.readLock();
        dwl = drwl.writeLock();
        denied = new HashSet<Denied>(30);
        checkDenied = false;
	grantLock = new Object();
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
        } else {
	    /* We should not be calling implies on the base Policy, if
	     * there are UnresolvedPermission's that are undergoing resolution
	     * while another Permission within that collection is already
	     * resolved, the Enumeration may cause a ConcurrentModificationException.
	     */ 
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
	    if ( pc.implies(permission)) return true;
	}
        // Once we get to here pc is definitely not null and we have the
        // copy referenced in the cache.
        if (loggable){
            logger.log(Level.FINEST, domain + permission.toString() + 
                    ": Base policy is not dynamic and returned false" );
        }
        // If the base policy doesn't imply a Permission then we should check for dynamic grants
        Collection<Permission> dynamicallyGrantedPermissions = new HashSet<Permission>(pGrants.length);
	PermissionGrant[] grantsRefCopy = pGrants; // In case the grants volatile reference is updated.
	int l = grantsRefCopy.length;
	for ( int i = 0; i < l; i++){
	    if (grantsRefCopy[i].implies(domain)) {
		// We only trust grantCache in case of mutable PermissionGrant.
		Permission[] perms = grantCache.get(grantsRefCopy[i]);
		dynamicallyGrantedPermissions.addAll(Arrays.asList(perms));
	    }
	}
        if (loggable) {
            logger.log(Level.FINEST, "Grants: " + dynamicallyGrantedPermissions.toString());
        }
        if (dynamicallyGrantedPermissions.isEmpty()) {
            // We have no dynamic grants
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
	// But UmbrellaGrant's are to enable easy dynamic GrantPermission's?
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
	synchronized (grantLock) {
	    // This lock doesn't stop reads to grants only other volatile reference updates.
	    // Manipulating, alterations (writes) to the pGrants array is prohibited.
	    int l = pGrants.length;
	    ArrayList<PermissionGrant> grantHolder 
		    = new ArrayList<PermissionGrant>(l);
	    for ( int i = 0; i < l; i++ ){
		if ( pGrants[i].isVoid()) continue;
		grantHolder.add(pGrants[i]);
	    }
	    PermissionGrant[] remaining = new PermissionGrant[grantHolder.size()];
	    pGrants = grantHolder.toArray(remaining); // Volatile reference update.
	}
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
	    //principals = principals.clone(); // Don't bother the builder will do it.
	    checkNullElements(principals);
	} 
        //permissions = permissions.clone(); // Don't bother the builder will do it.
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
	AccessController.checkPermission(new GrantPermission(permissions));
        PermissionGrantBuilder pgb = new PermissionGrantBuilderImp();
        PermissionGrant pe = pgb.clazz(cl).principals(principals)
                .permissions(permissions)
                .context(PermissionGrantBuilder.CLASSLOADER)
                .build();
	// We built this grant it's safe to trust.
	grantCache.put(pe, permissions); // Replace any existing too.
	// This grant is new, in the grantCache and we trust it.
	List<PermissionGrant> l = new ArrayList<PermissionGrant>(1);
	l.add(pe);
	processGrants(l);
	if (loggable){
	    logger.log(Level.FINEST, "Granting: " + pe.toString());
	}
    }
    
    // documentation inherited from DynamicPolicy.getGrants
    public Permission[] getGrants(Class cl, Principal[] principals) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
	if (basePolicyIsDynamic){
	    return ((DynamicPolicy)basePolicy).getGrants(cl, principals);
	}
        ClassLoader loader = null;
        if( cl != null ) {
            loader = cl.getClassLoader();
        }
        // defensive copy array
        if (principals != null && principals.length > 0) {
	    principals = principals.clone();
	    checkNullElements(principals);
	}
        Collection<Permission> cperms = new HashSet<Permission>(pGrants.length);
	PermissionGrant [] grantsRefCopy = pGrants; // Interim updates not seen.
	int l = grantsRefCopy.length;
	for ( int i = 0; i < l; i++ ){
	    if ( grantsRefCopy[i].implies(loader, principals) ){
		// Only use the trusted grantCache.
		Permission[] perm = grantCache.get(grantsRefCopy[i]);
		cperms.addAll(Arrays.asList(perm));
	    }
	}
	
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
        if ( basePolicyIsDynamic && revokeable){
            RevokeableDynamicPolicy bp = (RevokeableDynamicPolicy) basePolicy;
            bp.grant(grants);
            return;
        }
	grantCache.putAll(checkGrants(grants));
        // If we get to here, the caller has permission.
	processGrants(grants);
    }
    
    /**
     * This method checks that the PermissionGrant's are authorised to be
     * granted by it's caller, if it Fails, it will throw a SecurityException
     * or AccessControlException, if it succeeds it will return a Map containing
     * the PermissionGrant's mapped to their corresponding arrays of 
     * checked Permission's.
     * 
     * The PermissionGrant should not be requested for it's Permission's 
     * again, since doing so would risk an escallation of privelege attack if the
     * PermissionGrant implementation was mutable.
     * 
     * @param grants
     * @return map of checked grants.
     */
    private Map<PermissionGrant, Permission[]> 
	    checkGrants(Collection<PermissionGrant> grants){
	Map<PermissionGrant, Permission[]> allowed =
		new HashMap<PermissionGrant, Permission[]>(grants.size());
        Iterator<PermissionGrant> grantsItr = grants.iterator();
        while (grantsItr.hasNext()){
            PermissionGrant grant = grantsItr.next();
            Permission[] perms = grant.getPermissions().clone();
	    checkNullElements(perms);
            AccessController.checkPermission(new GrantPermission(perms));
	    allowed.put(grant, perms);
        }
	return allowed;
    }
    
    /**
     * Any grants must first be checked for PermissionGrants, checkGrants has
     * been provided for this purpose, then prior to calling this method,
     * the PermissionGrant's must be added to the grantsCache.
     * 
     * processGrants places the PermissionGrant's in the pGrants array. It is
     * recommended that only this method be used to update the pGrants
     * reference.
     * 
     * @param grants
     */
    private void processGrants(Collection<PermissionGrant> grants) {
	// This is slightly naughty calling a pGrants method, however if it
	// changes between now and gaining the lock, only the length of the
	// HashSet is potentially not optimal, keeping the HashSet creation
	// outside of the lock reduces the lock held duration.
	HashSet<PermissionGrant> holder 
		    = new HashSet<PermissionGrant>(grants.size() + pGrants.length);
	    holder.addAll(grants);
	synchronized (grantLock) {	    
	    int l = pGrants.length;
	    for ( int i = 0; i < l; i++ ){
		if (pGrants[i].isVoid()) continue;
		holder.add(pGrants[i]);
	    }
	    PermissionGrant[] updated = new PermissionGrant[holder.size()];
	    pGrants = holder.toArray(updated);
	}
    }

    public void revoke(List<PermissionGrant> grants) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        if (basePolicyIsDynamic && revokeable){
            RevokeableDynamicPolicy bp = (RevokeableDynamicPolicy) basePolicy;
            bp.revoke(grants);
            return;
        }
        AccessController.checkPermission(new RevokePermission());
	HashSet<PermissionGrant> holder = new HashSet<PermissionGrant>(pGrants.length);
	synchronized (grantLock){
	    int l = pGrants.length;
	    for (int i = 0; i < l; i++){
		if (pGrants[i].isVoid() || grants.contains(pGrants[i])) {
		    // should we consider removing from grantCache?
		    // For now we just let GC clean it up.
		    continue;
		}
		holder.add(pGrants[i]);
	    }
	    PermissionGrant[] updated = new PermissionGrant[holder.size()];
	    pGrants = holder.toArray(updated);
	}	
    }

    public List<PermissionGrant> getPermissionGrants() {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        if ( basePolicyIsDynamic && revokeable){
            RevokeableDynamicPolicy bp = (RevokeableDynamicPolicy) basePolicy;
            return bp.getPermissionGrants();
        }
        ArrayList<PermissionGrant> grants;
	PermissionGrant[] grantRefCopy = pGrants; // A local reference copy.
	int l = grantRefCopy.length;
	grants = new ArrayList<PermissionGrant>(l);
	grants.addAll(Arrays.asList(grantRefCopy));
	return grants;
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
