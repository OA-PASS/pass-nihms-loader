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

import java.io.BufferedReader;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import org.dataconservancy.pass.model.Submission.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NIHMS CSV Reader expects a Submissions CSV from the NIHMS site and will read it into a List,
 * mapping the relevant fields to the NihmsPublication model.
 * @author Karen Hanson
 * @version $Id$
 */
public class NihmsCsvProcessor {
 
    private static final Logger LOG = LoggerFactory.getLogger(NihmsCsvProcessor.class);
    
    /**
     * Heading used for the PMID column in the CSV
     */
    private static final String PMID_HEADING = "PMID";
    
    /**
     * Column number for PMID in the CSV
     */
    private static final Integer PMID_COLNUM = 0;

    /**
     * Heading used for the Grant ID column in the CSV
     */
    private static final String GRANTID_HEADING = "Grant number";

    /**
     * Column number for Grant ID in the CSV
     */
    private static final Integer GRANTID_COLNUM = 3;
    
    /**
     * Lists expected headers and their column number to support header validation
     */
    private static final Map<Integer, String> EXPECTED_HEADERS = new HashMap<Integer, String>();
    static {
        EXPECTED_HEADERS.put(PMID_COLNUM, PMID_HEADING);
        EXPECTED_HEADERS.put(GRANTID_COLNUM, GRANTID_HEADING);
    }
    
    /**
     * Counter for number of records processed so far
     */
    private int recCount = 0;

    /**
     * Counter for number of records that failed so far
     */
    private int failCount = 0;
    
    /**
     * Path to NIHMS CSV to read in
     */
    private Path filePath =  null;
    
    /**
     * Status of submission to pass to NihmsPublication
     */
    private Status status = null;    
    
    
    
    public NihmsCsvProcessor(Path filePath) {
        this.filePath = filePath;
        this.status = submissionStatus(filePath);
    }
       
    /**
     * Cycles through the CSV that is loaded, converting to a NihmsPublication, and then
     * using the consumer provided to process the record
     * @param pubConsumer
     */
    public void processCsv(Consumer<NihmsPublication> pubConsumer) {
        
        try(BufferedReader br=Files.newBufferedReader(filePath)) {
            
            LOG.info("Starting to process file: {}", filePath);

            Iterator<CSVRecord> csvRecords =  CSVFormat.DEFAULT.parse(br).iterator();

            CSVRecord record = csvRecords.next(); 
            if (!hasValidHeaders(record)){
                LOG.error("File at path \"{}\" has unrecognized headers", filePath.toString());  
                throw new RuntimeException("The headers were not as expected, aborting import");                  
            }
                        
            csvRecords.forEachRemaining(row -> consumeRow(row, pubConsumer));
            
        } catch (Exception e){ 
            String msg = String.format("A problem occurred while processing the csv with path %s" + filePath.toString());
            throw new RuntimeException(msg, e);            
        } 

        LOG.info("{} records were processed with {} failures.", recCount, failCount);
        
    }
        
    /**
     * Converts Row to a NihmsPublication object and passes it the consumer provided
     * @param row
     * @param rowConsumer
     */
    private void consumeRow(CSVRecord row, Consumer<NihmsPublication> pubConsumer) {
        if (row==null) {return;}
        recCount = recCount + 1;  
        try {
            NihmsPublication pub = new NihmsPublication(row.get(PMID_COLNUM), row.get(GRANTID_COLNUM), status);
            pubConsumer.accept(pub);                   
        }
        catch (Exception ex) {
            failCount = failCount + 1;
            LOG.error("A problem occurred while processing csv row {}. The record was not imported successfully.", recCount, ex);
        }
    }
    
    /**
     * Validates that the headers in the spreadsheet match what is expected before retrieving data from them
     * This will go through all headers even if the first one is not valid so that all issues with headers are
     * logged as errors before exiting
     * @param headers
     * @return
     */
    private boolean hasValidHeaders(CSVRecord headers) {
        boolean valid = true;
        LOG.debug("Checking CSV headers match expected");
        for (Entry<Integer, String> header : EXPECTED_HEADERS.entrySet()) {
            Integer colNum = header.getKey();
            String expectedName = header.getValue();
            if (!headers.get(colNum).equals(expectedName)) {
                valid = false;
                LOG.error("Expected header \"{}\" but was \"{}\"", expectedName, headers.get(colNum));  
            }            
        }
        return valid;
    }
    

    /**
     * Cycles through Submission status types, and matches it to the filepath to determine
     * the status of the rows in the CSV file. If no match is found, an exception is thrown.
     * @param path
     * @return
     */
    private static Status submissionStatus(Path path) {
        String filename = path.getFileName().toString();
        
        for (Status status : Status.values()) {
            if (filename.startsWith(status.getValue())) {
                return status;
            }
          }
        throw new RuntimeException("Could not determine the Status of the publications being imported. Please ensure filenames are prefixed according to the Submission status.");
    }
    
    
}
