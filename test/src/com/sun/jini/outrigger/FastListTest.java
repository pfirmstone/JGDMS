package com.sun.jini.outrigger;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests of com.sun.jini.outrigger.FastList
 * 
 */
public class FastListTest {

    /** The FastList under test. */
    private FastList<TestNode> testee;
    /** Convenience Iterable that gives access to testee's raw data. */
    private Iterable<TestNode> rawTestee;
    /** Next id to use in creating data. */
    int nextId = 0;

    @Before
    public void initialize() {
        testee = new FastList<TestNode>();
        rawTestee = new Iterable<TestNode>() {
            @Override
            public Iterator<TestNode> iterator() {
                return testee.rawIterator();
            }

        };
    }

    /* Single thread tests.*/
    
    
    /**
     * Test that a newly created list is both
     * logically and physically empty.
     */
    @Test
    public void emptyList() {
        /* Assert logically empty. */
        assertTrue(isEmpty(testee));
        /* Assert physically empty. */
        assertTrue(isEmpty(rawTestee));
    }

    /**
     * Test adding elements.
     */
    @Test
    public void simpleAdd() {
        final List<TestNode> elements = makeDataList(3);
        addElements(testee, elements);
        assertContainsInOrder(testee, elements);
        assertContainsInOrder(elements, testee);
    }

    /**
     * Add some elements, then check removing one.
     */
    @Test
    public void removeOneAndReap() {
        final List<TestNode> elements = makeDataList(3);
        addElements(testee, elements);
        final TestNode probe = elements.get(0);
        /* Probe should be removable. */
        assertTrue(testee.remove(probe));
        /* Probe should no longer be removable. */
        assertFalse(testee.remove(probe));
        /* reap() should remove it physically. */
        testee.reap();
        assertFalse(contains(rawTestee, probe));
        assertFalse(contains(testee, probe));
    }

    /**
     * Add some elements, then remove them all.
     */
    @Test
    public void removeAllAndReap() {
        final List<TestNode> elements = makeData(new int[] { 3 }).get(0);
        addElements(testee, elements);
        removeIfPresent(testee, elements, true, false);
        testee.reap();
        assertTrue(isEmpty(rawTestee));
        assertTrue(isEmpty(testee));
    }

    /**
     * Tests adding nodes from multiple threads, checking the state of the
     * FastList after the adds, then removing in parallel and again checking the
     * state.
     * 
     * @throws InterruptedException
     */
    @Test
    public void parallelAddThenRemove() throws InterruptedException {
        final int[] sizes = new int[] { 1000, 1000, 1000, 1000 };
        final List<List<TestNode>> elements = makeData(sizes);
        final CyclicBarrier barrier = new CyclicBarrier(elements.size());
        
        List<Thread> addThreads = getAddThreads(elements, barrier);
        startAll(addThreads);
        joinAll(addThreads);

        /*
         * Each thread's nodes should appear in testee in order, although the
         * interleaving between the threads is unpredictable.
         */
        for (List<TestNode> nodes : elements) {
            assertContainsInOrder(testee, nodes);
        }
        /*
         * The number of elements in the FastList should match the total of the
         * list sizes.
         */
        assertEquals(sum(sizes), count(testee));

        /* Now do the removes. */
        List<Thread> removeThreads = getRemoveThreads(elements, barrier);
        startAll(removeThreads);
        joinAll(removeThreads);
        
        /* After the remove, testee should be logically empty.*/
        assertTrue(isEmpty(testee));
        testee.reap();
        /* And a reap makes it also physically empty.*/
        assertTrue(isEmpty(rawTestee));
    }
    
    /**
     * Test adding and removing simultaneously.
     * @throws InterruptedException
     */
    @Test
    public void parallelAddWithRemove() throws InterruptedException {
        final int[] sizes = new int[] { 1000, 1000, 1000, 1000 };
        final List<List<TestNode>> elements = makeData(sizes);
        
        /* Require all the threads to meet at the barrier before
         * starting work - this creates maximum contention.
         */
        final CyclicBarrier barrier = new CyclicBarrier(2*elements.size());
        
        List<Thread> addThreads = getAddThreads(elements, barrier);
        List<Thread> removeThreads = getRemoveAllThreads(elements, barrier, 100);
        
        startAll(addThreads);        
        startAll(removeThreads);
        
        joinAll(addThreads);
        joinAll(removeThreads);
        
        /* After the remove, testee should be logically empty.*/
        assertTrue(isEmpty(testee));
        testee.reap();
        /* And a reap makes it also physically empty.*/
        assertTrue(isEmpty(rawTestee));
    }
    
   
    /* Utility methods.*/


