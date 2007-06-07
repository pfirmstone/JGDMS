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

import net.jini.entry.AbstractEntry;

/**
 * The location of the physical component of a service.  This is
 * distinct from the Address class in that it can be used alone in a
 * small, local organization.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see Address
 * @see LocationBean
 */
public class Location extends AbstractEntry {
    private static final long serialVersionUID = -3275276677967431315L;

    /**
     * Construct an empty instance of this class.
     */
    public Location() {
    }

    /**
     * Construct an instance of this class, with all fields
     * initialized appropriately.
     *
     * @param floor     a <code>String</code> representing the floor
     * @param room      a <code>String</code> representing the room
     * @param building  a <code>String</code> representing the building
     */
    public Location(String floor, String room, String building) {
	this.floor = floor;
	this.room = room;
	this.building = building;
    }

    /**
     * A floor designation.  For example, "2".
     *
     * @serial
     */
    public String floor;

    /**
     * A room or cube number.  For example, "B250".
     *
     * @serial
     */
    public String room;

    /**
     * A building name or code.  For example, "SUN04".
     *
     * @serial
     */
    public String building;
}
