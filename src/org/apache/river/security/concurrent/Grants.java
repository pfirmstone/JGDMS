package org.apache.river.security.concurrent;

import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.river.security.concurrent.ConcurrentDynamicPolicyProvider.DomainPermissions;

class Grants {

    private final Map principalGrants = new HashMap();
    private final WeakGroup scope;
    private final ReadWriteLock rwlock = new ReentrantReadWriteLock();
    private final Lock rlock = rwlock.readLock();
    private final Lock wlock = rwlock.writeLock();

    @SuppressWarnings(value = "unchecked")
    Grants() {
        super();
        PrincipalGrants pg = new PrincipalGrants();
        principalGrants.put(Collections.EMPTY_SET, pg);
        scope = pg.scope;
    }

    @SuppressWarnings(value = "unchecked")
    void add(Principal[] pra, Permission[] pa) {
        Set prs = (pra != null && pra.length > 0) ? new HashSet(Arrays.asList(pra)) : Collections.EMPTY_SET;
        ArrayList l = new ArrayList();
        wlock.lock();
        try {
            PrincipalGrants pg = (PrincipalGrants) principalGrants.get(prs);
            if (pg == null) {
                pg = new PrincipalGrants();
                for (Iterator i = scope.iterator(); i.hasNext();) {
                    DomainPermissions dp = (DomainPermissions) i.next();
                    if (containsAll(dp.getPrincipals(), prs)) {
                        pg.scope.add(dp);
                    }
                }
                principalGrants.put(prs, pg);
            }
            for (int i = 0; i < pa.length; i++) {
                Permission p = pa[i];
                if (!pg.perms.implies(p)) {
                    pg.perms.add(p);
                    l.add(p);
                }
            }
            if (l.size() > 0) {
                pa = (Permission[]) l.toArray(new Permission[l.size()]);
                for (Iterator i = pg.scope.iterator(); i.hasNext();) {
                    ((DomainPermissions) i.next()).add(pa);
                }
            }
        } finally {
            wlock.unlock();
        }
    }

    @SuppressWarnings(value = "unchecked")
    Permission[] get(Principal[] pra) {
        Set prs = (pra != null && pra.length > 0) ? new HashSet(Arrays.asList(pra)) : Collections.EMPTY_SET;
        List l = new ArrayList();
        rlock.lock();
        try {
            for (Iterator i = principalGrants.entrySet().iterator(); i.hasNext();) {
                Map.Entry me = (Map.Entry) i.next();
                if (containsAll(prs, (Set) me.getKey())) {
                    PrincipalGrants pg = (PrincipalGrants) me.getValue();
                    l.addAll(Collections.list(pg.perms.elements()));
                }
            }
        } finally {
            rlock.unlock();
        }
        return (Permission[]) l.toArray(new Permission[l.size()]);
    }

    @SuppressWarnings(value = "unchecked")
    void register(DomainPermissions dp) {
        Set prs = dp.getPrincipals();
        wlock.lock();
        try {
            for (Iterator i = principalGrants.entrySet().iterator(); i.hasNext();) {
                Map.Entry me = (Map.Entry) i.next();
                if (containsAll(prs, (Set) me.getKey())) {
                    PrincipalGrants pg = (PrincipalGrants) me.getValue();
                    pg.scope.add(dp);
                    List l = Collections.list(pg.perms.elements());
                    dp.add((Permission[]) l.toArray(new Permission[l.size()]));
                }
            }
        } finally {
            wlock.unlock();
        }
    }

    private static boolean containsAll(Set s1, Set s2) {
        return (s1.size() >= s2.size()) && s1.containsAll(s2);
    }

    private static class PrincipalGrants {

        final WeakGroup scope = new WeakGroup();
        final PermissionCollection perms = new Permissions();

        PrincipalGrants() {
            super();
        }
    }
}
