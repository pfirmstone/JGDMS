/*
 *  DemoServiceImpl.java
 * 
 *  Created on 20-Sep-2012 14:49:17 by sim
 * 
 */

package org.apache.river.examples.federation;

import java.rmi.RemoteException;

/**
 *
 * @author sim
 */
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
