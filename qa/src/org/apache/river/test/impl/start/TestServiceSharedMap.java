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
package org.apache.river.test.impl.start;

import net.jini.id.*;

import java.util.*;

import java.io.Serializable;

/**
 * A shared class used by ClassLoaderTest
 * that must be defined in a class loader
 * that is common to all application class loaders.
 */
public class TestServiceSharedMap {

    private static final Map uuidToImpl = 
	Collections.synchronizedMap(new HashMap());

    private TestServiceSharedMap() {
	// prevent instantiation
    }
    
    public static void storeClassLoader(Uuid uuid, ClassLoader cl) {
        uuidToImpl.put(uuid, cl);
    }
    public static ClassLoader getClassLoader(Uuid uuid) {
        return (ClassLoader) uuidToImpl.get(uuid);
    }
}
