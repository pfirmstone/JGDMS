/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.api.util;

/**
 * @author Peter Firmstone.
 * @since 2.2.0
 */
public interface Facade<T> {
    public T reveal();
    /**
     * Equals must be implemented to check against another Facade by utilising
     * reveal, so the underlaying parameter objects can be compared.
     * It must not return true if comparing the Facade against a
     * non-Facade object, otherwise the equals contract would not be symmetrical
     * and would violate the equals contract.
     * 
     * A Facade must not be allowed to contain another Facade implementation as
     * this too would voilate the equals contract.  The constructor should throw
     * an IllegalArgumentException.
     * 
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o);
    /**
     * The hashcode from T.hashCode();
     * @return hashCode for underlying parameter T.
     */
    @Override
    public int hashCode();
}
