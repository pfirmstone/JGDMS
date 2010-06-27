

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
    public PermissionGrantBuilder getPermissionGrantBuilder();
    public boolean revokeSupported();
}
