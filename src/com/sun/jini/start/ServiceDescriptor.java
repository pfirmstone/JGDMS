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
package com.sun.jini.start;
import net.jini.config.Configuration;

/**
 * Interface that all service description objects must implement.
 *
 * @see SharedActivationGroupDescriptor
 * @see SharedActivatableServiceDescriptor
 * @see NonActivatableServiceDescriptor
 */
public interface ServiceDescriptor {
    /**
     * Creates an object described by the 
     * actual <code>ServiceDescriptor</code> instance.
     * @param config The <code>Configuration</code> object
     *     used to configure the creation of the returned object.
     * @return An object instance appropriate for the given
     *     service descriptor instance.
     * @throws java.lang.Exception Thrown if there was any problem 
     *     creating the object.
     */
    public Object create(Configuration config) throws Exception;
}

