/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tests.support;

import java.io.Serializable;
import java.util.Comparator;

/**
 *
 * 
 */
public class StringComparator implements Comparator<String>, Serializable {
    private static final long serialVersionUID = 1L;
    
    public int compare(String o1, String o2) {
        return o1.compareTo(o2);
    }
}
