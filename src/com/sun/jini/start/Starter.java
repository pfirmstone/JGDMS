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

/**
 * Implemented by a service to enable starting after construction.
 * 
 */
public interface Starter {
    /**
     * Called by the ServiceStarter after construction, allows the service to
     * delay starting threads until construction is complete, to allow safe
     * publication of the service in accordance with the JMM.
     * 
     * In addition to starting threads after construction, it also allows
     * services to avoid throwing an exception during construction to avoid
     * finalizer attacks.
     * 
     * @throws Exception if there's a problem with construction or startup.
     */
    public void start() throws Exception;
}
