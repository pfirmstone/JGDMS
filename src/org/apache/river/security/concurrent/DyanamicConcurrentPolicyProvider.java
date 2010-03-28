

package org.apache.river.security.concurrent;

import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.Provider;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import net.jini.security.policy.DynamicPolicy;
import net.jini.security.policy.PolicyInitializationException;
import org.apache.river.security.policy.spi.RevokeableDynamicPolicySpi;
import org.apache.river.security.policy.util.PolicyEntry;
import org.apache.river.security.policy.util.PolicyUtils;
import org.apache.river.util.concurrent.WeakIdentityMap;

/**
 * This is a Dynamic Policy Provider that supports concurrent access,
 * for instances where a Policy provider is used for a distributed network
 * of computers, or where there is a large number of ProtectionDomains and
 * hence the opportunity for concurrency exists, concurrency comes with a 
 * cost however, that of increased memory usage.
 * 
 * Due to the Java 2 Security system's static design, a Policy Provider
 * can only augment the policy files utilised, that is it can only relax security
 * by granting additional permissions, this implementation adds an experimental 
 * feature for revoking permissions, however there are some caveats:
 * 
 * Firstly if the Policy.refresh() method is called, followed by the 
 * ProtectionDomain.toString() method, the ProtectionDomain
 * merge the permissions, from the policy with those in the ProtectionDomain, 
 * a ProtectionDomain cannot have Permissions
 * removed, only additional merged. 
 * 
 * So in order to prevent dynamic grants from finding
 * their way into a ProtectionDomain's private PermissionCollection,
 * one would have to ensure that no dynamically grantable permissions are 
 * returned via the methods:
 * 
 * getPermissions(Codesource source) or
 * getPermissions(ProtectionDomain domain)
 * 
 * This is different to the behaviour of the existing Jini 2.0
 * DynamicPolicyProvider implementation where dynamically granted Permissions
 * are added.
 * 
 * However when a Policy is checked via implies(ProtectionDomain d, Permission p)
 * this implementation checks the dynamic grants.
 * 
 * This means that if a DynamicPolicy is utilised as the base Policy class
 * and if it returns dynamically granted permissions, then those permissions
 * cannot be revoked.
 * 
 * It is thus reccommeded that Static policy files only be used for files
 * where the level of trust is relatively static.  This is the only implementation
 * where a dyanamic grant can be removed.  In the case of Proxy trust, a proxy
 * is no longer trusted when it has lost contact with it's Principal (server)
 * because the server cannot be asked if it trusts it's proxy and the proxy
 * cannot be given a thread of control to find it's server because it has
 * already attained too many Permissions.  In this new implementation it should
 * be possible to revoke AllPermission and grant Permissions dynamically as 
 * trust is gained.
 * 
 * This may cause some undesireable side effects in existing programs.
 * 
 * There is one final reccommendation and that is adopting / utilising an OSGi
 * Framework to enable far greater control over dynamic Permissions than this hack
 * implementation provides.
 * 
 * To make the best utilisation of this Policy provider, set the System property:
 * 
 * net.jini.security.policy.PolicyFileProvider.basePolicyClass = 
 * org.apache.river.security.concurrent.ConcurrentPolicyFile
 * 
 * @author Peter Firmstone
 * @version 1
 * @since 2.2
 * @see ProtectionDomain
 * @see Policy
 * @see ConcurrentPolicyFile
 * @see net.jini.security.policy.PolicyFileProvider
 * @see ConcurrentPermissionCollection
 */

public class DyanamicConcurrentPolicyProvider implements RevokeableDynamicPolicySpi {
    
