/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.imp.util;

import java.util.Set;
import java.util.concurrent.RunnableFuture;

/**
 * A PriorityHandler when given a runnable returns all runnable tasks that
 * must be run first.
 * 
 * The PriorityHandler may not know all the tasks it needs until it has been asked, it
 * may delay by invoking wait() until it is able to determine or retrieve all preceeding
 * tasks.
 * 
 * @author Peter Firmstone.
 */
public interface PriorityHandler<T extends Runnable> {
    public Set<RunnableFuture> getPreceedingTasks(T task);
}
