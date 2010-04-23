/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.discovery;

/**
 *
 * @author Peter Firmstone.
 */
public interface DiscoveryListenerManagement {

    /**
     * Adds an instance of <code>DiscoveryListener</code> to the set of
     * objects listening for discovery events. Once the listener is
     * registered, it will be notified of all lookup services discovered
     * to date, and will then be notified as new lookup services are
     * discovered or existing lookup services are discarded.
     * <p>
     * If <code>null</code> is input to this method, a
     * <code>NullPointerException</code> is thrown. If the listener
     * input to this method duplicates (using the <code>equals</code>
     * method) another element in the current set of listeners, no action
     * is taken.
     *
     * @param listener an instance of <code>DiscoveryListener</code>
     * corresponding to the listener to add to the set of
     * listeners.
     *
     * @throws java.lang.NullPointerException if <code>null</code> is
     * input to the <code>listener</code> parameter
     *
     * @see #removeDiscoveryListener
     * @see net.jini.discovery.DiscoveryListener
     */
    void addDiscoveryListener(DiscoveryListener listener);

    /**
     * Removes a listener from the set of objects listening for discovery
     * events. If the listener object input to this method does not exist
     * in the set of listeners, then this method will take no action.
     *
     * @param listener an instance of <code>DiscoveryListener</code>
     * corresponding to the listener to remove from the set
     * of listeners.
     *
     * @see #addDiscoveryListener
     * @see net.jini.discovery.DiscoveryListener
     */
    void removeDiscoveryListener(DiscoveryListener listener);

    /**
     * Ends all discovery processing being performed by the current
     * implementation of this interface. After this method is invoked,
     * no new lookup services will be discovered, and the effect of any
     * new operations performed on the current implementation object are
     * undefined. Any additional termination semantics must be defined
     * by the implementation class itself.
     */
    void terminate();

}
