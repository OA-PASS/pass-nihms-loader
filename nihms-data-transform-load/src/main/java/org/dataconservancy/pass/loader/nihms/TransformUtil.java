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

import java.net.URI;
import java.net.URISyntaxException;

import java.util.Set;

import org.dataconservancy.pass.client.util.ConfigUtil;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.RepositoryCopy.CopyStatus;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Karen Hanson
 */
public class TransformUtil {

    private static final Logger LOG = LoggerFactory.getLogger(TransformUtil.class);
    
    private static final String NIHMS_DATEFORMAT = "MM/dd/yyyy";    

    private static final String NIHMS_REPOSITORY_URI_KEY = "nihms.repository.uri"; 
    private static final String NIHMS_REPOSITORY_URI_DEFAULT = "https://example.com/fedora/repositories/1";
    
    /**
     * Determines a new deposit status based on various dates populated in the NIHMS publication
     * If the status registered in PASS is further along than NIHMS thinks we are, roll back to
     * what NIHMS says and log the fact as a warning.
     * @param pub
     * @param currDepositStatus
     * @return
     */
    public static CopyStatus calcRepoCopyStatus(NihmsPublication pub, CopyStatus currCopyStatus) {
        if (pub.getNihmsStatus().equals(NihmsStatus.COMPLIANT)) {
            return CopyStatus.COMPLETE;
        }
        
        if (pub.isTaggingComplete() || pub.hasInitialApproval()) {
            return CopyStatus.IN_PROGRESS;
        }

        if (pub.isFileDeposited()) {
            return CopyStatus.ACCEPTED;
        }
        
        // if the current status implies we are further along than we really are, roll back to submitted and log.
        if (currCopyStatus!=null 
                && (currCopyStatus.equals(CopyStatus.IN_PROGRESS))
                && (currCopyStatus.equals(CopyStatus.COMPLETE))) {
            LOG.warn("The status of the RepositoryCopy in PASS was at a later stage than the current NIHMS status would imply. "
                    + "Rolled back from \"{}\" to \"accepted\" for pmid {}", currCopyStatus.toString(), pub.getPmid());
            return CopyStatus.ACCEPTED;
        }
        
        return currCopyStatus;
    }
            
    /**
     * Searches the deposits list for a deposit to a specific repository. Returns the matching deposit record
     * @param deposits
     * @param repository
     * @return
     */
    public static Deposit pickDepositForRepository(Set<Deposit> deposits, URI repository) {
        if (deposits != null) {
            for (Deposit deposit : deposits) {
                if (deposit!=null && deposit.getRepository().equals(repository)) {
                    return deposit;
                }
            }
        }
        return null;
    }
    
    /**
     * Checks if string null or empty
     * @param str
     * @return
     */    
    public static boolean emptyStr(String str) {
        return (str==null || str.isEmpty());
    }
    
    /**
     * Formats a date MM/dd/yyyy to a joda datetime, returns null if no date passed in
     * @param date
     * @return
     */
    public static DateTime formatDate(String date) {
        if (emptyStr(date)) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormat.forPattern(NIHMS_DATEFORMAT);
        DateTime dt = formatter.parseDateTime(date);
        return dt;
    }
    
    /**
     * Retrieves the NIHMS Repository URI based on property key
     * @return
     */
    public static URI getNihmsRepositoryUri() {
        try {
            return new URI(ConfigUtil.getSystemProperty(NIHMS_REPOSITORY_URI_KEY, NIHMS_REPOSITORY_URI_DEFAULT));
        } catch (URISyntaxException e) {
            throw new RuntimeException("NIHMS repository property is not a valid URI, please check the nihms.pass.uri property is populated correctly.", e);
        }
    }

}
