/**
 * 
 */
package org.apache.river.test.share;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.discovery.DiscoveryChangeListener;
import net.jini.discovery.DiscoveryEvent;

/**
 * Listener for testing notify code resiliance in
 * the face of listener exceptions. Each of the event
 * handling methods throws an exception.
 */
public class BadTestListener implements DiscoveryChangeListener {
    protected final Logger logger;

    public BadTestListener(Logger logger) {
        this.logger = logger;
    }

    public void discarded(DiscoveryEvent e) {
        logger.log(Level.FINEST,
                "BadTestListener.discarded about to throw exception");
        throw new BadListenerException("Discarded");

    }

    public void discovered(DiscoveryEvent e) {
        logger.log(Level.FINEST,
                "BadTestListener.discovered about to throw exception");
        throw new BadListenerException("Discovered");
    }

    public void changed(DiscoveryEvent e) {
        logger.log(Level.FINEST,
                "BadTestListener changed about to throw exception");
        throw new BadListenerException("Changed");
    }

    public static class BadListenerException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public BadListenerException(String message) {
            super(message);
        }
    }
}