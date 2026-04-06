/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.vivek.utils.resource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 *
 * @author root
 */
public class ResorceFinder {
    private static final String[] possiblePathsInPriority = {
        "/etc/${app}/",
        System.getProperty("user.home") + "/${app}/.",
        System.getProperty("user.home") + "/.",
        ""
    };

    public static File getFile(String applicationName, String fileName){
        File f = null;
        for(String path : possiblePathsInPriority){
            f = new File(path.replace("${app}", applicationName) + fileName);
            if (f.canRead() && f.exists()) {
                System.out.println("Loading File from : " + f.getAbsolutePath());
                return f;
            }
        }
        // Fall back to classpath — works both in exploded dirs and inside fat JARs
        URL fileUrl = ResorceFinder.class.getClassLoader().getResource(fileName);
        if (fileUrl == null) {
            return null;
        }
        if ("file".equals(fileUrl.getProtocol())) {
            f = new File(fileUrl.getFile());
            if (f.exists()) {
                System.out.println("Loading File from : " + f.getAbsolutePath());
                return f;
            }
        } else {
            // Running inside a JAR — extract resource to a temp file
            String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
            try {
                File tmp = File.createTempFile("sql-storm-config-", ext);
                tmp.deleteOnExit();
                try (InputStream is = fileUrl.openStream();
                     FileOutputStream fos = new FileOutputStream(tmp)) {
                    is.transferTo(fos);
                }
                System.out.println("Loading File from classpath (extracted): " + fileName);
                return tmp;
            } catch (IOException e) {
                System.err.println("Failed to extract classpath resource: " + fileName + " — " + e.getMessage());
            }
        }
        return null;
    }
}
