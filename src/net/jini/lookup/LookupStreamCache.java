/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.lookup;

import java.util.List;
import net.jini.core.lookup.ServiceItem;
import org.apache.river.api.util.ResultStream;

/**
 *
 * @author peter
 */
public interface LookupStreamCache extends LookupCache {
    
    public ResultStream<ServiceItem> lookup(ServiceItemFilter[] filters, int maxMatches);

}
