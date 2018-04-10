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

import org.dataconservancy.pass.client.nihms.NihmsPassClientService;
import org.dataconservancy.pass.entrez.PmidLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates loading of csv files and passing into load and transform routine
 * @author Karen Hanson
 */
public class NihmsSubmissionLoaderApp {

    private static Logger LOG = LoggerFactory.getLogger(NihmsSubmissionLoaderApp.class);

    private PmidLookup pmidLookup;
    private NihmsPassClientService passClientService;
    
    public NihmsSubmissionLoaderApp() {
        this.pmidLookup = new PmidLookup();
        this.passClientService = new NihmsPassClientService();
    }
    
    public NihmsSubmissionLoaderApp(PmidLookup pmidLookup, NihmsPassClientService passClientService) {
        this.pmidLookup = pmidLookup;
        this.passClientService = passClientService;        
    }
    
    /**
     * @param directory
     */
    public void run(Path directory) {
        
        List<Path> filePaths = null;
        try {
            filePaths = FileUtil.getFilePaths(directory);
        } catch (Exception e) {
            LOG.error("A problem occurred while loading file paths from {}", directory.toString(), e);
            System.exit(0);
        }

        if (filePaths.size() == 0) {
            LOG.warn("No CSV files found at path {}", directory);
            System.exit(0);
        }
        
        Consumer<NihmsPublication> pubConsumer = pub -> transformAndLoad(pub);
        
        for (Path path : filePaths) {           
            NihmsCsvProcessor processor = new NihmsCsvProcessor(path);
            processor.processCsv(pubConsumer);
            FileUtil.renameToDone(path);            
        }
    }
        
    /**
     * Takes pub record from CSV loader, transforms it then passes transformed record to the 
     * loader. Note that exceptions should not be caught here, they should be caught by CSV processor which
     * tallies the successes/failures.
     * @param pub
     */
    private void transformAndLoad(NihmsPublication pub) {
        SubmissionTransformer transformer = new SubmissionTransformer(passClientService, pmidLookup);
        NihmsSubmissionDTO transformedRecord = transformer.transform(pub);
        SubmissionLoader loader = new SubmissionLoader(passClientService);
        loader.load(transformedRecord);
    }
    
    
}
