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

import com.vivek.utils.resource.ResorceFinder;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author Vivek Kumar <vivek43nit@gmail.com>
 */
public class ConfigParser<T> {
    private Map<String, ConfigParserInterface<T>> parsers;
    private T cache;
    private boolean isCompleted;

    //access of this class constuctor should be only to this package
    ConfigParser() {
        parsers = new LinkedHashMap<String, ConfigParserInterface<T>>();
        cache = null;
        isCompleted = false;
    }
    
    //restrict access of this function only current package
    void registerParser(ConfigParserInterface<T> parser){
        parsers.put(parser.getSupportedExtension(), parser);
    }
    
    public synchronized T parse(String fileNameWithoutExtensionName) throws FileNotFoundException, ConfigParsingError{
        if(isCompleted){
            return cache;
        }
        for(String extension : parsers.keySet()){
            File file = ResorceFinder.getFile(parsers.get(extension).getApplicationName(), fileNameWithoutExtensionName+"."+extension);
            if(file != null){
                System.out.println("Loading File from : "+file.getAbsolutePath());
                cache = parsers.get(extension).parse(file);
                isCompleted = true;
                return cache;
            }
        }
        throw new FileNotFoundException("File '"+fileNameWithoutExtensionName+"' not found for any supported extensions : "+parsers.keySet());
    }
}
