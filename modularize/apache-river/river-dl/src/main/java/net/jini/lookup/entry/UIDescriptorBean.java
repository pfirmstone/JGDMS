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
import java.rmi.MarshalledObject;
import java.util.Set;
import net.jini.core.entry.Entry;

/**
 * A JavaBeans(TM) component that encapsulates a <code>UIDescriptor</code> object.
 *
 * @see UIDescriptor
 */
public class UIDescriptorBean implements EntryBean, Serializable {

    private static final long serialVersionUID = -3185268510626360709L;
    
    /**
     * @serial The <code>UIDescriptor</code> object associated with this JavaBeans component.
     */
    protected UIDescriptor assoc;

    /**
     * Construct a new JavaBeans component, linked to a new empty <code>UIDescriptor</code> object.
     */
    public UIDescriptorBean() {
        assoc = new UIDescriptor();
    }
    
    @ConstructorProperties({"role", "toolkit", "attributes", "factory"})
    public UIDescriptorBean(String role, String toolkit, 
	    Set attributes, MarshalledObject factory)
    {
	assoc = new UIDescriptor(role, toolkit, attributes, factory);
    }

    /**
     * Make a link to a <code>UIDescriptor</code> object.
     *
     * @param e the <code>Entry</code> object, which must be a <code>UIDescriptor</code>, to which to link
     * @exception java.lang.ClassCastException the <code>Entry</code> is not 
     *     a <code>UIDescriptor</code>, the correct type for this JavaBeans component
     */
    public void makeLink(Entry e) {
        assoc = (UIDescriptor) e;
    }

    /**
     * Return the <code>UIDescriptor</code> linked to by this JavaBeans component.
     */
    public Entry followLink() {
        return assoc;
    }

    /**
     * Return the value of the <code>role</code> field in the object linked to by
     * this JavaBeans component.
     *
     * @return a <code>String</code> representing the role value
     * @see #setRole
     */
    public String getRole() {
        return assoc.role;
    }

    /**
     * Set the value of the <code>role</code> field in the object linked to by this
     * JavaBeans component.
     *
     * @param role  a <code>String</code> specifying the role value
     * @see #getRole
     */
    public void setRole(String role) {
        assoc.role = role;
    }

    /**
     * Return the value of the <code>toolkit</code> field in the object linked to by
     * this JavaBeans component.
     *
     * @return a <code>String</code> representing the toolkit value
     * @see #setToolkit
     */
    public String getToolkit() {
        return assoc.toolkit;
    }

    /**
     * Set the value of the <code>toolkit</code> field in the object linked to by this
     * JavaBeans component.
     *
     * @param toolkit  a <code>String</code> specifying the toolkit value
     * @see #getToolkit
     */
    public void setToolkit(String toolkit) {
        assoc.toolkit = toolkit;
    }

    /**
     * Return the value of the <code>attributes</code> field in the object linked to by
     * this JavaBeans component.
     *
     * @return a <code>Set</code> representing the attributes value
     * @see #setAttributes
     */
    public Set getAttributes() {
        return assoc.attributes;
    }

    /**
     * Set the value of the <code>attributes</code> field in the object linked to by this
     * JavaBeans component.
     *
     * @param attributes  a <code>Set</code> specifying the attributes value
     * @see #getAttributes
     */
    public void setAttributes(Set attributes) {
        assoc.attributes = attributes;
    }

    /**
     * Return the value of the <code>factory</code> field in the object linked to by
     * this JavaBeans component.
     *
     * @return a <code>MarshalledObject</code> representing the factory value
     * @see #setFactory
     */
    public MarshalledObject getFactory() {
        return assoc.factory;
    }

    /**
     * Set the value of the <code>factory</code> field in the object linked to by this
     * JavaBeans component.
     *
     * @param factory  a <code>MarshalledObject</code> specifying the factory value
     * @see #getFactory
     */
    public void setFactory(MarshalledObject factory) {
        assoc.factory = factory;
    }
}
