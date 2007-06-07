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
package net.jini.core.event;

/**
 * An exception thrown when the recipient of a RemoteEvent does not recognize
 * the combination of the event identifier and the event source as something
 * in which it is interested.  Throwing this exception has the effect of
 * asking the sender to not send further notifications of this kind of event
 * from this source in the future.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */

public class UnknownEventException extends Exception
{
    private static final long serialVersionUID = 5563758083292687048L;

    /**
     * Constructs an UnknownEventException with the specified detail message.
     *
     * @param reason  a <tt>String</tt> containing a detail message
     */
    public UnknownEventException(String reason) 
    {
	super(reason);
    }

    /**
     * Constructs an UnknownEventException with no detail message.
     */
    public UnknownEventException()
    {
	super();
    }
}
