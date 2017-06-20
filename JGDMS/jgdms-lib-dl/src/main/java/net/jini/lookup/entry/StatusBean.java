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

import java.io.Serializable;
import net.jini.core.entry.Entry;

/**
 * A JavaBeans(TM) component that encapsulates a Status object.  Since the 
 * Status class is abstract, so too is this JavaBeans component.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see Status
 */
public abstract class StatusBean implements EntryBean, Serializable {
    private static final long serialVersionUID = -1975539395914887503L;

    /**
     * The Status object associated with this JavaBeans component.
     *
     * @serial
     */
    protected Status assoc;

    /**
     * Unlike other EntryBean constructors, this one does <i>not</i>
     * establish a link to a new object.
     */
    protected StatusBean() {
    }

    /**
     * Make a link to an Entry object.
     *
     * @param e the Entry object to link to
     * @exception java.lang.ClassCastException the Entry is not of the
     * correct type for this component
     */
    public void makeLink(Entry e) {
	assoc = (Status) e;
    }

    /**
     * Return the Entry linked to by this JavaBeans component.
     */
    public Entry followLink() {
	return assoc;
    }

    /**
     * Get the value of the severity field of the Status object to
     * which this JavaBeans component is linked.
     * @return a StatusType object representing the severity value 
     * @see #setSeverity 
     */
    public StatusType getSeverity() {
	return assoc.severity;
    }

    /**
     * Set the value of the severity field of the Status object to
     * which this JavaBeans component is linked.
     *
     * @param x the new value to put in place
     * @see #getSeverity 
     */
    public void setSeverity(StatusType x) {
	assoc.severity = x;
    }
}
