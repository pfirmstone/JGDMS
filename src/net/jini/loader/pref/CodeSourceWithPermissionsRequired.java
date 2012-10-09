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

package net.jini.loader.pref;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.security.CodeSource;
import java.security.Permission;
import java.security.cert.Certificate;

/**
 * The intent of this class is to allow a jar file to carry with it the
 * permissions it requires, the intent is that a smart proxy use this, 
 * the permissions are dynamically granted to a Principal and ClassLoader
 * combination.
 * 
 * This is package private until the implementation is complete, the public
 * api should be reviewed before making public.
 * 
 * 
 */
class CodeSourceWithPermissionsRequired extends CodeSource {
    private static final long serialVersionUID = 1L;
    private Permission[] permissions;
    
    public CodeSourceWithPermissionsRequired(URL codebase, Certificate[] certs, Permission[] perms){
        super(codebase, certs );
        permissions = perms.clone();
    }
    
    public String toString(){
        StringBuilder sb = new StringBuilder(120);
        sb.append(super.toString())
                .append("\n")
                .append("Information only, minimum Permissions required for execution:\n");
        int l = permissions.length;
        for (int i = 0; i < l ; i++){
            sb.append(permissions[i].toString());
        }
        return sb.toString();
    }
    
    Permission [] required(){
        return permissions.clone();
    }
    
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException{
        in.defaultReadObject();
        // defensive copy of array reference to prevent stolen reference
        permissions = permissions.clone();
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException{
        out.defaultWriteObject();
    }
}