    // A set of PolicyEntries constituting this Policy.
    // PolicyEntry is lighter weight than PermissionCollection.
    private final ReentrantReadWriteLock rwl;
    private final ReadLock rl;
    private final WriteLock wl;    
    private final Set<PolicyEntry> dynamicGrants; // protected by rwl    
    private volatile Policy basePolicy; // effectively final looks after its own sync
    private final ConcurrentMap<ProtectionDomain, PermissionCollection> cache;
    private volatile boolean basePolicyIsDynamic;
    private boolean initialized = false;
    // do something about some domain permissions for this domain so we can 
    // avoid dead locks due to bug 4911907
    
    
    public DyanamicConcurrentPolicyProvider(){
        rwl = new ReentrantReadWriteLock();
        rl = rwl.readLock();
        wl = rwl.writeLock();
        dynamicGrants = new HashSet<PolicyEntry>();
        basePolicy = null;
        cache = new WeakIdentityMap<ProtectionDomain, PermissionCollection>();
        basePolicyIsDynamic = false;
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
     */ 
    public void initialize() throws PolicyInitializationException {
        if (basePolicy == null) throw new PolicyInitializationException("Base Policy hasn't " +
                "been set cannot initialize", new Exception("Failed to initialize"));
        if (basePolicy instanceof DynamicPolicy) basePolicyIsDynamic = true;
        initialized = true;
    }

    public void ensureDependenciesResolved() {
        if (initialized == false) throw new RuntimeException("Object not initialized");
    
    }

    public void revoke(Class cl, Principal[] principals, Permission[] permissions) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        /* The removal begins with removal from the dynamicGrants then the cache
         * while dynamicGrants is write locked, otherwise it could be added 
         * again due to a concurrent implies during the removal process.
         * Actually the decision has been made to release the lock as soon as
         * possible to prevent possible deadlocks, increase concurrency at the
         * risk of possible positive implies() the the mean time.
         */       
        ProtectionDomain domain = cl.getProtectionDomain();
        CodeSource codeSource = domain.getCodeSource();
        Collection<Permission> permToBeRemoved = Arrays.asList(permissions);
        Collection<Permission> remainingGrants = new HashSet<Permission>();
        try {
            wl.lock();
            Iterator<PolicyEntry> it = dynamicGrants.iterator();
            while (it.hasNext()) {
                PolicyEntry ge = it.next();
                if (ge.impliesPrincipals(domain == null ? null : principals)
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
        cache.remove(cl);
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
        return true;
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
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public PermissionCollection getPermissions(ProtectionDomain domain) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean implies(ProtectionDomain domain, Permission permission) {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        //First check the our cache
        if (basePolicyIsDynamic && cache.get(domain).implies(permission)) return true;
        // Then check the base policy
        if (basePolicy.implies(domain, permission)) return true;
        // If it doesn't then we should check if it has any grants
        Collection<Permission> dynamicallyGrantedPermissions = null;
        try {
            rl.lock();
            Iterator<PolicyEntry> it = dynamicGrants.iterator();
            while (it.hasNext()) {
                PolicyEntry ge = it.next();
                if (ge.impliesPrincipals(domain == null ? null : domain.getPrincipals())
                    && ge.impliesCodeSource(domain == null ? null : domain.getCodeSource())) {
                    dynamicallyGrantedPermissions = ge.getPermissions();
                }
            }               
        } finally { rl.unlock(); }
        if (dynamicallyGrantedPermissions == null) return false;
        if (dynamicallyGrantedPermissions.isEmpty()) return false;
        // Operation starts to get expensive
        PermissionCollection pc = null;
        if ( !(basePolicyIsDynamic) ) pc = cache.get(domain); // saves new object creation.
        if (pc == null){
            pc = basePolicy.getPermissions(domain);
            if (pc == null) pc = new ConcurrentPermissions();
            if (!(pc instanceof ConcurrentPermissions)) {
                pc = PolicyUtils.toConcurrentPermissions(pc);
            }           
            PermissionCollection existed = cache.putIfAbsent(domain, pc);
            if ( !(existed == null) ){ pc = existed;} //Another thread might have just done it!
        }        
        Iterator<Permission> dgpi = dynamicallyGrantedPermissions.iterator();
        while (dgpi.hasNext()){
            pc.add(dgpi.next());
        }
        // If we get refreshed the cache could be empty, which is more pedantic
        // however the result may still be true so we'll return it anyway.
        return pc.implies(permission);
    }

    public void refresh() {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        cache.clear();
        basePolicy.refresh();
        
    }

    public boolean grantSupported() {
        if (initialized == false) throw new RuntimeException("Object not initialized");
        return true;
    }

    public void grant(Class cl, Principal[] principals, Permission[] permissions) {
        CodeSource cs = cl.getProtectionDomain().getCodeSource();
        Collection<Principal> pal = Arrays.asList(principals);
        Collection<Permission> perm = Arrays.asList(permissions);
        PolicyEntry pe = new PolicyEntry(cs, pal, perm);
        try {
            wl.lock();
            dynamicGrants.add(pe);           
        } finally {wl.unlock();}
    }

    public Permission[] getGrants(Class cl, Principal[] principals) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
