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

package com.sun.jini.phoenix;

import java.rmi.activation.ActivationSystem;
import java.rmi.server.ObjID;

/**
 * JRMP exporter to export an <code>Activator</code> using the well-known
 * activator object identifier. This exporter implementation is only designed
 * to work with Java(TM) 2 Standard Edition implementations from Sun
 * Microsystems, Inc.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public class ActivatorSunJrmpExporter extends SunJrmpExporter {
    /**
     * Creates a JRMP exporter that exports on the standard port (1098).
     */
    public ActivatorSunJrmpExporter() {
	super(ObjID.ACTIVATOR_ID, ActivationSystem.SYSTEM_PORT);
    }

    /**
     * Creates a JRMP exporter that exports on the specified port.
     *
     * @param port the port (if zero, an anonymous port will be chosen)
     */
    public ActivatorSunJrmpExporter(int port) {
	super(ObjID.ACTIVATOR_ID, port);
    }
}
