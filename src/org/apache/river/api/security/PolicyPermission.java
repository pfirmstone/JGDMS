/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.api.security;

import java.security.BasicPermission;

/**
 * <p>A "remote" or "REMOTE" PolicyPermission is allows updating a 
 * RemotePolicy </p>
 * 
 * @author Peter Firmstone
 */
public class PolicyPermission extends BasicPermission {
    private static final long serialVersionUID = 1L;
    
    public PolicyPermission(String name){
        super(name);
    }
    
}
