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

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.dataconservancy.pass.client.nihms.NihmsPassClientService;
import org.dataconservancy.pass.entrez.PmidLookup;
import org.dataconservancy.pass.loader.nihms.model.NihmsPublication;
import org.dataconservancy.pass.loader.nihms.model.NihmsStatus;
import org.dataconservancy.pass.loader.nihms.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.dataconservancy.pass.loader.nihms.util.ProcessingUtil.nullOrEmpty;

/**
 * Service that takes a filepath, gets the csvs there, and transforms/loads the data according to a 
 * list of statuses to be processed.
 * @author Karen Hanson
 */
public class NihmsTransformLoadService {

    private static Logger LOG = LoggerFactory.getLogger(NihmsTransformLoadService.class);
    
    private NihmsPassClientService nihmsPassClient;
    
    private PmidLookup pmidLookup;
    
    public NihmsTransformLoadService() {
        nihmsPassClient = new NihmsPassClientService();
        pmidLookup = new PmidLookup();
    }

    /**
     * Option to inject dependencies
     * @param passClientService
     * @param pmidLookup
     */
    public NihmsTransformLoadService(NihmsPassClientService passClientService, PmidLookup pmidLookup) {
        this.nihmsPassClient=passClientService;
        this.pmidLookup=pmidLookup;
    }
    
    
    /**
     * Goes through list of files in directory specified and processes those that have a NihmsStatus 
     * that matches a row in statusesToProcess. If statuseseToProcess is null/empty, it will process all statuses
     * 
     * @param statusesToProcess if null or empty, all statuses will be processed.
     */
    public void transformAndLoadFiles(Set<NihmsStatus> statusesToProcess) {
        File dataDirectory = FileUtil.getDataDirectory();
        if (dataDirectory==null) {
            throw new RuntimeException("dataDirectory cannot be empty");
        }
        if (nullOrEmpty(statusesToProcess)) {
            statusesToProcess = new HashSet<NihmsStatus>();
            statusesToProcess.addAll(EnumSet.allOf(NihmsStatus.class));
        }

        List<Path> filepaths = loadFiles(dataDirectory);
         
        Consumer<NihmsPublication> pubConsumer = pub -> transformAndLoadNihmsPub(pub);
        int count = 0;
        for (Path path : filepaths) {    
            NihmsStatus nihmsStatus = nihmsStatus(path);
            if (statusesToProcess.contains(nihmsStatus)) {
                NihmsCsvProcessor processor = new NihmsCsvProcessor(path, nihmsStatus);
                processor.processCsv(pubConsumer);
                FileUtil.renameToDone(path);
                count = count +1;
            }
        }
        if (count>0) {
            LOG.info("Transform and load complete. Processed {} files", count);            
        } else {
            LOG.info("Transform and load complete. No files matched the statuses provided");
        }
    }
    
    /**
     * Takes pub record from CSV loader, transforms it then passes transformed record to the 
     * loader. Note that exceptions should not be caught here, they should be caught by CSV processor which
     * tallies the successes/failures.
     * @param pub
     */
    private void transformAndLoadNihmsPub(NihmsPublication pub) {
        NihmsPublicationToSubmission transformer = new NihmsPublicationToSubmission(nihmsPassClient, pmidLookup);
        SubmissionDTO transformedRecord = transformer.transform(pub);
        SubmissionLoader loader = new SubmissionLoader(nihmsPassClient);
        loader.load(transformedRecord);
    }

    /**
     * Checks directory provided and attempts to load a list of files to process
     * @param downloadDirectory
     */
    private List<Path> loadFiles(File downloadDirectory) {
        List<Path> filepaths = null;
        try {
            filepaths = FileUtil.getCsvFilePaths(downloadDirectory.toPath());
        } catch (Exception e) {
            throw new RuntimeException(String.format("A problem occurred while loading file paths from %s", downloadDirectory.toString()),e);
        }
        if (nullOrEmpty(filepaths)) {
            throw new RuntimeException(String.format("No file found to process at path %s", downloadDirectory.toString()));
        }  
        return filepaths;
    }

    /**
     * Cycles through Submission status types, and matches it to the filepath to determine
     * the status of the rows in the CSV file. If no match is found, an exception is thrown.
     * @param path
     * @return
     */
    private static NihmsStatus nihmsStatus(Path path) {
        String filename = path.getFileName().toString();
        
        for (NihmsStatus status : NihmsStatus.values()) {
            if (filename.startsWith(status.toString())) {
                return status;
            }
          }
        throw new RuntimeException("Could not determine the Status of the publications being imported. Please ensure filenames are prefixed according to the Submission status.");
    }
    
    
    

}
