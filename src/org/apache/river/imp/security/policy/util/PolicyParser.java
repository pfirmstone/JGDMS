/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.imp.security.policy.util;

import java.net.URL;
import java.util.Collection;
import java.util.Properties;

/**
 *
 * @author peter
 */
public interface PolicyParser {

    /**
     * This is the main business method. It manages loading process as follows:
     * the associated scanner is used to parse the stream to a set of
     * {@link org.apache.harmony.security.DefaultPolicyScanner.GrantEntry composite tokens},
     * then this set is iterated and each token is translated to a PolicyEntry.
     * Semantically invalid tokens are ignored, the same as void PolicyEntries.
     * <br>
     * A policy file may refer to some KeyStore(s), and in this case the first
     * valid reference is initialized and used in processing tokens.
     *
     * @param location an URL of a policy file to be loaded
     * @param system system properties, used for property expansion
     * @return a collection of PolicyEntry objects, may be empty
     * @throws Exception IO error while reading location or file syntax error
     */
    Collection<PolicyEntry> parse(URL location, Properties system) throws Exception;

}
