package org.dataconservancy.pass.loader.nihms;

import java.io.File;

import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;

/**
 * Hello world!
 *
 */
public class Main 
{
    public static void main( String[] args ) throws InterruptedException
    {
        String filepath = Main.class.getClassLoader().getResource("geckodriver.exe").getPath().toString();
        System.setProperty("webdriver.gecko.driver",filepath);

        FirefoxBinary firefoxBinary = new FirefoxBinary();
        //firefoxBinary.addCommandLineOptions("--headless");
        
        FirefoxProfile profile = new FirefoxProfile();
        //Set Location to store files after downloading. 
        //TODO: make download folder configurable
        File downloadDir = new File(System.getProperty("user.dir") + "/downloads");
        if (!downloadDir.exists()) {
            downloadDir.mkdir();
        }
        profile.setPreference("browser.download.dir", downloadDir.getAbsolutePath().toString());
        System.out.println("writing files to: " + downloadDir.getAbsolutePath().toString());
        profile.setPreference("browser.download.folderList", 2);
 
        //Set Preference to not show file download confirmation dialogue using MIME types Of different file extension types.
        profile.setPreference("browser.helperApps.neverAsk.saveToDisk", "text/csv;"); 
        profile.setPreference("browser.download.manager.showWhenStarting", false );

        FirefoxOptions options = new FirefoxOptions();        
        options.setProfile(profile);
        options.setBinary(firefoxBinary);
        
        WebDriver driver = new FirefoxDriver(options);

        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        System.out.println("opening driver");
        //get to era login
        driver.get("https://www.ncbi.nlm.nih.gov/account/pacm/?back_url=https%3A%2F%2Fwww%2Encbi%2Enlm%2Enih%2Egov%2Fpmc%2Futils%2Fpacm%2Flogin");

        System.out.println("first login options page loaded");
        
        driver.switchTo().frame("loginframe");
        driver.findElement(By.xpath("//img[@alt='Sign in with eRA Commons']")).click();
        
        driver.switchTo().defaultContent();
        
        System.out.println("selecting era commons option");
        //enter login info
        driver.findElement(By.id("USER")).click();
        driver.findElement(By.id("USER")).sendKeys("theusername");
        driver.findElement(By.id("PASSWORD")).click();
        driver.findElement(By.id("PASSWORD")).sendKeys("thepassword");
        TimeUnit.SECONDS.sleep(1);

        System.out.println("entered username/pass");
        driver.findElement(By.id("Image2")).click();
        System.out.println("logged in");
        TimeUnit.SECONDS.sleep(5);
        
        driver.findElement(By.xpath("//a[starts-with(@href, 'https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/n?')]")).click();
        //driver.get("https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/n?filter=&pdf=03%2F2017&pdt=03%2F2018&inst=JOHNS+HOPKINS+UNIVERSITY&ipf=4134401&rd=03%2F27%2F2018");
        System.out.println("goto non-compliant list");
        driver.findElement(By.linkText("Download as CSV file")).click();
        TimeUnit.SECONDS.sleep(30);
        System.out.println("download");
        //navigate to csv
        driver.close();
        
    }
}
