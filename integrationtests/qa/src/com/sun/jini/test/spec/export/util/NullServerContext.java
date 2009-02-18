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
package com.sun.jini.test.spec.export.util;

// davis packages
import net.jini.export.ServerContext;

// java.util
import java.util.Collection;


/**
 * An auxiliary class used in the tests for
 * {@link net.jini.export.ServerContext} as a server context provider.
 * This is a "Null Server Context Provider", i.e.
 * {@link NullServerContext#getServerContext()}
 * returns null.
 */
public class NullServerContext implements ServerContext.Spi {

    /**
     * Returns null server context collection.
     *
     * @return	null server context collection
     */
    public Collection getServerContext() {
        System.out.println(this.getClass().getName()
                + ".getServerContext() is invoked");
        return null;
    }
}
