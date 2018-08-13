/*
 * The MIT License
 *
 * Copyright 2018 Vivek Kumar <vivek43nit@gmail.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.vivek.utils.parser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Vivek Kumar <vivek43nit@gmail.com>
 */
public class ConfigParserFactory {
    
    private static Map<Class, ConfigParser> configParsers = new ConcurrentHashMap<Class, ConfigParser>();
    
    public synchronized static <T> void registerParser(Class<T> classType, ConfigParserInterface<T> parser){
        ConfigParser<T> p = configParsers.get(classType);
        if(p == null){
            p = new ConfigParser<T>();
            configParsers.put(classType, p);
        }
        p.registerParser(parser);
    }
    
    public static <T> ConfigParser<T> getParser(Class<T> classType) throws NoParserRegistered{
        if(!configParsers.containsKey(classType)){
            throw new NoParserRegistered("No parser registered for class type : "+classType.getCanonicalName());
        }
        return configParsers.get(classType);
    }
}
