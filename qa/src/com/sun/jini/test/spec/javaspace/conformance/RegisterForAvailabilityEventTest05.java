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
package com.sun.jini.test.spec.javaspace.conformance;

import net.jini.space.JavaSpace05;
import net.jini.space.AvailabilityEvent;
import net.jini.space.JavaSpace;
import net.jini.core.lease.Lease;
import net.jini.core.event.EventRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.rmi.MarshalledObject;

import com.sun.jini.qa.harness.TestException;


/**
 * RegisterForAvailabilityEventTest05 tests
 * JavaSpace05.registerForAvailabilityEvent method with null transaction.
 * See comments to run() method for details.
 *
 * @author Pavel Bogdanov
 */
public class RegisterForAvailabilityEventTest05 extends JavaSpaceTest {

    private final ArrayList templates = new ArrayList();
    private final ArrayList expectedResult = new ArrayList();

    private final SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
    private final SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
    private final SimpleEntry sampleEntry3 = new SimpleEntry("TestEntry #1", 2);

    /**
     * This method asserts that for JavaSpace05's
     * "registerForAvailabilityEvent" operation:<br>
     * 1) Register for events triggered when a matching Entry transitions from
     *    unavailable to available. The resulting events will be instances of
     *    the AvailabilityEvent class and the AvailabilityEvent.getEntry method
     *    will return a copy of the Entry whose transition triggered the event.
     *    <br>
     * 2) The tmpls parameter must be a Collection of Entry instances to be
     *    used as templates. Events will be generated when an Entry that
     *    matches one or more of these templates makes an appropriate
     *    transition. A single transition will generate only one event per
     *    registration, in particular the transition of an Entry that matches
     *    multiple elements of tmpls must still generate exactly one event for
     *    this registration. If a given Entry undergoes multiple applicable
     *    transitions while the registration is active, each must generate
     *    a separate event.<br>
     * 3) A non-null EventRegistration object will be returned. Each
     *    registration will be assigned an event ID. The event ID will be
     *    unique at least with respect to all other active event registrations
     *    for AvailabilityEvents on this space with a non-equivalent set of
     *    templates, a different transaction, and/or a different value for the
     *    visibilityOnly flag. The event ID can be obtained by calling the
     *    EventRegistration.getID method on the returned EventRegistration.
     *    The returned EventRegistration object's EventRegistration.getSource
     *    method will return a reference to the space.<br>
     * 4) Registrations are leased. leaseDurations  represents the client's
     *    desired initial lease duration. If leaseDuration is positive,
     *    the initial lease duration will be a positive value less than or
     *    equal to leaseDuration. If leaseDuration is Lease.ANY, the space is
     *    free to pick any positive initial lease duration it desires.
     *    A proxy for the lease associated with the registration can be
     *    obtained by calling the returned EventRegistration's
     *    EventRegistration.getLease  method.<br>
     * 5) Throws:
     *      IllegalArgumentException - if any non-null element of tmpls
     *      is not an instance of Entry, if tmpls is empty, or
     *      if leaseDuration is neither positive nor Lease.ANY.<br>
     *      NullPointerException - if tmpls or listener is null.<br>
     *
     * <P>Notes:<BR>For more information see the JavaSpaces05 javadoc </P>
     *
     * @throws Exception
     */
    public synchronized void run() throws Exception {
        ArrayList registrations = new ArrayList();

        TestEventListener05.setConfiguration(getConfig().getConfiguration());
        JavaSpace05 space05 = (JavaSpace05) space;
        reset();

        TestEventListener05 testEventListener0 = new TestEventListener05();
        templates.add((SimpleEntry) sampleEntry1.clone());
        EventRegistration er0 = space05.registerForAvailabilityEvent(templates,
                null, true, testEventListener0, leaseForeverTime,
                new MarshalledObject("notUsedHere"));
        final long gotER0Timestamp = System.currentTimeMillis();
        expectedResult.add(sampleEntry1);  // this entry is to trigger the event
        space.write(sampleEntry1, null, leaseForeverTime);
        Thread.sleep(waitingNotificationsToComeTime);
        checkNotifications(testEventListener0.getNotifications(), expectedResult,
                           "Writing one entry to trigger an event");
        registrations.add(er0);
        reset();

        TestEventListener05 testEventListener1 = new TestEventListener05();
        templates.add((SimpleEntry) sampleEntry1.clone());
        templates.add((SimpleEntry) sampleEntry2.clone());
        EventRegistration er1 = space05.registerForAvailabilityEvent(templates,
                null, true, testEventListener1, leaseForeverTime,
                new MarshalledObject("notUsedHere"));
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry2);
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        Thread.sleep(waitingNotificationsToComeTime);
        checkNotifications(testEventListener1.getNotifications(), expectedResult,
                           "Writing 2 entries to trigger 2 events");
        registrations.add(er1);
        reset();

