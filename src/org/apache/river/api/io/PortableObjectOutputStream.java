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
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * PortableObjectOutputStream replaces @ref{Portable} instances
 * in the OutputStream with a PortableFactory that recreates the 
 * Portable Object during unmarshalling.
 * 
 * @author peter
 * @since 3.0.0
 */
public class PortableObjectOutputStream extends ObjectOutputStream {
    
     public static ObjectOutputStream create(OutputStream out) throws IOException{
        PortableObjectOutputStream result = new PortableObjectOutputStream(out);
        AccessController.doPrivileged(new EnableReplaceObject(result));
        return result;
    }
    
    protected PortableObjectOutputStream (OutputStream out) throws IOException{
        super(out);
    }
    
     @Override
    protected Object replaceObject(Object o){
        if (o instanceof Portable) return ((Portable)o).factory();
        return o;
    }
    
    private void enableReplaceObject(){
        super.enableReplaceObject(true);
    }
    
    private static class EnableReplaceObject implements PrivilegedAction{
        private final PortableObjectOutputStream out;
        
        EnableReplaceObject(PortableObjectOutputStream out){
            this.out = out;
        }
        
        @Override
        public Object run() {
            out.enableReplaceObject();
            return null;
        }
        
    }
}
