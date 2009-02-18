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

package com.sun.jini.test.spec.locatordiscovery;

import java.util.logging.Level;

/**
 * This class verifies that the <code>LookupLocatorDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular, this
 * class verifies that the method <code>getLocators</code> returns
 * the empty array if the managed set of locators the lookup locator
 * discovery utility is currently configured to discover is empty.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> zero lookup services
 *    <li> an instance of the lookup locator discovery utility that is
 *         configured to discover no locators
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then the
 * <code>getLocators</code> method will return an empty array.
 *
 */
public class GetLocatorsEmpty extends GetLocators {
}//end class GetLocatorsEmpty

