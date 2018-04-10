package org.dataconservancy.pass.loader.nihms;

import java.io.File;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NIHMS Submission Loader CLI
 * @author Karen Hanson
 */
public class NihmsSubmissionLoaderCLI 
{
    
    private static Logger LOG = LoggerFactory.getLogger(NihmsSubmissionLoaderApp.class);
    
    public static void main( String[] args )
    {
        Path directory = FileUtil.selectDirectory(args);
        if (directory == null) {
            File downloadDir = new File(System.getProperty("user.dir") + "/downloads");
            directory = downloadDir.toPath();
            LOG.warn("No directory indicated either as an argument or using the \"dir\" system property, defaulting to %s", downloadDir.getAbsolutePath());
        }
        NihmsSubmissionLoaderApp app = new NihmsSubmissionLoaderApp();
        app.run(directory);
        System.exit(0);
    }
}
