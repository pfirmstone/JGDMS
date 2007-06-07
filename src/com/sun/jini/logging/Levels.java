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
 * @author Sun Microsystems, Inc.
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
    public static final Level FAILED = createLevel("FAILED", 600, null);

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
    public static final Level HANDLED = createLevel("HANDLED", 550, null);

    /** This class cannot be instantiated. */
    private Levels() {
	throw new AssertionError("This class cannot be instantiated");
    }

    /**
     * Defines a class that has the same data format as the Level class, to
     * permit creating the serialized form of a Level instance.
     */
    private static final class LevelData implements Serializable {
	private static final long serialVersionUID = -8176160795706313070L;
	private final String name;
	private final int value;
	private final String resourceBundleName;
	LevelData(String name, int value, String resourceBundleName) {
	    this.name = name;
	    this.value = value;
	    this.resourceBundleName = resourceBundleName;
	}
    }

    /**
     * Defines an object output stream that allows the data for one class to be
     * interpreted as the data for another class.  This class is useful in
     * creating serialization data for a class when access to an appropriate
     * constructor is not available.
     */
    private static final class ClassReplacingObjectOutputStream
	extends ObjectOutputStream
    {
	private final ObjectStreamClass from;
	private final ObjectStreamClass to;

	ClassReplacingObjectOutputStream(OutputStream out,
					 Class from,
					 Class to)
	    throws IOException
	{
	    super(out);
	    this.from = ObjectStreamClass.lookup(from);
	    this.to = ObjectStreamClass.lookup(to);
	}

	protected void writeClassDescriptor(ObjectStreamClass desc)
	    throws IOException
	{
	    if (from.equals(desc)) {
		desc = to;
	    }
	    super.writeClassDescriptor(desc);
	}
    }

    /**
     * Creates an instance of the Level class.  This method works around the
     * fact that there is no public constructor for the Level class by
     * constructing the serialized form for an instance with the specified
     * field values and deserializing it.
     */
    private static Level createLevel(String name,
				     int value,
				     String resourceBundleName)
    {
	try {
	    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
	    ObjectOutputStream out =
		new ClassReplacingObjectOutputStream(
		    bytes, LevelData.class, Level.class);
	    out.writeObject(new LevelData(name, value, resourceBundleName));
	    out.close();
	    ObjectInputStream in = new ObjectInputStream(
		new ByteArrayInputStream(bytes.toByteArray()));
	    Level result = (Level) in.readObject();
	    in.close();
	    return result;
	} catch (Exception e) {
	    throw new RuntimeException("Unexpected exception", e);
	}
    }
}