    /**
     * Create an add Thread for each List<TestNode> in elements.
     * @param elements
     * @param barrier Each thread will wait at the barrier before starting work -
     * this ensures that the thread start at about the same time, rather than
     * being staggered by the time it takes each thread to start.
     * @return A List of the created Thread objects.
     */
    private List<Thread> getAddThreads(final List<List<TestNode>> elements,
            final CyclicBarrier barrier) {
        NodeListRunnableFactory factory = new NodeListRunnableFactory() {
            @Override
            public Runnable getRunnable(final List<TestNode> nodes) {
                return new Runnable() {
                    public void run() {
                        barrierWait(barrier);
                        addElements(testee, nodes);
                    }
                };
            }
        };

        return getThreads(factory, elements);

    }
    
    /**
     * Create a remove Thread for each List<TestNode> in elements
     * @param elements
     * @param barrier Each thread will wait at the barrier before starting work -
     * this ensures that the thread start at about the same time, rather than
     * being staggered by the time it takes each thread to start.
     * @return List of created Thread objects.
     */
    private List<Thread> getRemoveThreads(final List<List<TestNode>> elements,
            final CyclicBarrier barrier) {
        NodeListRunnableFactory factory = new NodeListRunnableFactory() {
            @Override
            public Runnable getRunnable(final List<TestNode> nodes) {
                return new Runnable() {
                    public void run() {
                        barrierWait(barrier);
                        removeIfPresent(testee, nodes, true, false);
                    }
                };
            }
        };
        return getThreads(factory, elements);
    }
    
    /**
     * Create a removall Thread for each List<TestNode> in elements
     * @param elements
     * @param barrier Each thread will wait at the barrier before starting work -
     * this ensures that the thread start at about the same time, rather than
     * being staggered by the time it takes each thread to start.
     * @param tries Maximum number of attempts to make to remove
     * the elements.
     * @return List of created Thread objects.
     */
    private List<Thread> getRemoveAllThreads(final List<List<TestNode>> elements,
            final CyclicBarrier barrier, final int tries) {
        NodeListRunnableFactory factory = new NodeListRunnableFactory() {
            @Override
            public Runnable getRunnable(final List<TestNode> nodes) {
                return new Runnable() {
                    public void run() {
                        barrierWait(barrier);
                        removeAll(testee, nodes, tries);
                    }
                };
            }
        };
        return getThreads(factory, elements);
    }

