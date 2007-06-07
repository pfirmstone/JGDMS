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
package com.sun.jini.reggie;

import net.jini.id.Uuid;
import net.jini.id.UuidFactory;

/**
 * Class for generating {@link Uuid} instances.  By default, Reggie uses
 * instances of this class to generate <code>Uuid</code>s; this behavior can be
 * customized by providing as configuration entry values instances of
 * subclasses which override the {@link #generate} method of this class.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 2.0
 */
public class UuidGenerator {

    /**
     * Creates new <code>UuidGenerator</code> instance.
     */
    public UuidGenerator() {
    }

    /**
     * Generates a new <code>Uuid</code>.  The default implementation of this
     * method returns the result of calling {@link UuidFactory#generate};
     * subclasses can override this method to customize <code>Uuid</code>
     * generation.
     *
     * @return newly generated <code>Uuid</code>
     */
    // REMIND: accept last generated Uuid as input, for stateful generators?
    public Uuid generate() {
	return UuidFactory.generate();
    }
}
