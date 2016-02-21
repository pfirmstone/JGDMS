/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.export;

import java.io.IOException;
import net.jini.core.lookup.ServiceID;

/**
 *
 * @author peter
 */
public interface ServiceIDAccessor {
    
    ServiceID serviceID() throws IOException;
    
}
