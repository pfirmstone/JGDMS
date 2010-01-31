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
package com.sun.jini.tool.classdepend;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.ClassReader;

/**
 * A utility class for computing the classes referred to by another class.
 * This class cannot be instantiated.
 */
public class ReferencedClasses {

    /** This class cannot be instantiated. */
    private ReferencedClasses() {
	throw new AssertionError();
    }

    /**
     * Computes the classes referred to by another class.  The argument should
     * be a input stream containing the bytecodes for the class.  The return
     * value is a set containing the names of the classes referred to by the
     * class bytecodes in the input.  The input stream is left open when this
     * method returns.
     *
     * @param	in the input stream containing the class bytecodes
     * @return	a set of the names of the classes referred to by the class
     *		bytecodes
     * @throws	IOException if a I/O failure occurs while reading the class
     *		bytecodes
     */
    public static Set compute(InputStream in) throws IOException {
	if (in == null) {
	    throw new NullPointerException(
		"The in argument must not be null");
	}
	final Set dependencies = new HashSet();
	new ClassReader(in).accept(
	    new AbstractDependencyVisitor() {
		protected void addName(String name) {
		    dependencies.add(name);
		}
	    },
	    ClassReader.SKIP_DEBUG);
	return dependencies;
    }
}
