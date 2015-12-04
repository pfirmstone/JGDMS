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
 * A JavaBeans(TM) component that encapsulates a Location object.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see Location
 * @see Address
 * @see AddressBean
 */
public class LocationBean implements EntryBean, Serializable {
    private static final long serialVersionUID = -4182591284470292829L;

    /**
     * The Location object associated with this JavaBeans component.
     *
     * @serial
     */
    protected Location assoc;

    /**
     * Construct a new JavaBeans component linked to a new empty 
     * Location object.
     */
    public LocationBean() {
	assoc = new Location();
    }

    /**
     * Make a link to an Entry object.
     *
     * @param e the Entry object to link to
     * @exception java.lang.ClassCastException the Entry is not of the
     * correct type for this JavaBeans component
     */
    public void makeLink(Entry e) {
	assoc = (Location) e;
    }

    /**
     * Return the Entry linked to by this JavaBeans component.
     */
    public Entry followLink() {
	return assoc;
    }

    /**
     * Return the value of the floor field in the Location object linked to by
     * this JavaBeans component.
     *
     * @return a String representing the floor value
     * @see #setFloor
     */
    public String getFloor() {
	return assoc.floor;
    }

    /**
     * Set the value of the floor field in the Location object linked to by
     * this JavaBeans component.
     *
     * @param x  a String specifying the floor value
     * @see #getFloor
     */
    public void setFloor(String x) {
	assoc.floor = x;
    }

    /**
     * Return the value of the room field in the Location object linked to by
     * this JavaBeans component.
     *
     * @return a String representing the room value
     * @see #setRoom
     */
    public String getRoom() {
	return assoc.room;
    }

    /**
     * Set the value of the room field in the Location object linked to by this
     * JavaBeans component.
     *
     * @param x  a String specifying the room value
     * @see #getRoom
     */
    public void setRoom(String x) {
	assoc.room = x;
    }

    /**
     * Return the value of the building field in the Location object linked to
     * by this JavaBeans component.
     *
     * @return a String representing the building value
     * @see #setBuilding
     */
    public String getBuilding() {
	return assoc.building;
    }

    /**
     * Set the value of the building field in the Location object linked to by
     * this JavaBeans component.
     *
     * @param x  a String specifying the building value
     * @see #getBuilding
     */
    public void setBuilding(String x) {
	assoc.building = x;
    }
}
