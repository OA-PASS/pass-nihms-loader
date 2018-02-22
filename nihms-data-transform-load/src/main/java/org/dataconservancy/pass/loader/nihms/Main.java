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
package org.dataconservancy.pass.loader.nihms;

import java.nio.file.Path;

import java.util.List;
import java.util.function.Consumer;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.fedora.FedoraPassClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Karen Hanson
 * @version $Id$
 */
public class Main {

    private static Logger LOG = LoggerFactory.getLogger(Main.class);
    
    /**
     * @param args
     */
    public static void main(final String[] args) {
        
        Path directory = null;
        List<Path> filePaths = null;
        try {
            directory = PathUtils.selectDirectory(args);
            if (directory == null) {
                throw new RuntimeException("No directory specified, please indicate which directory using the \"-Ddir\" property");
            }
            filePaths = PathUtils.getFilePaths(directory);
        } catch (Exception e) {
            LOG.error("A problem occurred while loading file paths from {}", directory.toString(), e);
        }
        
        PassClient client = new FedoraPassClient();
        NihmsLoader loader = new NihmsLoader(client);
        
        Consumer<NihmsPublication> pubConsumer = pub -> loader.transformAndLoad(pub);

        for (Path path : filePaths) {
           
            NihmsCsvProcessor processor = new NihmsCsvProcessor(path);
            processor.processCsv(pubConsumer);
            PathUtils.renameToDone(path);
            
        }
        
    }
    
}
