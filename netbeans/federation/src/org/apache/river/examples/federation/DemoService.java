/*
 *  DemoService.java
 * 
 *  Created on 20-Sep-2012 14:45:39 by sim
 * 
 */

package org.apache.river.examples.federation;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 *
 * @author sim
 */
public interface DemoService
    extends Remote
{
    public String hello( String msg ) throws RemoteException ;
}
