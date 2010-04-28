/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.core.lookup;

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
    public T get();
}
