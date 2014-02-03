/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.impl.thread;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import org.apache.river.api.util.FutureObserver;

/**
 *
 * @author peter
 */
public class DependencyLinker implements FutureObserver {
    private final ExecutorService executor;
    private final List<ObservableFuture> tasks;
    private final RunnableFuture dependant;

    public DependencyLinker(ExecutorService ex, List<ObservableFuture> tasks, RunnableFuture dep) {
        executor = ex;
        this.tasks = new ArrayList<ObservableFuture>(tasks);
        dependant = dep;
    }

    public synchronized void register() {
        Iterator<ObservableFuture> it = tasks.iterator();
        while (it.hasNext()) {
            it.next().addObserver(this);
        }
    }

    @Override
    public synchronized void futureCompleted(Future e) {
        tasks.remove(e);
        if (tasks.isEmpty()) {
            executor.submit(dependant);
        }
    }
    
}
