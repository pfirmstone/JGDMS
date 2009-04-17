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

package com.sun.jini.test.impl.fiddler.joinadmin;

import java.util.logging.Level;

import com.sun.jini.test.share.AttributesUtil;
import net.jini.core.entry.Entry;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully modify its current set of attributes.
 *
 * This test adds one or more "well-known" attributes to the service's 
 * current set and then attempts to modify that set of just-added attributes
 * by inputing a null reference for the parameter corresponding to the
 * new set of attribute templates; which should result in a
 * <code>NullPointerException</code>.
 */
public class ModifyLookupAttributesNullTmpl
       extends ModifyLookupAttributesNullInput
{
    /** Constructs and returns the set of attributes that are used in the
     *  matching process for the attributes that will be modified (overrides
     *  the parent class' version of this method)
     */
    Entry[] getAttributeSetTemplates() {
        return null;
    }

    /** Constructs and returns the set of attributes that are used to modify
     *  the desired set of attributes (overrides the parent class' version
     *  of this method)
     */
    Entry[] getTestAttributeSet() {
        return new Entry[] {new AttributesUtil.TestAttr00("ZZZ","ZZZ","ZZZ"),
                            new AttributesUtil.TestAttr00("YYY","YYY","YYY")
                           };
    }
}


