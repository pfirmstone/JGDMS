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

package com.sun.jini.logging;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.logging.Level;

/**
 * Defines additional {@link Level} values. <p>
 *
 * See the {@link LogManager} class for one way to use the <code>FAILED</code>
 * and <code>HANDLED</code> logging levels in standard logging configuration
 * files.
 *
 * 
 * @since 2.0
 */
public class Levels {

    /**
     * <code>FAILED</code> is a message level indicating that a facility has
     * experienced a failure that it will reflect to its caller. <p>
     *
     * <code>FAILED</code> messages are intended to provide users with
     * information about failures produced by internal components in order to
     * assist with debugging problems in systems with multiple components. This
     * level is initialized to <code>600</code>.
     */
    public static final Level FAILED = new CustomLevel("FAILED", 600, null);

    /**
     * <code>HANDLED</code> is a message level indicating that a facility has
     * detected a failure that it will take steps to handle without reflecting
     * the failure to its caller. <p>
     *
     * <code>HANDLED</code> messages are intended to provide users with
     * information about failures detected by internal components in order to
     * assist with debugging problems in systems with multiple components. This
     * level is initialized to <code>550</code>.
     */
    public static final Level HANDLED = new CustomLevel("HANDLED", 550, null);

    /** This class cannot be instantiated. */
    private Levels() {
	    throw new AssertionError("This class cannot be instantiated");
    }

    static class CustomLevel extends Level {
	    CustomLevel(String name, int value, String resourceBundleName) {
	        super(name, value, resourceBundleName);
	    }
	}

    
}
