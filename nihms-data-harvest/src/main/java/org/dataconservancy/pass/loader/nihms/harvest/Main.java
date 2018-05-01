package org.dataconservancy.pass.loader.nihms.harvest;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.dataconservancy.pass.loader.nihms.harvest.NihmsSubmissionHarvester.NihmsStatus;
import org.dataconservancy.pass.loader.nihms.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 *
 * @author Karen Hanson
 */
public class Main 
{
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) {
        
        //check that the download directory is available, if not try to create it so that we can move forward
        Path downloadDir = FileUtil.selectDownloadDirectory();
        if (!Files.isDirectory(downloadDir)) {
            LOG.warn("Directory does not exist. A new directory will be created at path: {}", downloadDir.toString());
            if (!downloadDir.toFile().mkdir()) {
                //could not be created.
                throw new RuntimeException(String.format("A new download directory could not be created at path: %s. Please provide a valid path for the downloads", downloadDir.toString()));
            }
        }
        LOG.info("NIHMS files will be downloaded to directory \"{}\"", downloadDir.toString());
        
        //now validate properties location
        Path configDir = FileUtil.selectConfigDirectory();
        if (!Files.isDirectory(configDir)) {
            throw new RuntimeException(String.format("The config directory could not be found at the location provided: %s", configDir.toString()));
        }
        
        Properties properties = new Properties();
        
        NihmsSubmissionHarvester harvester = new NihmsSubmissionHarvester(properties);
        //check for arguments and populate
        Set<NihmsStatus> statusesToHarvest = new HashSet<NihmsStatus>();        
        //if args contains "-c" include compliant etc.
        statusesToHarvest.add(NihmsStatus.COMPLIANT);
        statusesToHarvest.add(NihmsStatus.NON_COMPLIANT);
        statusesToHarvest.add(NihmsStatus.IN_PROCESS);
        
        if (statusesToHarvest.size()>0) {
            try {
                harvester.harvest(statusesToHarvest); 
            } catch (Exception ex){
                LOG.error("A problem occurred while attempting to download the NIHMS submission status files. Files may not have been downloaded successfully.", ex);
            }
        }
    }
}
