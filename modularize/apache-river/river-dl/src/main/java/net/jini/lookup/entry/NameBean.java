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
package net.jini.lookup.entry;

import java.beans.ConstructorProperties;
import java.io.Serializable;
import net.jini.core.entry.Entry;

/**
 * A JavaBeans(TM) component that encapsulates a Name object.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see Name
 */
public class NameBean implements EntryBean, Serializable {
    private static final long serialVersionUID = -6026791845102735793L;
    
    /**
     * The Name object associated with this JavaBeans component.
     *
     * @serial
     */
    protected Name assoc;

    /**
     * Construct a new JavaBeans component, linked to a new empty Name object.
     */
    public NameBean() {
	assoc = new Name();
    }
    
    @ConstructorProperties({"name"})
    public NameBean(String name){
	assoc = new Name(name);
    }

    /**
     * Make a link to an Entry object.
     *
     * @param e the Entry object to link to
     * @exception java.lang.ClassCastException the Entry is not of the
     * correct type for this JavaBeans component
     */
    public void makeLink(Entry e) {
	assoc = (Name) e;
    }

    /**
     * Return the Name linked to by this JavaBeans component.
     */
    public Entry followLink() {
	return assoc;
    }

    /**
     * Return the value of the name field in the object linked to by
     * this JavaBeans component.
     *
     * @return a String representing the name value
     * @see #setName
     */
    public String getName() {
	return assoc.name;
    }

    /**
     * Set the value of the name field in the object linked to by this
     * JavaBeans component.
     *
     * @param x  a String specifying the name value
     * @see #getName
     */
    public void setName(String x) {
	assoc.name = x;
    }
}
