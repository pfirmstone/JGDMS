/*
 *  DemoServiceImpl.java
 * 
 */

package org.apache.river.examples.federation;

import java.rmi.RemoteException;
import org.apache.river.federation.ServiceClass;

/**
 *
 */
@ServiceClass(name="DemoService")
public class DemoServiceImpl
    implements DemoService
{

    @Override
    public String hello(String msg)
            throws RemoteException
    {
        return "hello back: " + msg ;
    }
    
}
