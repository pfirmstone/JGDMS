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

import java.beans.Beans;
import java.io.IOException;
import net.jini.core.entry.Entry;

/**
 * Utility class for handling JavaBeans(TM) components that relate to Entry 
 * classes in the Jini lookup service.
 * 
 * @author Sun Microsystems, Inc.
 */
public class EntryBeans {
    /**
     * Create a bean of the appropriate type for the given Entry
     * object, and link it to the object for immediate manipulation.
     *
     * @param ent the Entry for which to create and link a bean
     * @return a new bean of the right type, linked to the given Entry
     * @exception ClassNotFoundException no bean class of the
     * appropriate type could be found
     * @exception ClassCastException the bean class does not implement
     * the EntryBean interface
     * @exception IOException the JavaBeans component could not be instantiated
     */
    public static EntryBean createBean(Entry ent)
	throws ClassNotFoundException, IOException
    {
	String beanClass = ent.getClass().getName() + "Bean";
	Object obj = null;
	try {
	    obj = Beans.instantiate(ent.getClass().getClassLoader(),
				    beanClass);
	} catch (ClassNotFoundException e) {
	    // Ignore any ClassNotFoundException thrown here.
	} // Allow the instantiate method to throw an IOException.	
	if (obj == null)
	    obj = Beans.instantiate(null, beanClass);
	EntryBean entBean = (EntryBean)Beans.getInstanceOf(obj,
							   EntryBean.class);
	entBean.makeLink(ent);
	return entBean;
    }

    /**
     * Return the class of JavaBeans component that corresponds to a particular 
     * Entry class.  The class passed in as argument must implement the
     * Entry interface.
     *
     * @param c the class for which a JavaBeans component should be found
     * @return corresponding JavaBeans component class
     * @exception ClassNotFoundException no such class could be found
     * @exception ClassCastException the class does not implement
     * the EntryBean interface
     * @see EntryBean
     */
    public static Class getBeanClass(Class c) throws ClassNotFoundException {
	if (!Entry.class.isAssignableFrom(c))
	    throw new IllegalArgumentException("class does not implement net.jini.core.entry.Entry");
	String beanClassName = c.getName() + "Bean";
	Class beanClass = null;
	try {
	    beanClass = c.getClassLoader().loadClass(beanClassName);
	} catch (ClassNotFoundException e) {
	    // ignore
	}
	if (beanClass == null)
	    beanClass = Class.forName(beanClassName);
	if (!EntryBean.class.isAssignableFrom(beanClass))
	    throw new ClassCastException("JavaBeans component class does not implement EntryBean interface");
	return beanClass;
    }
}
