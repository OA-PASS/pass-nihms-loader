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
package org.dataconservancy.pass.loader.nihms.harvest;

import java.io.File;

import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Karen Hanson
 */
public class NihmsSubmissionHarvester {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsSubmissionHarvester.class);
    
    //properties needed to setup FireFox driver
    private static final String DOWNLOAD_DIRECTORY_PROPNAME = "browser.download.dir";
    private static final String DOWNLOAD_FOLDERLIST_PROPNAME = "browser.download.folderList";
    private static final String NEVERASK_SAVETODISK_PROPNAME = "browser.helperApps.neverAsk.saveToDisk";
    private static final String DOWNLOAD_SHOWWHENSTART_PROPNAME = "browser.download.manager.showWhenStarting";
    private static final String DOWNLOAD_ALLOWEDAUTO_MIMETYPE = "text/csv";
    private static final String GECKODRIVER_PATH_PROPNAME = "webdriver.gecko.driver";
    private static final String GECKODRIVER_PATH = "geckodriver.exe";
    
    private static final Integer PAGELOAD_WAIT_TIMEOUT = 15;
    
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
    private static final String GUI_INPROCESS_LINK_XPATH = "//a[starts-with(@href, 'https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/i?')]";
    
    
    private String nihmsUser;
    private String nihmsPasswrd;
    
    private File downloadDirectory;
    
    public NihmsSubmissionHarvester(Properties properties) {
        this.downloadDirectory = new File(properties.get("nihms.download.directory").toString());  
        nihmsUser = properties.get("nihms.harvester.username").toString();
        nihmsPasswrd = properties.get("nihms.harvester.password").toString();
    }
    
    public void harvest(Set<NihmsStatus> statusesToDownload) throws Exception {

        String filepath = getClass().getClassLoader().getResource(GECKODRIVER_PATH).getPath().toString();
        System.setProperty(GECKODRIVER_PATH_PROPNAME,filepath);

        FirefoxBinary firefoxBinary = new FirefoxBinary();
        //firefoxBinary.addCommandLineOptions("--headless");
        
        FirefoxProfile profile = new FirefoxProfile();
        //Set Location to store files after downloading. 
        profile.setPreference(DOWNLOAD_DIRECTORY_PROPNAME, downloadDirectory.getAbsolutePath());
        LOG.info("Writing files to: {}", downloadDirectory.getAbsolutePath());
        profile.setPreference(DOWNLOAD_FOLDERLIST_PROPNAME, 2);
 
        //Set Preference to not show file download confirmation dialogue using MIME types Of different file extension types.
        profile.setPreference(NEVERASK_SAVETODISK_PROPNAME, DOWNLOAD_ALLOWEDAUTO_MIMETYPE); 
        profile.setPreference(DOWNLOAD_SHOWWHENSTART_PROPNAME, false );

        FirefoxOptions options = new FirefoxOptions();        
        options.setProfile(profile);
        options.setBinary(firefoxBinary);
        
        WebDriver driver = new FirefoxDriver(options);

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
        TimeUnit.SECONDS.sleep(1);

        LOG.debug("Entered username/pass into form");
        driver.findElement(By.id(GUI_LOGIN_BUTTON_ID)).click();
        System.out.println("Logged into NIHMS download page");
        TimeUnit.SECONDS.sleep(5);
        
        if (statusesToDownload.contains(NihmsStatus.COMPLIANT)) {
            driver.findElement(By.xpath(GUI_COMPLIANT_LINK_XPATH)).click();     
            System.out.println("Goto compliant list");
            driver.findElement(By.linkText(GUI_DOWNLOAD_LINKTEXT)).click(); 
            TimeUnit.SECONDS.sleep(30);  
            System.out.println("Downloaded compliant");       
        }        
        if (statusesToDownload.contains(NihmsStatus.NON_COMPLIANT)) {
            //driver.get("https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/n?filter=&pdf=03%2F2017&pdt=03%2F2018&inst=JOHNS+HOPKINS+UNIVERSITY&ipf=4134401&rd=03%2F27%2F2018");
            driver.findElement(By.xpath(GUI_NONCOMPLIANT_LINK_XPATH)).click();     
            System.out.println("Goto non-compliant list");
            driver.findElement(By.linkText(GUI_DOWNLOAD_LINKTEXT)).click(); 
            TimeUnit.SECONDS.sleep(30); 
            System.out.println("Downloaded non-compliant");        
        }
        if (statusesToDownload.contains(NihmsStatus.IN_PROCESS)) {
            driver.findElement(By.xpath(GUI_INPROCESS_LINK_XPATH)).click();     
            System.out.println("Goto in-process list");
            driver.findElement(By.linkText(GUI_INPROCESS_LINK_XPATH)).click();  
            TimeUnit.SECONDS.sleep(30);
            System.out.println("Downloaded In-process");     
        }
        //navigate to csv
        driver.close();
    }
    
    public enum NihmsStatus {
        COMPLIANT,
        NON_COMPLIANT,
        IN_PROCESS;
    }
    
}
