/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.vivek.utils.resource;

import java.io.File;
import java.net.URL;

/**
 *
 * @author root
 */
public class ResorceFinder {
    private static final String[] possiblePathsInPriority = {
        "/etc/${app}/",
        "~/${app}/.",
        "~/.",
        ""
    };
    
    public static File getFile(String applicationName, String fileName){
        File f = null;
        for(String path : possiblePathsInPriority){
            f = new File(path.replace("${app}", applicationName)+fileName);
            if (f.canRead() && f.exists()) {
                return f;
            }
        }
        URL fileUrl = ResorceFinder.class.getClassLoader().getResource(fileName);
        if(fileUrl == null){
            return null;
        }
        f = new File(fileUrl.getFile());
        if (f.exists()) {
            return f;
        } else {
            return null;
        }
    }
}
