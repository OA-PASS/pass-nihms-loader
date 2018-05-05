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

import org.dataconservancy.pass.loader.nihms.util.ConfigUtil;
import org.dataconservancy.pass.loader.nihms.util.FileUtil;

/**
 * Holds information and methods required to configure the NIHMS harvester tool.
 * @author Karen Hanson
 */
public class NihmsHarvesterConfig {

    private static final String USERNAME_KEY = "nihmsetl.harvester.username";
    
    private static final String PASSWRD_KEY = "nihmsetl.harvester.password";
    
    private static final String GECKO_PATH_KEY = "webdriver.gecko.driver";
    
    private static final String GECKODRIVER_DEFAULTNAME = "geckodriver.exe";
    
    /**
     * Retrieve the user name from a system property, or use default
     * @return user name
     */
    public static String getUserName() {
        String username = ConfigUtil.getSystemProperty(USERNAME_KEY, null);
        return username;
    }

    /**
     * Retrieve the password from a system property, or use default
     * @return user name
     */
    public static String getPassword() {
        String password = ConfigUtil.getSystemProperty(PASSWRD_KEY, null);
        return password;
    }

    /**
     * Retrieve the gecko driver path from a system property, or use current directory
     * @return user name
     */
    public static String getGeckoDriverPath() {
        String geckoDriverPath = ConfigUtil.getSystemProperty(GECKO_PATH_KEY, FileUtil.getCurrentDirectory() + "/" + GECKODRIVER_DEFAULTNAME);
        return geckoDriverPath;
    }
}
