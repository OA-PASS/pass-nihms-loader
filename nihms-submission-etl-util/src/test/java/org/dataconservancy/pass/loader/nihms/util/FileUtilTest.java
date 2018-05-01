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
package org.dataconservancy.pass.loader.nihms.util;

import java.io.File;
import java.io.IOException;

import java.nio.file.FileSystems;
import java.nio.file.Path;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.dataconservancy.pass.loader.nihms.util.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Karen Hanson
 */
public class FileUtilTest {

    @Rule
    public TemporaryFolder folder= new TemporaryFolder();
    
    private File createdFolder;
    
    @Before
    public void createFolder() throws Exception {
        createdFolder= folder.getRoot();
    }
    
    
    @After
    public void clearProps() throws IOException {    
        System.clearProperty("filter");
        System.clearProperty("dir");  
        if (createdFolder.exists()) {
            folder.delete();
        }
        assertFalse(createdFolder.exists());
    }
    
    /**
     * Confirms select directory works if pass path as argument
     * @throws IOException
     */
    @Test
    public void testSelectDirectoryFromArgs() throws IOException {
        String[] args = new String[1];
        args[0] = createdFolder.getAbsolutePath();
        Path path = FileUtil.selectDirectory(args);
        assertEquals(createdFolder.getAbsolutePath(), path.toString());
    }

    /**
     * Confirms select directory works if path is a system property
     * @throws IOException
     */
    @Test
    public void testSelectDirectoryFromSystemProperty() throws IOException {
        System.setProperty("dir", createdFolder.getAbsolutePath());
        Path path = FileUtil.selectDirectory(new String[0]);
        assertEquals(createdFolder.getAbsolutePath(), path.toString());        
    }

    /**
     * Confirms select directory returns null if no path provided as argument or system property
     * @throws IOException
     */
    @Test
    public void testSelectDirectoryNoneSet() throws IOException {
        Path path = FileUtil.selectDirectory(new String[0]);
        assertEquals(null, path);        
    }
    
    
    /**
     * Confirms that getFilePaths will retrieve 3 csv files in target directory
     * @throws IOException
     */
    @Test
    public void testGetFilePaths3csv() throws IOException {
        File file1 = File.createTempFile("file1", ".csv", createdFolder);
        File file2 = File.createTempFile("file2", ".csv", createdFolder);
        File file3 = File.createTempFile("file3", ".csv", createdFolder);
        List<Path> paths = FileUtil.getCsvFilePaths(createdFolder.toPath());
        assertEquals(file1.toPath(), paths.get(0));
        assertEquals(file2.toPath(), paths.get(1));
        assertEquals(file3.toPath(), paths.get(2));
    }
    
    /**
     * Verifies that getFilePaths ignores .docx, and .done, and gathers only .csv
     * @throws IOException
     */
    @Test
    public void testGetFilePaths3csv1doc() throws IOException {
        File file1 = File.createTempFile("file1", ".csv", createdFolder);
        File.createTempFile("file2", ".csv.done", createdFolder);
        File file2 = File.createTempFile("file3", ".csv", createdFolder);
        File.createTempFile("file4", ".docx", createdFolder);
        List<Path> paths = FileUtil.getCsvFilePaths(createdFolder.toPath());
        assertEquals(file1.toPath(), paths.get(0));
        assertEquals(file2.toPath(), paths.get(1));
        assertEquals(2, paths.size());
        paths = null;
    }
    

    
    /**
     * Verifies that getFilePaths ignores .docx, and .done, and gathers only .csv
     * @throws IOException
     */
    @Test
    public void testGetFilePathsFilterFileName() throws IOException {
        File file1 = File.createTempFile("file1-02-08-2018-", ".csv", createdFolder);
        File.createTempFile("file2-02-08-2018-", ".docx", createdFolder);
        File file2 = File.createTempFile("file3-02-08-2018-", ".csv", createdFolder);
        File.createTempFile("file4-02-09-2018-", ".csv", createdFolder);
        File.createTempFile("file5-02-09-2018-", ".csv", createdFolder);
        System.setProperty("filter", "*02-08-2018*");
        List<Path> paths = FileUtil.getCsvFilePaths(createdFolder.toPath());
        assertEquals(file1.toPath(), paths.get(0));
        assertEquals(file2.toPath(), paths.get(1));
        assertEquals(2, paths.size());        
    }
    
    
    /**
     * Confirms that if you rename a file to append ".done", the new file exists, while the old is gone.
     * @throws IOException
     */
    @Test
    public void renameToDone() throws IOException {

        File file1 = File.createTempFile("file1-02-08-2018-", ".csv", createdFolder);
        FileUtil.renameToDone(file1.toPath());
        String newFileName = file1.getAbsolutePath().toString() + ".done";
        Path path = FileSystems.getDefault().getPath(newFileName);
        assertTrue(path.toFile().exists());
        assertFalse(file1.exists());
        
    }
    
    
}
