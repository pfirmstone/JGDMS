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

package org.apache.river.api.security;

import java.net.URL;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.apache.river.imp.security.policy.util.PolicyUtils;

/**
 * Immutable List of codebase URL's and CodeSource's to deny in a PermissionGrant. 
 * Supplied to a PermissionGrantBuilder.
 * 
 * Applies only to Certificate[] and ClassLoader grant's, serves no use for CodeSource
 * or PermissionDomain grants.
 * 
 * This is not a global list, however since it is immutable, it can be shared
 * among PermissionGrant's to save memory. 
 * 
 * The user is responsible for deciding how these apply to each PermissionGrant.
 * 
 * Usually this will be used in combination with Certificate[] grants, to
 * circumvent security bugs identified in known jar files by trusted developers.
 * 
 * Deny does not allow any permission, only Deny.
 * 
 * This class may be extended. The caller must have a DenyPermission before it
 * can be added to a PermissionGrantBuilder.
 *
 * @author Peter Firmstone
 */
public class Deny {
    private final List<URL> uri;
    private final List<CodeSource> code;
    
    public Deny(List<URL> denyURL, List<CodeSource> denyCode){
        List<URL> denied = new ArrayList<URL>(denyURL.size());
        Iterator<URL> itU = denyURL.iterator();
        while (itU.hasNext()){
            denied.add(PolicyUtils.normalizeURL(itU.next()));
        }
        uri = Collections.unmodifiableList(denied);
        List<CodeSource> c = new ArrayList<CodeSource>(denyCode.size());
        Iterator<CodeSource> itC = c.iterator();
        while (itC.hasNext()){
            c.add(normalizeCodeSource(itC.next()));
        }
        code = Collections.unmodifiableList(c);
    }
    
    public List<URL> deniedURLs(){
        return uri;
    }
    
    public List<CodeSource> deniedCode() {
        return code;
    }
    
    public boolean allow(ProtectionDomain pd){
        CodeSource cs = pd.getCodeSource();
        cs = normalizeCodeSource(cs);
        if (code.contains(cs)) return false;
        URL codeSourceURL = cs.getLocation();
        if (uri.contains(codeSourceURL)) return false;
        return true;
    }
    
    private CodeSource normalizeCodeSource(CodeSource codeSource) {
        if (codeSource == null ) return null;
        URL codeSourceURL = PolicyUtils.normalizeURL(codeSource.getLocation());
        CodeSource result = codeSource;

        if (codeSourceURL != codeSource.getLocation()) {
            // URL was normalized - recreate codeSource with new URL
            CodeSigner[] signers = codeSource.getCodeSigners();
            if (signers == null) {
                result = new CodeSource(codeSourceURL, codeSource
                        .getCertificates());
            } else {
                result = new CodeSource(codeSourceURL, signers);
            }
        }
        return result;
    } 
}
