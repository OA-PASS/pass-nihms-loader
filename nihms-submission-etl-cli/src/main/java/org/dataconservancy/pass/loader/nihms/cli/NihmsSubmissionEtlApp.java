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
package org.dataconservancy.pass.loader.nihms.cli;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.dataconservancy.pass.client.nihms.NihmsPassClientService;
import org.dataconservancy.pass.client.util.ConfigUtil;
import org.dataconservancy.pass.entrez.PmidLookup;
import org.dataconservancy.pass.loader.nihms.NihmsCsvProcessor;
import org.dataconservancy.pass.loader.nihms.NihmsPublication;
import org.dataconservancy.pass.loader.nihms.NihmsStatus;
import org.dataconservancy.pass.loader.nihms.NihmsSubmissionDTO;
import org.dataconservancy.pass.loader.nihms.SubmissionLoader;
import org.dataconservancy.pass.loader.nihms.SubmissionTransformer;
import org.dataconservancy.pass.loader.nihms.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates loading of csv files and passing into load and transform routine
 * @author Karen Hanson
 */
public class NihmsSubmissionEtlApp {

    private static Logger LOG = LoggerFactory.getLogger(NihmsSubmissionEtlApp.class);

    private PmidLookup pmidLookup;
    private NihmsPassClientService passClientService;
    
    private String startDate;
    
    private Set<NihmsStatus> statusesToProcess;
    
    
    public NihmsSubmissionEtlApp(Set<NihmsStatus> statusesToProcess, String startDate, PmidLookup pmidLookup, NihmsPassClientService passClientService) {
        this.statusesToProcess = statusesToProcess;
        this.startDate = startDate;
        this.pmidLookup = pmidLookup;
        this.passClientService = passClientService;        
    }

    public NihmsSubmissionEtlApp(Set<NihmsStatus> statusesToProcess, String startDate) {
        this(statusesToProcess, startDate, new PmidLookup(), new NihmsPassClientService());
    }
    
    
    /**
     * @param directory
     */
    public void run() throws NihmsSubmissionEtlException {
        //properties and downloads folders will default to current if environment variable/system variable not supplied
        String currDirectoryPath = System.getProperty("user.dir");
        String configFilePath = ConfigUtil.getSystemProperty("nihms.config.dir", currDirectoryPath);
        String downloadFilePath = ConfigUtil.getSystemProperty("nihms.downloads.dir", currDirectoryPath + "/downloads");
        
        Path configDirectory = FileUtil.selectConfigDirectory();
        if (!Files.isDirectory(configDirectory)) {
            LOG.error("Directory \"{}\" does not exist. Please provide a valid path for the config files using the \"nihms.config.dir\" property.", configFilePath);
            System.exit(0);
        }
        
        Path downloadDirectory = FileUtil.selectDownloadDirectory();
        //if download directory doesn't already exist attempt to make it
        if (!Files.isDirectory(downloadDirectory)) {
            LOG.warn("Directory does not exist. A new directory will be created at path: {}", downloadFilePath);
            if (!downloadDirectory.toFile().mkdir()) {
                //could not be created.
                throw new NihmsSubmissionEtlException("A new download directory could not be created at path: {}. Please provide a valid path for the downloads");
            }
        }

        String[] args = new String[0];
        
        //TODO:first initiate download
        //Main.main(args);
                
        List<Path> filePaths = null;
        try {
            filePaths = FileUtil.getCsvFilePaths(downloadDirectory);
        } catch (Exception e) {
            throw new NihmsSubmissionEtlException(String.format("A problem occurred while loading file paths from %s", downloadDirectory.toString()),e);
        }

        if (filePaths.size() == 0) {
            LOG.warn("No CSV files found at path {}", filePaths);
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
