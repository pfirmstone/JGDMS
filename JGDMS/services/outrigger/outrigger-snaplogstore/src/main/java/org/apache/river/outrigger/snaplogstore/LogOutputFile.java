/*
 * Copyright 2018 peter.
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
package org.apache.river.outrigger.snaplogstore;

import java.io.IOException;
import java.util.Observable;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.id.Uuid;
import org.apache.river.outrigger.LogOps;
import org.apache.river.outrigger.OutriggerServerImpl;
import org.apache.river.outrigger.proxy.StorableObject;
import org.apache.river.outrigger.proxy.StorableResource;
import org.apache.river.thread.NamedThreadFactory;

/**
 *
 * @author peter
 */
public class LogOutputFile implements LogOps {
    /** Logger for logging persistent store related information */
    private static final Logger logger = 
	Logger.getLogger(OutriggerServerImpl.storeLoggerName);
    private final LogOps logFile;
    private final ExecutorService executor;
    private final BlockingQueue<Runnable> queue;
    LogOutputFile(String basePath, int maxOps) throws IOException {
	this.logFile = new LogOutputFileImpl(basePath, maxOps);
	this.queue = new LinkedBlockingQueue<Runnable>();
	executor = new ThreadPoolExecutor(
	    1, 1,
	    0L, TimeUnit.MILLISECONDS,
	    queue,
	    new NamedThreadFactory(
		    "Outrigger Snaplogstore LogOutputFile",
		    false
	    ),
	    new ThreadPoolExecutor.AbortPolicy()
	);
	
    }

    @Override
    public void bootOp(long time, long sessionId) {
	executor.submit(() -> {
	    logFile.bootOp(time,sessionId);
	});
    }

    @Override
    public void joinStateOp(StorableObject state) {
	executor.submit(() -> {
	    logFile.joinStateOp(state);
	});
    }

    @Override
    public void writeOp(StorableResource entry, Long txnId) {
	executor.submit(() -> {
	    logFile.writeOp(entry, txnId);
	});
    }

    @Override
    public void writeOp(StorableResource[] entries, Long txnId) {
	executor.submit(() -> {
	    logFile.writeOp(entries, txnId);
	});
    }

    @Override
    public void takeOp(Uuid cookie, Long txnId) {
	executor.submit(() -> {
	    logFile.takeOp(cookie, txnId);
	});
    }

    @Override
    public void takeOp(Uuid[] cookies, Long txnId) {
	executor.submit(() -> {
	    logFile.takeOp(cookies, txnId);
	});
    }

    @Override
    public void registerOp(StorableResource registration, String type, StorableObject[] templates) {
	executor.submit(() -> {
	    logFile.registerOp(registration, type, templates);
	});
    }

    @Override
    public void renewOp(Uuid cookie, long expiration) {
	executor.submit(() -> {
	    logFile.renewOp(cookie, expiration);
	});
    }

    @Override
    public void cancelOp(Uuid cookie, boolean expired) {
	executor.submit(() -> {
	    logFile.cancelOp(cookie, expired);
	});
    }

    @Override
    public void prepareOp(Long txnId, StorableObject transaction) {
	executor.submit(() -> {
	    logFile.prepareOp(txnId, transaction);
	});
    }

    @Override
    public void commitOp(Long txnId) {
	executor.submit(() -> {
	    logFile.commitOp(txnId);
	});
    }

    @Override
    public void abortOp(Long txnId) {
	executor.submit(() -> {
	    logFile.abortOp(txnId);
	});
    }

    @Override
    public void uuidOp(Uuid uuid) {
	executor.submit(() -> {
	    logFile.uuidOp(uuid);
	});
    }
    
    void destroy() {
	executor.shutdownNow();
	boolean interrupted = false;
	if (!executor.isTerminated()){
	    try {
		executor.awaitTermination(30, TimeUnit.SECONDS);
	    } catch (InterruptedException ex) {
		logger.log(Level.INFO, "Interrupted during termination", ex);
		interrupted = true;
	    }
	}
	if (interrupted) Thread.currentThread().interrupt();
	((LogOutputFileImpl) logFile).destroy();
	logger.log(Level.INFO, "Log destroyed");
    }
    
    void close() throws IOException {
	//Poison pill.
	executor.submit(() -> {
	    try {
		((LogOutputFileImpl)logFile).close();
	    } catch (IOException ex) {
		logger.log(Level.WARNING, "Unable to close LogFile", ex);
	    }
	});
	executor.shutdown();
	boolean interrupted = false;
	while (!executor.isTerminated()){
	    try {
		executor.awaitTermination(2, TimeUnit.MINUTES);
	    } catch (InterruptedException ex) {
		logger.log(Level.INFO, "Interrupted during termination", ex);
		interrupted = true;
	    }
	}
	if (interrupted) Thread.currentThread().interrupt();
    }
    
    Observable observable() {
	return ((LogOutputFileImpl) logFile).observable();
    }
    
}
