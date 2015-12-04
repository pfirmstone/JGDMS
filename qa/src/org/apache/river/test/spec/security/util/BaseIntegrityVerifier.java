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
package org.apache.river.test.spec.security.util;

// java
import java.net.URL;
import java.util.ArrayList;

// net.jini
import net.jini.security.IntegrityVerifier;


/**
 * Base class for all IntegrityVerifier-s implementing IntegrityVerifier
 * interface.
 */
public abstract class BaseIntegrityVerifier implements IntegrityVerifier {

    /** Classes whose 'isTrustedObject' method was called. */
    protected static ArrayList classes = new ArrayList();

    /** Urls passed to 'providesIntegrity' method. */
    protected static ArrayList urls = new ArrayList();

    /**
     * Returns classes whose 'isTrustedObject' method was called.
     *
     * @return classes whose 'isTrustedObject' method was called
     */
    public static Class[] getClasses() {
        return (Class []) classes.toArray(new Class[classes.size()]);
    }

    /**
     * Returns urls passed to 'providesIntegrity' method.
     *
     * @return urls passed to 'providesIntegrity' method
     */
    public static URL[] getUrls() {
        return (URL [] ) urls.toArray(new URL[urls.size()]);
    }

    /**
     * Resets urls list.
     */
    public static void initLists() {
        classes = new ArrayList();
        urls = new ArrayList();
    }
}
