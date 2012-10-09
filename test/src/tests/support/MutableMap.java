/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tests.support;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * 
 */
public class MutableMap<K,V> extends AbstractMap<K,V> {
    private Set<Entry<K,V>> entrySet;
    public MutableMap(){
        entrySet = new TreeSet<Entry<K,V>>(new Compare<K,V>());
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return entrySet;
    }
    
    public V put(K key, V value) {
	Entry<K,V> e = new SimpleEntry<K,V>(key, value);
        V oldVal = null;
        Iterator<Entry<K,V>> i = entrySet.iterator();
        while (i.hasNext()){
            Entry<K,V> en = i.next();
            if ( e.getKey().equals(key)){
                i.remove();
                oldVal = e.getValue();
                break;
            }
        }
        entrySet.add(e);
        return oldVal;
    }
    
    /**
     * This class prevents duplicate keys from being added to the underlying
     * set.
     * @param <K>
     * @param <V> 
     */
    private static class Compare<K,V> implements Comparator<Entry<K,V>> {

        @Override
        public int compare(Entry<K, V> o1, Entry<K, V> o2) {
            K key1 = o1.getKey();
            K key2 = o2.getKey();
            if (key1 instanceof Comparable && key2 instanceof Comparable){
                return ((Comparable) key1).compareTo(key2);
            }
            if ( key1.hashCode() < key2.hashCode()) return -1;
            if ( key1.hashCode() == key2.hashCode()) return 0;
            return 1;
        }

       
    }
}