        TestEventListener05 testEventListener2 = new TestEventListener05();
        templates.add(new SimpleEntry(null, 2));
        EventRegistration er2 = space05.registerForAvailabilityEvent(templates,
                null, true, testEventListener2, leaseForeverTime,
                new MarshalledObject("notUsedHere"));
        expectedResult.add(sampleEntry2);
        expectedResult.add(sampleEntry3);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);
        Thread.sleep(waitingNotificationsToComeTime);
        checkNotifications(testEventListener2.getNotifications(), expectedResult,
                "Writing 2 entries to trigger 2 events (with single template)");
        registrations.add(er2);
        reset();

        TestEventListener05 testEventListener3 = new TestEventListener05();
        templates.add(new SimpleEntry(null, null));
        EventRegistration er3 = space05.registerForAvailabilityEvent(templates,
                null, true, testEventListener3, leaseForeverTime,
                new MarshalledObject("notUsedHere"));
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry2);
        expectedResult.add(sampleEntry3);
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);
        Thread.sleep(waitingNotificationsToComeTime);
        checkNotifications(testEventListener3.getNotifications(), expectedResult,
                "Writing 3 entries to trigger 3 events (with single template)");
        registrations.add(er3);
        reset();

        TestEventListener05 testEventListener4 = new TestEventListener05();
        templates.add(null);
        EventRegistration er4 = space05.registerForAvailabilityEvent(templates,
                null, true, testEventListener4, leaseForeverTime,
                new MarshalledObject("notUsedHere"));
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry2);
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        Thread.sleep(waitingNotificationsToComeTime);
        checkNotifications(testEventListener4.getNotifications(), expectedResult,
                "Writing 2 entries to trigger 2 events (with null template)");
        registrations.add(er4);
        reset();

        TestEventListener05 testEventListener5 = new TestEventListener05();
        templates.add(null);
        EventRegistration er5 = space05.registerForAvailabilityEvent(templates,
                null, true, testEventListener5, leaseForeverTime,
                new MarshalledObject("notUsedHere"));
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry1);
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry1, null, leaseForeverTime);
        Thread.sleep(waitingNotificationsToComeTime);
        checkNotifications(testEventListener5.getNotifications(), expectedResult,
                           "Writing 2 duplicate entries to trigger 2 events "
                           + "(with null template)");

        /*
         * not adding this as a registration for such set
         * of templates (null) already exists
         */
        //registrations.add(er5);
        reset();

        TestEventListener05 testEventListener6 = new TestEventListener05();
        templates.add(null);
        templates.add(new SimpleEntry(null, null));
        templates.add((SimpleEntry) sampleEntry2.clone());
        templates.add((SimpleEntry) sampleEntry3.clone());
        templates.add(new SimpleEntry(null, 2));
        EventRegistration er6 = space05.registerForAvailabilityEvent(templates,
                null, true, testEventListener6, leaseForeverTime,
                new MarshalledObject("notUsedHere"));
        expectedResult.add(sampleEntry2);
        expectedResult.add(sampleEntry3);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);
        Thread.sleep(waitingNotificationsToComeTime);
        checkNotifications(testEventListener6.getNotifications(), expectedResult,
                           "Writing 2 entries to trigger 2 events " +
                           "(with multiple matching templates)");
        registrations.add(er6);
        reset();

        /*
         * Section below is the same as above but
         * with visibilityOnly flag set to false.
         * "a" stands for availability.
         */
        TestEventListener05 testEventListener0a = new TestEventListener05();
        templates.add((SimpleEntry) sampleEntry1.clone());
        EventRegistration er0a = space05.registerForAvailabilityEvent(
                templates, null, false,
                testEventListener0a, leaseForeverTime,
                new MarshalledObject("notUsedHere"));
        expectedResult.add(sampleEntry1);  // this entry is to trigger the event
        space.write(sampleEntry1, null, leaseForeverTime);
        Thread.sleep(waitingNotificationsToComeTime);
        checkNotifications(testEventListener0a.getNotifications(), expectedResult,
                           "Writing one entry to trigger an event");
        registrations.add(er0a);
        reset();

        TestEventListener05 testEventListener1a = new TestEventListener05();
        templates.add((SimpleEntry) sampleEntry1.clone());
        templates.add((SimpleEntry) sampleEntry2.clone());
        EventRegistration er1a = space05.registerForAvailabilityEvent(
                templates, null, false,
                testEventListener1a, leaseForeverTime,
                new MarshalledObject("notUsedHere"));
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry2);
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        Thread.sleep(waitingNotificationsToComeTime);
        checkNotifications(testEventListener1a.getNotifications(), expectedResult,
                           "Writing 2 entries to trigger 2 events");
        registrations.add(er1a);
        reset();

        TestEventListener05 testEventListener2a = new TestEventListener05();
        templates.add(new SimpleEntry(null, 2));
        EventRegistration er2a = space05.registerForAvailabilityEvent(
                templates, null, false,
                testEventListener2a, leaseForeverTime,
                new MarshalledObject("notUsedHere"));
        expectedResult.add(sampleEntry2);
        expectedResult.add(sampleEntry3);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);
        Thread.sleep(waitingNotificationsToComeTime);
        checkNotifications(testEventListener2a.getNotifications(), expectedResult,
                           "Writing 2 entries to trigger 2 events "
                           + "(with single template)");
        registrations.add(er2a);
        reset();

        TestEventListener05 testEventListener3a = new TestEventListener05();
        templates.add(new SimpleEntry(null, null));
        EventRegistration er3a = space05.registerForAvailabilityEvent(
                templates, null, false,
                testEventListener3a, leaseForeverTime,
                new MarshalledObject("notUsedHere"));
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry2);
        expectedResult.add(sampleEntry3);
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);
        Thread.sleep(waitingNotificationsToComeTime);
        checkNotifications(testEventListener3a.getNotifications(), expectedResult,
                           "Writing 3 entries to trigger 3 events "
                           + "(with single template)");
        registrations.add(er3a);
        reset();

        TestEventListener05 testEventListener4a = new TestEventListener05();
        templates.add(null);
        EventRegistration er4a = space05.registerForAvailabilityEvent(
                templates, null, false,
                testEventListener4a, leaseForeverTime,
                new MarshalledObject("notUsedHere"));
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry2);
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        Thread.sleep(waitingNotificationsToComeTime);
        checkNotifications(testEventListener4a.getNotifications(), expectedResult,
                           "Writing 2 entries to trigger 2 events "
                           + "(with null template)");
        registrations.add(er4a);
        reset();

        TestEventListener05 testEventListener5a = new TestEventListener05();
        templates.add(null);
        EventRegistration er5a = space05.registerForAvailabilityEvent(
                templates, null, false,
                testEventListener5a, leaseForeverTime,
                new MarshalledObject("notUsedHere"));
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry1);
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry1, null, leaseForeverTime);
        Thread.sleep(waitingNotificationsToComeTime);
        checkNotifications(testEventListener5a.getNotifications(), expectedResult,
                           "Writing 2 duplicate entries to trigger 2 events "
                           + "(with null template)");

        /*
         * not adding this as a registration for such set
         * of templates (null) already exists
         */
        //registrations.add(er5a);
        reset();

        TestEventListener05 testEventListener6a = new TestEventListener05();
        templates.add(null);
        templates.add(new SimpleEntry(null, null));
        templates.add((SimpleEntry) sampleEntry2.clone());
        templates.add((SimpleEntry) sampleEntry3.clone());
        templates.add(new SimpleEntry(null, 2));
        EventRegistration er6a = space05.registerForAvailabilityEvent(
                templates, null, false,
                testEventListener6a, leaseForeverTime,
                new MarshalledObject("notUsedHere"));
        expectedResult.add(sampleEntry2);
        expectedResult.add(sampleEntry3);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);
        Thread.sleep(waitingNotificationsToComeTime);
        checkNotifications(testEventListener6a.getNotifications(), expectedResult,
                           "Writing 2 entries to trigger 2 events " +
                           "(with multiple matching templates)");
        registrations.add(er6a);
        reset();

        ArrayList eventIDs = new ArrayList();
        Iterator registrationsItr = registrations.iterator();
        while (registrationsItr.hasNext()) {
            EventRegistration er = (EventRegistration) registrationsItr.next();
            if (er == null) {
                throw new TestException("Event registration is null");
            }
            Long id = new Long(er.getID());
            if (eventIDs.contains(id)) {
                throw new TestException("Event registrations have "
                                        + "identical IDs");
            }
            eventIDs.add(id);
        }

        JavaSpace testSpace = (JavaSpace) er0.getSource();
        space.write(sampleEntry1, null, leaseForeverTime);
        SimpleEntry entry = (SimpleEntry) testSpace.readIfExists(sampleEntry1,
                                                                 null,
                                                                 instantTime);
        if (!sampleEntry1.equals(entry)) {
            throw new TestException("EventRegistration.getSource method "
                                    + "does not return a proper "
                                    + "reference to the space");
        }
        reset();

        Lease lease = er0.getLease();
        if (lease.getExpiration() > gotER0Timestamp + leaseForeverTime) {
            throw new TestException(
                    "Lease for EventRegistration expires later than expected");
        }

        TestEventListener05 testEventListenerExc = new TestEventListener05();
        templates.add((SimpleEntry) sampleEntry1.clone());
        templates.add("not an Entry");
        try {
            space05.registerForAvailabilityEvent(templates, null, true,
                    testEventListenerExc, leaseForeverTime,
                    new MarshalledObject("notUsedHere"));
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when a non-null element of tmpls "
                                    + "is not an instance of Entry");
        } catch (IllegalArgumentException e) {}

        templates.clear();        
        try {
            space05.registerForAvailabilityEvent(templates, null, true,
                    testEventListenerExc, leaseForeverTime,
                    new MarshalledObject("notUsedHere"));
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when tmpls is empty");
        } catch (IllegalArgumentException e) {}

        templates.add((SimpleEntry) sampleEntry1.clone());
        try {
            space05.registerForAvailabilityEvent(templates, null, true,
                    testEventListenerExc, 0,
                    new MarshalledObject("notUsedHere"));
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when leaseDuration is neither positive "
                                    + "nor Lease.ANY (0)");
        } catch (IllegalArgumentException e) {}

        try {
            space05.registerForAvailabilityEvent(templates, null, true,
                    testEventListenerExc, Lease.ANY - 1,
                    new MarshalledObject("notUsedHere"));
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when leaseDuration is neither positive "
                                    + "nor Lease.ANY (Lease.ANY-1)");
        } catch (IllegalArgumentException e) {}

        try {
            space05.registerForAvailabilityEvent(null, null, true,
                    testEventListenerExc, leaseForeverTime,
                    new MarshalledObject("notUsedHere"));
            throw new TestException("NullPointerException is not thrown "
                                    + "when tmpls is null");
        } catch (NullPointerException e) {}

        try {
            space05.registerForAvailabilityEvent(templates, null, true,
                    null, leaseForeverTime,
                    new MarshalledObject("notUsedHere"));
            throw new TestException("NullPointerException is not thrown "
                                    + "when listener is null");
        } catch (NullPointerException e) {}
    }

    private void checkNotifications(List notifications, List expectedResult,
                                    String testName) throws Exception {


        Iterator notificationsItr = notifications.iterator();
        while (notificationsItr.hasNext()) {
            AvailabilityEvent availEvent =
                    (AvailabilityEvent) notificationsItr.next();
            SimpleEntry se = (SimpleEntry) availEvent.getEntry();
            if (!expectedResult.contains(se)) {
                throw new TestException(testName + " failed. Unexpected "
                                        + "entry triggered the event");
            } else {
                expectedResult.remove(se);
            }
        }
        if (expectedResult.size() > 0) {
            throw new TestException(testName + " failed. Not all the entries "
                                    + "triggered appropriate events");
        }
    }

    /**
     * Clears templates, expectedResult lists and space
     *
     * @throws Exception
     */
    private void reset() throws Exception {
        templates.clear();
        expectedResult.clear();
        cleanSpace(space);
    }

}
