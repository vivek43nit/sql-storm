/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.vivek.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Vivek
 */
public class MultiMap<K, V> {
    
    private final Map<K, ArrayList<V>> map = new HashMap<K, ArrayList<V>>();

    public void put(K key, V value)
    {
        if (!map.containsKey(key))
        {
            map.put(key, new ArrayList<V>());
        }
        map.get(key).add(value);
    }

    public ArrayList<V> get(K key)
    {
        return map.get(key);
    }

    public void clear()
    {
        map.clear();
    }

    public boolean containsKey(K key)
    {
        return map.containsKey(key);
    }
    
    public Set<K> keySet(){
        return map.keySet();
    }
}
