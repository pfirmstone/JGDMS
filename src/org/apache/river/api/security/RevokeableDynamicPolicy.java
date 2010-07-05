

package org.apache.river.api.security;

import java.util.List;

/**
 *
 * @author Peter Firmstone
 */
public interface RevokeableDynamicPolicy {
    public void add(List<PermissionGrant> grants);
    public void remove(List<PermissionGrant> grants);
    public List<PermissionGrant> getPermissionGrants();
    public void add(List<Denied> denials);
    public void remove(List<Denied> denials);
    public List<Denied> getDenied();    
    public boolean revokeSupported();
}
