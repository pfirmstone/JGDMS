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

/**
 * An activation descriptor contains the information necessary to activate an object:
 * <ul>
 *   <li>the object's group identifier,</li>
 *   <li>the object's fully-qualified class name,</li>
 *   <li>the object's code location (the location of the class), a codebase URL path,</li>
 *   <li>the object's restart "mode", and,</li>
 *   <li>a <code>String[]</code> that can contain object specific initialization data,
 *       typically used for configuration. </li>
 * </ul>
 * <p>
 * A descriptor registered with the activation system can be used to 
 * recreate/activate the object specified by the descriptor. 
 * The <code>String[]</code> in the object's descriptor is passed as the 
 * second argument to the remote object's constructor for object to 
 * use during reinitialization/activation.
 * </p>
 */
public interface ActivationDesc {
    
    /**
     * Returns the group identifier for the object specified by this descriptor.
     * A group provides a way to aggregate objects into a single Java
     * virtual machine. RMI creates/activates objects with the same groupID
     * in the same virtual machine.
     * 
     * @return the group identifier.
     */
    public ActivationGroupID getGroupID();
    /**
     * Returns intialization/activation/configuration
     * data in the form of a String[] specified by this descriptor.
     * 
     * @return a String[]
     */
    public String[] getData();
    /**
     * Returns the code location for the object specified by this descriptor.
     * @return the code location for the object specified by this descriptor.
     */
    public String getLocation();
    /**
     * Returns the class name for the object specified by this descriptor.
     * @return the class name for the object specified by this descriptor.
     */
    public String getClassName();
    /**
     * Returns the "restart" mode of the object associated with this activation descriptor.
     * @return true if the activatable object associated with this activation
     * descriptor is restarted via the activation daemon when either the
     * daemon comes up or the object's group is restarted after an unexpected
     * crash; otherwise it returns false, meaning that the object is only
     * activated on demand via a method call. Note that if the restart mode 
     * is true, the activator does not force an initial immediate activation
     * of a newly registered object; initial activation is lazy.
     */
    public boolean getRestartMode();
    
}
