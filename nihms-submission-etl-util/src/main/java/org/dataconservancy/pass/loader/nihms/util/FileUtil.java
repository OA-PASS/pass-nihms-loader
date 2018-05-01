/*
 * Copyright 2018 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dataconservancy.pass.loader.nihms.util;

import java.io.File;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;

/**
 * Utility class to support directory/filepath processing
 * @author Karen Hanson
 */
public class FileUtil {

    
    private static final String NIHMS_CONFIG_DIR_PROPKEY = "nihms.config.dir";
    private static final String NIHMS_DOWNLOADS_DIR_PROPKEY = "nihms.downloads.dir";
    private static final String USER_DIR_PROPKEY = "user.dir";
    
    private static final String DEFAULT_DOWNLOAD_FOLDER = "/downloads";
    
    
    public static Path selectDownloadDirectory() {
        String currDirectoryPath = System.getProperty(USER_DIR_PROPKEY);
        String downloadFilePath = ConfigUtil.getSystemProperty(NIHMS_DOWNLOADS_DIR_PROPKEY, currDirectoryPath + DEFAULT_DOWNLOAD_FOLDER);
        Path downloadDirectory = new File(downloadFilePath).toPath();
        return downloadDirectory;
    }
        
    
    public static Path selectConfigDirectory() {
        String currDirectoryPath = System.getProperty(USER_DIR_PROPKEY);
        String configFilePath = ConfigUtil.getSystemProperty(NIHMS_CONFIG_DIR_PROPKEY, currDirectoryPath);
        Path configDirectory = new File(configFilePath).toPath();
        return configDirectory;
    }
    
    /**
     * If there is at least one path specified use the first one, but it must be a directory.
     * Additional directories will be ignored. If no directory was provided as an argument, 
     * look at system properties
     * @param candidates
     * @return
     */
    public static Path selectDirectory(String[] args) {
        List<Path> paths = commandLineFiles(args);
        Path directory = null;
        if (paths.size() > 0 && Files.isDirectory(paths.get(0))) {
            directory = paths.get(0);
        } else {
            //check sysprop
            String dir = ConfigUtil.getSystemProperty("dir", null);
            
            if (dir!=null && dir.length()>0) {
                Path path = Paths.get(dir);
                directory = (Files.isDirectory(path) ? path : null);
            }
        }
        return directory;
    }

    /**
     * Find directory paths in the list of arguments
     * @param args
     * @return
     */
    private static List<Path> commandLineFiles(String[] args) {
        return Arrays.asList(args).stream()
                .map(File::new)
                .filter(File::exists)
                .map(File::toPath)
                .collect(toList());
    }
    
    /**
     * Retrieve a list of files in a directory, filter by directory
     * @param directory
     * @return
     */
    public static List<Path> getCsvFilePaths(Path directory) {
        List<Path> filepaths = null;
        try {
            filepaths = Files.list(directory)
                .filter(FILTER_GENERAL)
                .filter(FILTER_CSV)
                .map(Path::toAbsolutePath)
                .collect(toList());
        } catch (Exception ex){
            throw new RuntimeException("A problem occurred while loading CSV file paths from " + directory.toString());
        }
        return filepaths;
    }
    

    /** 
     * Calculate filter based on whether there is a filter system property, and whether the file is appended 
     * with ".done" which signals the file was processed
     */
    private static Predicate<Path> FILTER_GENERAL = path  -> {
        PathMatcher pathFilter = p -> true;
        String filterProp = ConfigUtil.getSystemProperty("filter", null);
        if (filterProp != null) {
            pathFilter = FileSystems.getDefault().getPathMatcher("glob:" + filterProp);
        }
        return pathFilter.matches(path.getFileName());
    };


    /** 
     * Calculate filter based on whether there is a filter system property, and whether the file is appended 
     * with ".done" which signals the file was processed
     */
    private static Predicate<Path> FILTER_CSV = path  -> {
        return path.getFileName().toString().endsWith(".csv");
    };

    /**
     * Rename file to append ".done" once it has been processed
     * @param path
     */
    public static void renameToDone(Path path) {
        final File file = path.toFile();
        file.renameTo(new File(file.getAbsolutePath() + ".done"));
    }
}
