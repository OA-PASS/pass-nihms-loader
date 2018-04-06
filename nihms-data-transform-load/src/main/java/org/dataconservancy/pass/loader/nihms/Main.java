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

import java.io.File;

import java.nio.file.Path;

import java.util.List;
import java.util.function.Consumer;

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
            directory = FileUtil.selectDirectory(args);
            if (directory == null) {
                File downloadDir = new File(System.getProperty("user.dir") + "/downloads");
                directory = downloadDir.toPath();
                LOG.warn("No directory indicated either as an argument or using the \"dir\" system property, defaulting to %s", downloadDir.getAbsolutePath());
            }
            filePaths = FileUtil.getFilePaths(directory);
        } catch (Exception e) {
            LOG.error("A problem occurred while loading file paths from {}", directory.toString(), e);
        }
        
        Consumer<NihmsPublication> pubConsumer = pub -> transformAndLoad(pub);

        for (Path path : filePaths) {           
            NihmsCsvProcessor processor = new NihmsCsvProcessor(path);
            processor.processCsv(pubConsumer);
            FileUtil.renameToDone(path);            
        }
    }
    
    private static void transformAndLoad(NihmsPublication pub) {
        try  {    
            SubmissionTransformer transformer = new SubmissionTransformer();
            NihmsSubmissionDTO transformedRecord = transformer.transform(pub);
            SubmissionLoader loader = new SubmissionLoader();
            loader.load(transformedRecord);
            
        } catch (Exception ex){
            //catch any exceptions and goto next
            LOG.error("Error during transform and load of record with pmid {}.", pub.getPmid());            
        }
    }
    
    
}
