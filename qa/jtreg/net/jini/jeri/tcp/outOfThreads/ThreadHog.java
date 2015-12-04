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
import org.apache.river.thread.Executor;

class ThreadHog {
    private final Executor executor;
    private final Object lock = new Object();
    private int waiters = 0;
    private int released = 0;

    ThreadHog(Executor executor) {
	this.executor = executor;
    }

    void hogThreads(int n) {
	synchronized (lock) {
	    for (int i = 0; i < n; i++) {
		executor.execute(new Waiter(), "waiter");
		waiters++;
	    }
	}
    }

    void hogAllThreads() {
	synchronized (lock) {
	    try {
		while (true) {
		    executor.execute(new Waiter(), "waiter");
		    waiters++;
		}
	    } catch (OutOfMemoryError e) {
		System.err.println("reached " + waiters + " waiters");
	    }
	}
    }

    void release(int n) {
	synchronized (lock) {
	    released = Math.min(released + n, waiters);
	    lock.notifyAll();
	}
    }

    void releaseAll() {
	synchronized (lock) {
	    released = waiters;
	    lock.notifyAll();
	}
    }

    private class Waiter implements Runnable {
	Waiter() { }
	public void run() {
	    synchronized (lock) {
		while (released == 0) {
		    try {
			lock.wait();
		    } catch (InterruptedException ignore) {
		    }
		}
		released--;
		waiters--;
	    }
	}
    }
}
