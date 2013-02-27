/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.river.api.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 *
 * @author 
 */
public class DistributedObjectInputStream extends ObjectInputStream {
    
    public static ObjectInputStream create(InputStream in) throws IOException{
        DistributedObjectInputStream result = new DistributedObjectInputStream(in);
        AccessController.doPrivileged(new EnableResolveObject(result));
        return result;
    }
    
    /**
     * Caller must have SerializablePermission("enableSubstitution") to call
     * this method.
     * 
     * @param in
     * @throws IOException 
     */
    protected DistributedObjectInputStream(InputStream in) throws IOException{
        super(in);
        try {
            super.enableResolveObject(true);
        } catch (SecurityException e){
            // Ignore, will be called from privileged context if create method used.
        }
    }
    
    private void enableResolveObject(){
        super.enableResolveObject(true);
    }
    
    protected final Object resolveObject(Object o) throws IOException{
        if (o instanceof SerialFactory) return ((SerialFactory)o).create();
        return o;
    }
    
    private static class EnableResolveObject implements PrivilegedAction{
        private final DistributedObjectInputStream in;
        
        EnableResolveObject(DistributedObjectInputStream in){
            this.in = in;
        }
        
        @Override
        public Object run() {
            in.enableResolveObject();
            return null;
        }
        
    }
}
