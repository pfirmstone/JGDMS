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
package org.apache.river.security.concurrent;

import net.jini.security.policy.*;
import org.apache.river.util.concurrent.WeakIdentityMap;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Principal;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.security.GrantPermission;
import org.apache.river.security.policy.spi.RevokeableDynamicPolicySpi;

/**
 * Security policy provider that supports dynamic granting of permissions at
 * run-time.  This provider is designed as a wrapper to layer dynamic grant
 * functionality on top of an underlying policy provider.  If the underlying
 * provider does not implement the {@link DynamicPolicy} interface, then its
 * permission mappings are assumed to change only when its {@link
 * Policy#refresh refresh} method is called.  Permissions are granted on the
 * granularity of class loader; granting a permission requires (of the calling
 * context) {@link GrantPermission} for that permission.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
class ConcurrentDynamicPolicyProvider extends Policy implements RevokeableDynamicPolicySpi {

    private static final String basePolicyClassProperty =
            "net.jini.security.policy." +
            "DynamicPolicyProvider.basePolicyClass";
    private static final String defaultBasePolicyClass =
            "org.apache.river.security.concurrent.ConcurrentPolicyFile";
    @SuppressWarnings("unchecked")
    private static final ProtectionDomain sysDomain = (ProtectionDomain) AccessController.doPrivileged(new PrivilegedAction() {

        public Object run() {
            return Object.class.getProtectionDomain();
        }
    });
    private volatile Policy basePolicy;
    private volatile boolean cacheBasePerms;
    private volatile boolean initialized;
    // REMIND: do something with WeakIdentityMap and Concurrency, note
    // this means different implementation methods not a drop in.
    private final ConcurrentMap<ProtectionDomain, DomainPermissions> domainPerms;
    private final ConcurrentMap<ClassLoader, Grants> loaderGrants;
    private final Grants globalGrants;
    private static final Logger logger = Logger.getLogger(
            "net.jini.security.policy.DynamicPolicyProviderImpl");

    /**
     * A new uninitialized instance.
     */
    ConcurrentDynamicPolicyProvider() {
        domainPerms = new WeakIdentityMap<ProtectionDomain, DomainPermissions>();
        loaderGrants = new WeakIdentityMap<ClassLoader, Grants>();
        globalGrants = new Grants();
        basePolicy = null;
        cacheBasePerms = false;
        initialized = false;
    }

    /**
     * This method is only called once, on an uninitialized instance
     * further attempts will fail.
     * @param basePolicy
     * @return success
     */
    public boolean basePolicy(Policy basePolicy) {
        if (this.basePolicy == null) {
            this.basePolicy = basePolicy;
            return true;
        }
        return false;
    }

    /**
     * This method completes construction of the Implementation, considered
     * safe since it is called through the Service and cannot be accessed
     * otherwise.
     * @throws net.jini.security.policy.PolicyInitializationException
     * @throws java.lang.InstantiationException
     */
    public void initialize() throws PolicyInitializationException {
        if (initialized == true) {
            return;
        }
        if (basePolicy == null) {
            String cname = Security.getProperty(basePolicyClassProperty);
            if (cname == null) {
                cname = defaultBasePolicyClass;
            }
            try {
                basePolicy = (Policy) Class.forName(cname).newInstance();
            } catch (InstantiationException ex) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, null, ex);
                }
                throw new PolicyInitializationException(
                        "Unable to create a new instance of: " +
                        cname, ex);
            } catch (IllegalAccessException ex) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.logp(Level.SEVERE, "DynamicPolicyProviderImpl",
                            "initialize()", "Unable to create a new instance of: " +
                            cname, ex);
                }
                throw new PolicyInitializationException(
                        "Unable to create a new instance of: " +
                        cname, ex);
            } catch (ClassNotFoundException ex) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "Check " + cname + " is accessable" +
                            "from your classpath", ex);
                }
                throw new PolicyInitializationException(
                        "Unable to create a new instance of: " +
                        cname, ex);
            } catch (SecurityException ex) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE,
                            "You don't have sufficient permissions to create" +
                            "a new instance of" + cname, ex);
                }
                throw new PolicyInitializationException(
                        "Unable to create a new instance of: " +
                        cname, ex);
            }
        }
        cacheBasePerms = !(basePolicy instanceof DynamicPolicy);
        initialized = true;
    }

    /**
     * Behaves as specified by {@link Policy#getPermissions(CodeSource)}.
     */
    @Override
    public PermissionCollection getPermissions(CodeSource source) {
        PermissionCollection pc = basePolicy.getPermissions(source);
        Permission[] pa = globalGrants.get(null);
        for (int i = 0; i < pa.length; i++) {
            Permission p = pa[i];
            if (!pc.implies(p)) {
                pc.add(p);
            }
        }
        return pc;
    }

    /**
     * Behaves as specified by {@link Policy#getPermissions(ProtectionDomain)}.
     */
    @Override
    public PermissionCollection getPermissions(ProtectionDomain domain) {
        return getDomainPermissions(domain).getPermissions(domain);
    }

    /**
     * Behaves as specified by {@link Policy#implies}.
     */
    public boolean implies(ProtectionDomain domain, Permission permission) {
        return getDomainPermissions(domain).implies(permission, domain);
    }

    /**
     * Behaves as specified by {@link Policy#refresh}.
     */
    public void refresh() {
        basePolicy.refresh();
        if (cacheBasePerms) {
            domainPerms.clear();
        }
    }

    // documentation inherited from DynamicPolicy.grantSupported
    public boolean grantSupported() {
        return true;
    }

    // documentation inherited from DynamicPolicy.grant
    public void grant(Class cl,
            Principal[] principals,
            Permission[] permissions) {
        if (cl != null) {
            checkDomain(cl);
        }
        if (principals != null && principals.length > 0) {
            principals = (Principal[]) principals.clone();
            checkNullElements(principals);
        }
        if (permissions == null || permissions.length == 0) {
            return;
        }
        permissions = (Permission[]) permissions.clone();
        checkNullElements(permissions);

        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new GrantPermission(permissions));
        }

        Grants g = (cl != null) ? getLoaderGrants(getClassLoader(cl)) : globalGrants;
        g.add(principals, permissions);
    }

    // documentation inherited from DynamicPolicy.getGrants
    public Permission[] getGrants(Class cl, Principal[] principals) {
        if (cl != null) {
            checkDomain(cl);
        }
        if (principals != null && principals.length > 0) {
            principals = (Principal[]) principals.clone();
            checkNullElements(principals);
        }

        List l = Arrays.asList(globalGrants.get(principals));
        if (cl != null) {
            l = new ArrayList(l);
            l.addAll(Arrays.asList(
                    getLoaderGrants(getClassLoader(cl)).get(principals)));
        }
        PermissionCollection pc = new Permissions();
        for (Iterator i = l.iterator(); i.hasNext();) {
            Permission p = (Permission) i.next();
            if (!pc.implies(p)) {
                pc.add(p);
            }
        }
        l = Collections.list(pc.elements());
        return (Permission[]) l.toArray(new Permission[l.size()]);
    }

    /**
     * Ensures that any classes depended on by this policy provider are
     * resolved.  This is to preclude lazy resolution of such classes during
     * operation of the provider, which can result in deadlock as described by
     * bug 4911907.
     */
    public void ensureDependenciesResolved() {
        // force class resolution by pre-invoking method called by implies()
        getDomainPermissions(sysDomain);
    }

    private DomainPermissions getDomainPermissions(ProtectionDomain pd) {
        DomainPermissions dp = domainPerms.get(pd);
        if (dp == null) {
            dp = new DomainPermissions(pd);
            DomainPermissions exists = null;
            if (pd != null) {
                // If a concurrent thread has created a new copy, we get that instead;
                exists = domainPerms.putIfAbsent(pd, dp);
                if (exists != null) {
                    dp = exists;
                }
                getLoaderGrants(pd.getClassLoader()).register(dp);
            }
        }

        synchronized (domainPerms) {
            dp = (DomainPermissions) domainPerms.get(pd);
        }
        if (dp == null) {
            dp = new DomainPermissions(pd);
            globalGrants.register(dp);
            if (pd != null) {
                getLoaderGrants(pd.getClassLoader()).register(dp);
            }
            synchronized (domainPerms) {
                domainPerms.put(pd, dp);
            }
        }
        return dp;
    }

    private Grants getLoaderGrants(ClassLoader ldr) {
        if (ldr == null) {
            throw new NullPointerException("ClassLoader cannot be null");
        }
        Grants g = loaderGrants.get(ldr);
        if (g == null) {
            g = new Grants();
            Grants exists = loaderGrants.putIfAbsent(ldr, g);
            if (exists != null) {
                g = exists;
            }
        }
        return g;
    }

    private static ClassLoader getClassLoader(final Class cl) {
        return (ClassLoader) AccessController.doPrivileged(
                new PrivilegedAction() {

                    public Object run() {
                        return cl.getClassLoader();
                    }
                });
    }

    private static void checkDomain(final Class cl) {
        ProtectionDomain pd = (ProtectionDomain) AccessController.doPrivileged(
                new PrivilegedAction() {

                    public Object run() {
                        return cl.getProtectionDomain();
                    }
                });
        if (pd != sysDomain && pd.getClassLoader() == null) {
            throw new UnsupportedOperationException(
                    "ungrantable protection domain");
        }
    }

    private static void checkNullElements(Object[] array) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == null) {
                throw new NullPointerException();
            }
        }
    }

    /**
     * Class which holds permissions and principals of a ProtectionDomain. The
     * domainPerms map associates ProtectionDomain instances to instances of
     * this class.
     * 
     * This isn't the best designed class, the Set escapes, it isn't immutable
     * or synchronized although it is backed by a fixed size array, so one 
     * could potentially change the Principals, it is only read however 
     * within this implementation and is private, so we'll leave it for now,
     * with this caution.
     */
    class DomainPermissions {

        private final Set principals;
        private final PermissionCollection perms;
        private final List grants = new ArrayList();

        @SuppressWarnings("unchecked")
        DomainPermissions( ProtectionDomain pd) {
            Principal[] pra;
            principals = (pd != null && (pra = pd.getPrincipals()).length > 0)
                    ? new HashSet(Arrays.asList(pra)) : Collections.EMPTY_SET;
            perms = cacheBasePerms ? basePolicy.getPermissions(pd) : null;
        }

        Set getPrincipals() {
            return principals;
        }

        @SuppressWarnings("unchecked")
        synchronized void add(Permission[] pa) {
            for (int i = 0; i < pa.length; i++) {
                Permission p = pa[i];
                grants.add(p);
                if (perms != null) {
                    perms.add(p);
                }
            }
        }

        synchronized PermissionCollection getPermissions(ProtectionDomain d) {
            return getPermissions(true, d);
        }

        synchronized boolean implies(Permission p, ProtectionDomain domain) {
//            System.out.println("Permission: " + p.toString() + 
//                    " ProtectionDomain: " + domain.toString());
            if (perms != null) {
                return perms.implies(p);
            }
            if (basePolicy.implies(domain, p)) {
                return true;
            }
            if (grants.isEmpty()) {
                return false;
            }
            return getPermissions(false, domain).implies(p);
        }

        private PermissionCollection getPermissions(boolean compact,
                ProtectionDomain domain) {
            // base policy permission collection may not be enumerable
            assert Thread.holdsLock(this);
            PermissionCollection pc = basePolicy.getPermissions(domain);
            for (Iterator i = grants.iterator(); i.hasNext();) {
                Permission p = (Permission) i.next();
                if (!(compact && pc.implies(p))) {
                    pc.add(p);
                }
            }
            return pc;
        }
    }

    public void revoke(Class cl, Principal[] principals, Permission[] permissions) {
        throw new UnsupportedOperationException("Revoke not supported.");
    }

    public boolean revokeSupported() {
        return false;
    }

    public Object parameters() throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
