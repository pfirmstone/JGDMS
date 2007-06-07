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

import net.jini.id.Uuid;
import net.jini.id.UuidFactory;

/**
 * Provides constants for Phoenix.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public final class PhoenixConstants {

    /**
     * An object identifier for exporting the activator.
     */
    public static final Uuid ACTIVATOR_UUID =
    	UuidFactory.create("d710347c-273c-11b2-bee3-080020c9e4a1");

    /**
     * An object identifier for exporting the activation system.
     */
    public static final Uuid ACTIVATION_SYSTEM_UUID =
 	UuidFactory.create("d7115186-273c-11b2-8836-080020c9e4a1");

    /** Prevents instantiation. */
    private PhoenixConstants() { throw new AssertionError(); }
}
