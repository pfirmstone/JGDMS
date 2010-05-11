/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.lookup;

import org.apache.river.api.util.ResultStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.jini.core.lookup.ServiceItem;

/**
 * A Filter utility class designed to filter out unwanted results.  Filters can
 * be daisy chained with pre prepared filters to perform logical AND operations.
 * 
 * Logical OR operations can be simulated by providing multiple filters in
 * constructors.
 * 
 * Any references to ServiceResultStreamFilter should be set to null 
 * immediately after filtering.
 * New instances can be created as required.
 * 
 * @author Peter Firmstone.
 */
public class ServiceResultStreamFilter implements ResultStream<ServiceItem> {
    private final List<ServiceItemFilter> filters;
    private final ResultStream<ServiceItem> inputResultStream;
    
    public ServiceResultStreamFilter(ResultStream<ServiceItem> rs,
            ServiceItemFilter[] sf){
        inputResultStream = rs;
        filters = new ArrayList<ServiceItemFilter>(sf.length);
        filters.addAll(Arrays.asList(sf));
    }

    public ServiceItem get() {
        for(ServiceItem item = inputResultStream.get(); item != null; 
                item = inputResultStream.get()) {
            int l = filters.size();
            for ( int i = 0; i < l; i++){
                ServiceItemFilter filter = filters.get(i);
                if(filter == null)  continue;
                if( filter.check(item) )  return item;
            }// end filter loop
        }//end item loop
        return null; // Our stream terminated item was null;
    }

    public void close() {
        inputResultStream.close();
    }
}
