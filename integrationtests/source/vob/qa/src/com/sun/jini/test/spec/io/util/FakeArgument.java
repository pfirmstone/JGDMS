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
package com.sun.jini.test.spec.io.util;

import java.io.Serializable;
import java.util.logging.Logger;

/**
 * A fake serializable object that contains some state that can be verified.
 * It is intended that this class be placed in a downloadable JAR file.
 */
public class FakeArgument implements Serializable {

    int state;

    /**
     * Constructs a FakeArgument.  
     * Equivalent to calling FakeArgument(43).
     */
    public FakeArgument() {
        this(43);
    }

    /**
     * Constructs a FakeArgument.
     *
     * @param state some internal state for the object
     */
    public FakeArgument(int state) {
        Logger logger = Logger.getLogger("com.sun.jini.qa.harness.test");
        logger.entering(getClass().getName(),"constructor", new Integer(state));
        this.state = state;
    }

    /**
     * Two <code>FakeArguments</code> are equal if both have identical
     * <code>state</code> fields.
     */
    public boolean equals(Object object) {
        Logger logger = Logger.getLogger("com.sun.jini.qa.harness.test");
        logger.entering(getClass().getName(),"equals",object);
        if (object == null || !(object instanceof FakeArgument)) {
            return false;
        }

        FakeArgument fa = (FakeArgument)object;
        return fa.state == state;
    }

    /**
     * Overloads <code>Object.hashCode</code>.
     *
     * @return the value 13
     */
    public int hashCode() {
        Logger logger = Logger.getLogger("com.sun.jini.qa.harness.test");
        logger.entering(getClass().getName(),"hashCode");
        return 13;
    }
}
