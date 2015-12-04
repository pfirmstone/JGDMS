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
package org.apache.river.test.spec.export.util;

// org.apache.river.qa
import org.apache.river.qa.harness.TestException;

// davis packages
import net.jini.export.ServerContext;

// java.util
import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;


/**
 * An auxiliary class used in the tests for
 * {@link net.jini.export.ServerContext} as a server context provider.
 * This is a "Non-null Server Context Provider", i.e.
 * {@link NonNullServerContext_Another#getServerContext()} returns
 * {@link java.util.Collection} that contains only one element - the fully
 * qualified name of the class as String
 * ("org.apache.river.test.spec.export.util.NonNullServerContext_Another").
 */
public class NonNullServerContext_Another implements ServerContext.Spi {
    private Collection context = new ArrayList();

    /**
     * Returns server context collection.
     *
     * @return	server context collection
     */
    public Collection getServerContext() {
        System.out.println(this.getClass().getName()
                + ".getServerContext() is invoked");
        context.add(this.getClass().getName());
        return context;
    }
}
