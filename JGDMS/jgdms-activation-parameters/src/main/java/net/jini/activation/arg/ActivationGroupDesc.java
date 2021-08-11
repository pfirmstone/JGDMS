/*
 * Copyright 2021 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jini.activation.arg;

import java.util.Properties;

/**
 * An activation group descriptor contains the information necessary to
 * create/recreate an activation group in which to activate objects.
 * <p>
 * Such a descriptor contains:
 *  <ul>
 *    <li>the group's class name,</li>
 *    <li>the group's code location (the location of the group's class), and</li>
 *    <li>a <code>String[]</code> that can contain group specific initialization data. </li>
 *  </ul>
 * <p>
 * The group's class must be a concrete subclass of ActivationGroup.
 * A subclass of ActivationGroup is created/recreated via the 
 * ActivationGroup.createGroup static method that invokes a special
 * constructor that takes three arguments:
 *  <ul>
 *    <li>the group's ActivationGroupID, and</li>
 *    <li>the group's ActivationGroupDesc, and<li>
 *    <li>the group's incarnation</li>
 *  </ul>
 */
public interface ActivationGroupDesc {
    /**
     * Returns the group's class name (possibly null). 
     * A null group class name indicates the system's default ActivationGroup implementation.
     * @return the group class name, or null.
     */
    public String getClassName();

    /**
     * Returns the group's code location.
     * @return the group's code location.
     */
    public String getLocation();

    /**
     * Returns the group's initialization data, in the form of a String[] array. 
     * 
     * @return the group's initialization data
     */
    public String[] getData();

    /**
     * Returns the group's command-environment control object.
     * @return the command-environment object, or null
     */
    public CommandEnvironment getCommandEnvironment();

    /**
     * Returns the group's property-override list.
     * @return the property-override list, or null
     */
    public Properties getPropertyOverrides();
    
    /**
     * Startup options for ActivationGroup implementations. 
     * This class allows overriding default system properties and specifying
     * implementation-defined options for ActivationGroups.
     */
    public static interface CommandEnvironment {
        /**
         * Fetch the configured java command options.
         * @return An array of the command options which will be passed to 
         * the new child command by Phoenix Activation. Note that Phoenix may
         * add other options before or after these options, or both.
         * Never returns null.
         */
        public String[] getCommandOptions();
        /**
         * Fetch the configured path-qualified java command name.
         * @return the configured name, or null if configured to accept the default
         */
        public String getCommandPath();
    }
    
}
