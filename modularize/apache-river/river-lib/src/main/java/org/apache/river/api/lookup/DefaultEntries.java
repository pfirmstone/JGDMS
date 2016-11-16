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

package org.apache.river.api.lookup;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import net.jini.lookup.entry.Address;
import net.jini.lookup.entry.Comment;
import net.jini.lookup.entry.Location;
import net.jini.lookup.entry.Name;
import net.jini.lookup.entry.ServiceInfo;
import net.jini.lookup.entry.Status;
import net.jini.lookup.entry.UIDescriptor;

/**
 * A little builder utility class that creates an array of Entry classes to
 * be used as a parameter for StreamServiceRegistrar.  All the jini platform
 * Entry's are included by default.
 * 
 * Note: This class is not threadsafe, use external synchronization if required.
 * 
 * Suggested by Dan Creswell.
 * @author Peter Firmstone.
 * @since 3.0.0
 */
public class DefaultEntries {
    private final Set<Class> entrys;
    
    public DefaultEntries() {
        entrys = new HashSet<Class>(16);
    }
    /**
     * Add an Entry class.
     * @param cl - class
     * @return this
     */
    public DefaultEntries add(Class cl){
        entrys.add(cl);
        return this;
    }
    /**
     * All all the Jini Platform Entry's
     * @return DefaultEntries
     */
    public DefaultEntries addPlatformEntries(){
        add(Comment.class);
        add(Location.class);
        add(Name.class);
        add(ServiceInfo.class);
        add(Status.class);
        add(UIDescriptor.class);
        add(Address.class);
        return this;
    }
    /**
     * Remove all Entry's
     */
    public void reset(){
        entrys.clear();
    }
    
    /**
     * Generate a new array containing all Entry's added since last reset.
     * @return Class[] an array of Entry classes.
     */
    public Class[] getEntries(){
        return entrys.toArray(new Class[entrys.size()]);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 29 * hash + (this.entrys != null ? this.entrys.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object o){
        if (o == null) return false;
        if (o instanceof DefaultEntries){
            if (entrys.equals(((DefaultEntries)o).entrys)) return true;
        }
        return false;
    }
    
    @Override
    public String toString(){
        String newline = System.getProperty("line.separator");
        StringBuilder sb = new StringBuilder(256);
        sb.append("DefaultEntries:");
        sb.append(newline);
        Iterator<Class> it = entrys.iterator();
        while (it.hasNext()){
            sb.append(it.next().getName());
            sb.append(newline);
        }
        return sb.toString();
    }
}
