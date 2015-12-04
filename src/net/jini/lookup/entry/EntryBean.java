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

import net.jini.core.entry.Entry;

/**
 * Interface to be implemented by all JavaBeans(TM) components that act 
 * as "front ends" to Entry classes.  Such components must follow several
 * patterns:
 * <ul>
 *
 * <li>The component's name is derived from the name of the Entry class,
 * with "Bean" appended.  Thus, the JavaBeans component for the 
 * <tt>foo.Bar</tt> Entry class is <tt>foo.BarBean</tt>.
 *
 * <li>The component has a public no-arg constructor; this creates a new,
 * uninitialized, instance of the JavaBeans component's Entry class.
 *
 * <li>For each public object field <tt><i>foo</i></tt> in the
 * associated class, the JavaBeans component has both a <tt>set<i>Foo</i>
 * </tt> and a <tt>get<i>Foo</i></tt> method.  The former returns the 
 * value of that field in the linked Entry object, and the latter sets the
 * value of that field.
 *
 * </ul>
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.core.entry.Entry
 */
public interface EntryBean {
    /**
     * Make a link to an Entry object.
     *
     * @param e the Entry object to link to
     * @exception java.lang.ClassCastException the Entry is not of the
     * correct type for this JavaBeans component
     */
    void makeLink(Entry e);

    /**
     * Return the Entry linked to by this JavaBeans component.
     * @return the entry linked to by this JavaBeans component. 
     */
    Entry followLink();
}
