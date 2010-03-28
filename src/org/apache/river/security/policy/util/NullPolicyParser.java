/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.security.policy.util;

import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;

/**
 * A null PolicyParser.
 * Just in case you don't want to utilise any policy files, for whatever reason.
 * @author Peter Firmstone.
 */
public class NullPolicyParser implements PolicyParser{

    public Collection<PolicyEntry> parse(URL location, Properties system) throws Exception {
        return new HashSet<PolicyEntry>();
    }

}
