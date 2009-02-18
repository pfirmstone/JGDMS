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
package com.sun.jini.test.spec.loader.util;


/**
 *  Helper class for classes/resources description.
 *  Static arrays of this class describe classes/resources names and
 *  it's preffered status.
 *
 */
public class Item {

    /** Name of package which contains preferred/non-preferred classes */
    public static final String PACKAGE = "com.sun.jini.test.spec.loader.util.";

    /** Relative path which contains preferred/non-preferred resources */
    public static final String PATH = "com/sun/jini/test/spec/loader/util/";

    /** The name of class/resource */
    public String name;

    /** Boolean value to indicate whether or not class/resource is preferred */
    public boolean pref;

    /** Create item with the given name and preferred flag */
    Item(String name, boolean pref, boolean isClass) {
        String pkg = isClass ? this.PACKAGE : this.PATH;
        this.name = pkg + name;
        this.pref = pref;
    }
}
