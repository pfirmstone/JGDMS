/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.api.util;

/**
 * This interface is similar to an Enumerator, it is designed to return
 * results incrementally in loops, however unlike an Enumerator, there is no
 * check first operation as implementors must return a null value after
 * the backing data source has been exhausted. So this terminates like a stream
 * by returning a null value.
 * 
 * @author Peter Firmstone
 */
public interface ResultStream<T> {
    /**
     * Get next T, call from a loop until T is null;
     * @return T unless end of stream in which case null is returned.
     */
    public T get();
    /**
     * Close the result stream, this allows the implementer to close any
     * resources prior to deleting reference.
     */
    public void close();
}