    /**
     * Turn barrier failures and unexpected interrupts into
     * JUnit failure.
     * @param barrier
     */
    private void barrierWait(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException e) {
            fail(e.getMessage());
        } catch (BrokenBarrierException e) {
            fail(e.getMessage());
        }
    }

    /**
     * Create a list of lists of data, giving each node a unique id.
     * 
     * @param sizes
     * @return
     */
    private List<List<TestNode>> makeData(int[] sizes) {
        List<List<TestNode>> result = new ArrayList<List<TestNode>>();
        for (int size : sizes) {
            List<TestNode> l = makeDataList(size);
            result.add(l);
        }
        return result;
    }

    /**
     * Make one list of data, giving each node a unique id.
     * 
     * @param size
     * @return
     */
    private List<TestNode> makeDataList(int size) {
        List<TestNode> l = new ArrayList<TestNode>();
        for (int i = 0; i < size; i++) {
            l.add(new TestNode(nextId));
            nextId++;
        }
        return l;
    }

    /**
     * Return true if, and only if, data is empty.
     * 
     * @param data
     * @return
     */
    private boolean isEmpty(Iterable<TestNode> data) {
        return !data.iterator().hasNext();
    }

    /**
     * Add the elements in source to testee, in source's iterator order.
     * 
     * @param testee
     * @param source
     */
    private void addElements(FastList<TestNode> testee,
            Iterable<TestNode> source) {
        for (TestNode node : source) {
            testee.add(node);
        }
    }

    /**
     * Assert that iterable1 contains all the elements of iterable2, in order.
     * iterable1 may contain additional elements not found in iterable2.
     * 
     * @param iterable1
     * @param iterable2
     */
    private void assertContainsInOrder(Iterable<TestNode> iterable1,
            Iterable<TestNode> iterable2) {
        Iterator<TestNode> it1 = iterable1.iterator();
        Iterator<TestNode> it2 = iterable2.iterator();
        while (it2.hasNext()) {
            TestNode target = it2.next();
            boolean targetFound = false;
            while (!targetFound && it1.hasNext()) {
                TestNode found = it1.next();
                if (found.equals(target)) {
                    targetFound = true;
                }
            }
            if (!targetFound) {
                fail("Missing node: " + target);
            }
        }

    }

    /**
     * Return true if, and only if, iterable contains the probe.
     * 
     * @param iterable
     * @param probe
     * @return
     */
    public boolean contains(Iterable<TestNode> iterable, TestNode probe) {
        for (TestNode found : iterable) {
            if (found.equals(probe)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Count the elements in an Iterable.
     * @param data
     * @return The number of elements.
     */
    private int count(Iterable<TestNode> data) {
        int count = 0;
        for (@SuppressWarnings("unused")
        TestNode node : data) {
            count++;
        }
        return count;
    }

    /**
     * Add up an int[]
     * @param data
     * @return The sum reduction of the data.
     */
    private int sum(int[] data) {
        int sum = 0;
        for (int i : data) {
            sum += i;
        }
        return sum;
    }

    /**
     * Remove from testee the elements of removees.
     * @param testee
     * @param removees
     * @param assertRemovable fail if an element cannot be removed.
     * @param assertNotRemovable fail if an element can be removed.
     * @return List, in iterator order, of the elements that could not be
     * removed.
     */
    private List<TestNode> removeIfPresent(FastList<TestNode> testee, Iterable<TestNode> removees,
            boolean assertRemovable, boolean assertNotRemovable) {
        List<TestNode> notRemoved = new ArrayList<TestNode>();
        for (TestNode node : removees) {
            try {
                boolean removable = testee.remove(node);
                if (removable) {
                    if (assertNotRemovable) {
                        fail("Could remove node " + node);
                    }
                } else {
                    notRemoved.add(node);
                    if (assertRemovable) {
                        fail("Could not remove node " + node);
                    }
                }
            } catch (IllegalArgumentException e) {
                notRemoved.add(node);
                if (assertRemovable) {
                    fail("Could not remove node " + node);
                }
            }
        }
        return notRemoved;
    }
    
    /**
     * Attempt to remove the elements in removees from testee, retrying as necessary
     * in case elements are added during or after the remove attempt.
     * @param testee
     * @param removees
     * @param tries The maximum number of attempts before failure.
     */
    private void removeAll(FastList<TestNode> testee, Iterable<TestNode> removees, int tries){
        Iterable<TestNode> target = removees;
        for(int i=0; i<tries; i++){
            List<TestNode> remainder = removeIfPresent(testee, target, false, false);
            if(remainder.isEmpty()){
                return;
            }
            target = remainder;
        }
        fail("Could not remove all entries "+target);
    }



    /**
     * Wait for all the threads to finish.
     * @param threads
     * @throws InterruptedException
     */
    private void joinAll(List<Thread> threads) throws InterruptedException {
        for (Thread thread : threads) {
            thread.join();
        }
    }

    /**
     * Start all the threads.
     * @param threads
     */
    private void startAll(List<Thread> threads) {
        for (Thread thread : threads) {
            thread.start();
        }
    }

    /**
     * Create a Thread for each List of nodes, using a Runnable generated by the
     * factory.
     * 
     * @param factory
     * @param nodeLists
     * @return The list of created Threads.
     */
    private List<Thread> getThreads(NodeListRunnableFactory factory,
            List<List<TestNode>> nodeLists) {
        List<Thread> threads = new LinkedList<Thread>();
        for (List<TestNode> list : nodeLists) {
            Thread thread = new Thread(factory.getRunnable(list));
            threads.add(thread);
        }
        return threads;
    }

    /**
     * TestNode is the node class used in all the tests. 
     *
     */
    private static class TestNode extends FastList.Node {
        @Override
        public String toString() {
            return "TestNode [id=" + getId() + ", toString()="
                    + super.toString() + "]";
        }

        int id;

        private TestNode(int id) {
            this.id = id;
        }

        private int getId() {
            return id;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + id;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            TestNode other = (TestNode) obj;
            if (id != other.id)
                return false;
            return true;
        }

    }

    /**
     * Factory for production of Runnable objects to operate on a TestNode list.
     */
    private interface NodeListRunnableFactory {
        Runnable getRunnable(List<TestNode> nodes);
    }

}
