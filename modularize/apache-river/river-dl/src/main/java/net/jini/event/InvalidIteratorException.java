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
package net.jini.event;

/**
 * An exception thrown when an event iterator becomes invalid.
 * An event iterator becomes invalid when:
 * <UL> 
 * <LI> A subsequent call to 
 * {@link MailboxPullRegistration#getRemoteEvents 
 * MailboxPullRegistration.getRemoteEvents}
 * creates another iterator for 
 * the same registration.
 * <LI> A subsequent call to 
 * {@link MailboxRegistration#enableDelivery(net.jini.core.event.RemoteEventListener) 
 * MailboxRegistration.enableDelivery}
 * for the same registration switches from  
 * iterator functionality to event listener functionality.
 * <LI> A subsequent call to {@link RemoteEventIterator#close() 
 * RemoteEventIterator.close} occurs.
 * </UL>
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 2.1
 */

public class InvalidIteratorException extends Exception
{
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an InvalidIteratorException with the specified detail message.
     *
     * @param reason  a <code>String</code> containing a detail message
     */
    public InvalidIteratorException(String reason) 
    {
	super(reason);
    }

    /**
     * Constructs an InvalidIteratorException with no detail message.
     */
    public InvalidIteratorException()
    {
	super();
    }
}
