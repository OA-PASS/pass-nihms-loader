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

import java.net.URLEncoder;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.dataconservancy.pass.loader.nihms.model.NihmsStatus;
import org.dataconservancy.pass.loader.nihms.util.FileUtil;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.dataconservancy.pass.loader.nihms.util.ProcessingUtil.nullOrEmpty;

/**
 *
 * @author Karen Hanson
 */
public class NihmsHarvester {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsHarvester.class);
    
    //properties needed to setup FireFox driver
    private static final String DOWNLOAD_DIRECTORY_PROPNAME = "browser.download.dir";
    private static final String DOWNLOAD_FOLDERLIST_PROPNAME = "browser.download.folderList";
    private static final String NEVERASK_SAVETODISK_PROPNAME = "browser.helperApps.neverAsk.saveToDisk";
    private static final String DOWNLOAD_SHOWWHENSTART_PROPNAME = "browser.download.manager.showWhenStarting";
    private static final String DOWNLOAD_ALLOWEDAUTO_MIMETYPE = "text/csv";
    private static final String GECKODRIVER_PATH_PROPNAME = "webdriver.gecko.driver";
    private static final String HEADLESS_MODE = "--headless";
    
    private static final Integer PAGELOAD_WAIT_TIMEOUT = 20;
    
    private static final String COMPLIANT_FILE_PREFIX = "Compliant ";
    private static final String NONCOMPLIANT_FILE_PREFIX = "Noncompliant ";
    private static final String INPROCESS_FILE_PREFIX = "In process";
    
    
    //page elements
    private static final String START_URL = "https://www.ncbi.nlm.nih.gov/account/pacm/?back_url=https%3A%2F%2Fwww%2Encbi%2Enlm%2Enih%2Egov%2Fpmc%2Futils%2Fpacm%2Flogin";
    private static final String GUI_LOGIN_FRAME = "loginframe";    
    private static final String GUI_ERA_SIGNIN_BUTTON_XPATH = "//img[@alt='Sign in with eRA Commons']";
    private static final String GUI_USER_FIELD_NAME = "USER";
    private static final String GUI_PASSWORD_FIELD_NAME = "PASSWORD";
    private static final String GUI_LOGIN_BUTTON_ID = "Image2";
    private static final String GUI_DOWNLOAD_LINKTEXT = "Download as CSV file";
    private static final String GUI_NONCOMPLIANT_LINK_XPATH = "//a[starts-with(@href, 'https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/n?')]";
    private static final String GUI_COMPLIANT_LINK_XPATH = "//a[starts-with(@href, 'https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/c?')]";
    private static final String GUI_INPROCESS_LINK_XPATH = "//a[starts-with(@href, 'https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/p?')]";
    private static final String STARTDATE_FILTER_URL_TEMPLATE = "https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/s?pdf=%s";
    private static final String GUI_SIGNOUT_LINK = "https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/logout";
    
    /**
     * Login username for NIHMS website
     */
    private String nihmsUser;
    
    /**
     * Login password for NIHMS website
     */
    private String nihmsPasswrd;
    
    /**
     * Directory to download files to
     */
    private Path downloadDirectoryPath;
    
    
    /**
     * Initiate harvester with required properties
     * 
     * @param downloadDirectoryPath
     * @param nihmsUser
     * @param nihmsPasswrd
     */
    public NihmsHarvester() {
        this.downloadDirectoryPath = FileUtil.getDataDirectory().toPath();
        this.nihmsUser = NihmsHarvesterConfig.getUserName();
        this.nihmsPasswrd = NihmsHarvesterConfig.getPassword();
                
        if (downloadDirectoryPath==null) {throw new RuntimeException("The harvester's downloadDirectory cannot be empty");}
        if (nullOrEmpty(nihmsUser)) {throw new RuntimeException("The harvester's nihmsUser cannot be empty");}
        if (nullOrEmpty(nihmsPasswrd)) {throw new RuntimeException("The harvester's nihmsPasswrd cannot be empty");}
                
        //if download directory doesn't already exist attempt to make it
        if (!Files.isDirectory(downloadDirectoryPath)) {
            LOG.warn("Download directory does not exist at path provided. A new directory will be created at path: {}", downloadDirectoryPath);
            if (!downloadDirectoryPath.toFile().mkdir()) {
                //could not be created.
                throw new RuntimeException("A new download directory could not be created at path: {}. Please provide a valid path for the downloads");
            }
        }
    }
    
    /**
     * Retrieve files from NIHMS based on status list and startDate provided
     * 
     * @param statusesToDownload list of {@code NihmsStatus} types to download from the NIHMS website
     * @param startDate formatted as {@code yyyy-mm}, can be null to default to 1 year prior to harvest date
     */
    public void harvest(Set<NihmsStatus> statusesToDownload, String startDate) {
        if (nullOrEmpty(statusesToDownload)) {
            throw new RuntimeException("statusesToDownload list cannot be empty");
        }
        if (!validStartDate(startDate)) {
            throw new RuntimeException(String.format("The startDate %s is not valid. The date must be formatted as yyyy-mm", startDate));
        }   
        
        WebDriver driver = null;
        
        try {
            //make sure the property is set as expected
            System.setProperty(GECKODRIVER_PATH_PROPNAME, NihmsHarvesterConfig.getGeckoDriverPath());
    
            FirefoxBinary firefoxBinary = new FirefoxBinary();
            firefoxBinary.addCommandLineOptions(HEADLESS_MODE);
            
            FirefoxProfile profile = new FirefoxProfile();
            //Set Location to store files after downloading. 
            profile.setPreference(DOWNLOAD_DIRECTORY_PROPNAME, downloadDirectoryPath.toString());
            LOG.info("Writing files to: {}", downloadDirectoryPath.toString());
            profile.setPreference(DOWNLOAD_FOLDERLIST_PROPNAME, 2);
     
            //Set Preference to not show file download confirmation dialogue using MIME types Of different file extension types.
            profile.setPreference(NEVERASK_SAVETODISK_PROPNAME, DOWNLOAD_ALLOWEDAUTO_MIMETYPE); 
            profile.setPreference(DOWNLOAD_SHOWWHENSTART_PROPNAME, false );
    
            FirefoxOptions options = new FirefoxOptions();        
            options.setProfile(profile);
            options.setBinary(firefoxBinary);
            
            driver = new FirefoxDriver(options);
    
            driver.manage().timeouts().implicitlyWait(PAGELOAD_WAIT_TIMEOUT, TimeUnit.SECONDS);
            
            LOG.debug("Opening Selenium driver");
            //get to era login
            driver.get(START_URL);
    
            LOG.debug("First login options page loaded");
            
            driver.switchTo().frame(GUI_LOGIN_FRAME);
            driver.findElement(By.xpath(GUI_ERA_SIGNIN_BUTTON_XPATH)).click();
            
            driver.switchTo().defaultContent();
            
            LOG.debug("selecting era commons option");
            //enter login info
            driver.findElement(By.id(GUI_USER_FIELD_NAME)).click();
            driver.findElement(By.id(GUI_USER_FIELD_NAME)).sendKeys(nihmsUser);
            driver.findElement(By.id(GUI_PASSWORD_FIELD_NAME)).click();
            driver.findElement(By.id(GUI_PASSWORD_FIELD_NAME)).sendKeys(nihmsPasswrd);
    
            LOG.debug("Entered username/pass into form");
            driver.findElement(By.id(GUI_LOGIN_BUTTON_ID)).click();
            LOG.info("Logged into NIHMS download page");
            
            if (!nullOrEmpty(startDate)) {
                startDate = startDate.replace("-", "/");
                LOG.info("Filtering with Start Date " + startDate);
                String filteredUrl = String.format(STARTDATE_FILTER_URL_TEMPLATE, URLEncoder.encode(startDate, "UTF-8"));
                driver.get(filteredUrl);
            }
            
            if (statusesToDownload.contains(NihmsStatus.COMPLIANT)) {
                driver.findElement(By.xpath(GUI_COMPLIANT_LINK_XPATH)).click();     
                LOG.info("Goto compliant list");
                driver.findElement(By.linkText(GUI_DOWNLOAD_LINKTEXT)).click(); 
                String newfile = pollAndRename(COMPLIANT_FILE_PREFIX, NihmsStatus.COMPLIANT);    
                LOG.info("Downloaded compliant publications as file " + newfile);       
            }        
            
            if (statusesToDownload.contains(NihmsStatus.NON_COMPLIANT)) {
                driver.findElement(By.xpath(GUI_NONCOMPLIANT_LINK_XPATH)).click();     
                LOG.info("Goto non-compliant list");
                driver.findElement(By.linkText(GUI_DOWNLOAD_LINKTEXT)).click(); 
                String newfile = pollAndRename(NONCOMPLIANT_FILE_PREFIX, NihmsStatus.NON_COMPLIANT);
                LOG.info("Downloaded and saved non-compliant publications as file " + newfile);       
            }
            
            if (statusesToDownload.contains(NihmsStatus.IN_PROCESS)) {
                driver.findElement(By.xpath(GUI_INPROCESS_LINK_XPATH)).click();     
                LOG.info("Goto in-process list");
                driver.findElement(By.linkText(GUI_DOWNLOAD_LINKTEXT)).click();  
                String newfile = pollAndRename(INPROCESS_FILE_PREFIX, NihmsStatus.IN_PROCESS);
                LOG.info("Downloaded and saved in-process publications as file " + newfile);       
            }

            //logout
            driver.get(GUI_SIGNOUT_LINK); 
            
        } catch (Exception ex) {
            throw new RuntimeException("An error occurred while downloading the NIHMS files.", ex);
        } finally {
            if (driver!=null) {
                driver.quit();
            }
            try {
                Runtime.getRuntime().exec("taskkill /F /IM geckodriver.exe /T");
            } catch (Exception ex) {
                //do nothing
            }
        }
    }
    
    /**
     * null or empty are OK for start date, but a badly formatted date that does not have the format mm-yyyy should return false
     * @param startDate true if valid start date (empty or formatted mm-yyyy)
     */
    public static boolean validStartDate(String startDate) {
        return (nullOrEmpty(startDate) || startDate.matches("^(0?[1-9]|1[012])-(\\d{4})$"));
    }
    
    private String pollAndRename(String prefix, NihmsStatus status) {
        File newfile = FileWatcher.getNewFile(downloadDirectoryPath, prefix, ".csv");
        LOG.info("New file downloaded: " + newfile.getAbsolutePath());
        String newFilePath = null;
        if (newfile!=null) {
            DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyyMMddHHmmss");
            String timeStamp = fmt.print(new DateTime());
            newFilePath = downloadDirectoryPath.toString() + "/" + status.toString() + "_nihmspubs_" + timeStamp + ".csv";
            newfile.renameTo(new File(newFilePath));            
        } 
        LOG.info("Downloaded {} publications as file {}", status.toString(), newFilePath);       
        return newFilePath;
    }

    
    
}
