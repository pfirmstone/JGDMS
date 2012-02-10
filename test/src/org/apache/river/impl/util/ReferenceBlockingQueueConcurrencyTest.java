/* 
 * University of Illinois/NCSA
 * Open Source License
 *
 * Copyright (c) 2011, University of Illinois at Urbana-Champaign.
 * All rights reserved.
 *
 * Developed by:
 *
 *     The IMUnit Project Team:
 *         Vilas Jagannath (vbangal2@illinois.edu)
 *         Milos Gligoric (gliga@illinois.edu)
 *         Dongyun Jin (djin3@illinois.edu)
 *         Qingzhou Luo (qluo2@illinois.edu)
 *         Grigore Rosu (grosu@illinois.edu)
 *         Darko Marinov (marinov@illinois.edu)
 *     University of Illinois at Urbana-Champaign
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal with the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimers.
 * 
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimers in the documentation and/or other materials provided
 *       with the distribution.
 *   
 *     * Neither the names of the IMUnit project team, the University of
 *       Illinois at Urbana-Champaign, nor the names of its contributors
 *       may be used to endorse or promote products derived from this
 *       Software without specific prior written permission.
 *    
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE CONTRIBUTORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS WITH THE SOFTWARE.
 */

package org.apache.river.impl.util;

import static edu.illinois.imunit.IMUnit.fireEvent;
import static edu.illinois.imunit.IMUnit.schAssertEquals;
import edu.illinois.imunit.IMUnitRunner;
import edu.illinois.imunit.Schedule;
import edu.illinois.imunit.Schedules;
import static org.junit.Assert.assertEquals;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This class demonstrates how multithreaded unit tests can be written using IMUnit.
 * The original demonstration class was suitable, with some minor modification
 * to test ReferenceBlockingQueue
 * 
 * @author Vilas Jagannath <vbangal2@illinois.edu>
 * 
 */
@RunWith(IMUnitRunner.class)
public class ReferenceBlockingQueueConcurrencyTest{
    
 private BlockingQueue<Integer> queue;

    @Before
    public void setup() {
        queue = RC.blockingQueue(new ArrayBlockingQueue<Referrer<Integer>>(1), Ref.SOFT);
    }

    @Test
    @Schedule("finishOffer2->startingTake")
    public void testOfferOfferTake() throws InterruptedException {
        performParallelOfferssAndTake();
        assertEquals(0, queue.size());
    }

    @Test
    @Schedule("finishOffer1->startingTake,finishTake->startingOffer2")
    public void testOfferTakeOffer() throws InterruptedException {
        performParallelOfferssAndTake();
        assertEquals(1, queue.size());
    }

    @Test
    @Schedule("[startingTake]->startingOffer1,finishTake->startingOffer2")
    public void testTakeBlockOfferTakeFinishOffer() throws InterruptedException {
        performParallelOfferssAndTake();
        assertEquals(1, queue.size());
    }

    @Test
    @Schedules({ @Schedule(name = "offer-offer-take", value = "finishOffer2->startingTake"),
            @Schedule(name = "offer-take-offer", value = "finishOffer1->startingTake,finishTake->startingOffer2"),
            @Schedule(name = "takeBlock-offer-takeFinish-offer", value = "[startingTake]->startingOffer1,finishTake->startingOffer2") })
    public void testAllThreeSchedules() throws InterruptedException {
        performParallelOfferssAndTake();
        schAssertEquals("offer-offer-take", 0, queue.size());
        schAssertEquals("offer-take-offer", 1, queue.size());
        schAssertEquals("takeBlock-offer-takeFinish-offer", 1, queue.size());
    }

    private void performParallelOfferssAndTake() throws InterruptedException {
        Thread offerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                fireEvent("startingOffer1");
                queue.offer(42);
                fireEvent("finishOffer1");
                fireEvent("startingOffer2");
                queue.offer(47);
                fireEvent("finishOffer2");
            }
        });
        offerThread.start();
        fireEvent("startingTake");
        assertEquals(42, (int) queue.take());
        fireEvent("finishTake");
        offerThread.join();
    }

}
