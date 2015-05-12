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
package org.apache.river.test.impl.outrigger.matching;

// imports
import net.jini.core.entry.Entry;
import java.lang.reflect.InvocationTargetException;


/**
 * Template generators take a JavaSpace entry in their constructor a
 * generate a sequence of templates that will ether match, or not
 * match the entry they were given when they were constructed.
 */
interface TemplateGenerator {

    /**
     * Returns the next template in the sequence. Returns
     * <code>null</code> if sequence is exhausted
     */
    public Entry next()
            throws InvocationTargetException, IllegalAccessException,
            InstantiationException;

    /**
     * Return <code>true</code> if this generator returns templates
     * that are supposted to match the entry passed to the constructor
     * and <code>false</code> otherwise.
     */
    public boolean isMatchingGenerator();
}
