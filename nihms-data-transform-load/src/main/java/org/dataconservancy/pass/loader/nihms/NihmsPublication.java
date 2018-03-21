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

import org.dataconservancy.pass.model.Submission.Status;

/**
 * Light weight model for transferring data from CSV files to processing
 * @author Karen Hanson
 * @version $Id$
 */
public class NihmsPublication {
    
    /** 
     * NIHMS submission CSV field "PMID" 
     */
    private String pmid;

    /** 
     * NIHMS submission CSV field "Grant number" 
     */
    private String grantNumber;

    /** 
     * NIHMS submission CSV field "NIHMSID" 
     */
    private String nihmsId;

    /** 
     * NIHMS submission CSV field "PMCID" 
     */
    private String pmcId;
    
    /**
     * Status of NIHMS submission
     */
    private Status submissionStatus;
    
    /**
     * Constructor populates the required fields for a NIHMS publication
     * @param pmid
     * @param grantNumber
     * @param nihmsId
     * @param pmcId
     * @param submissionStatus
     */
    public NihmsPublication(String pmid, String grantNumber, String nihmsId, String pmcId, Status submissionStatus) {
        if (pmid == null || pmid.length()<3) {
            throw new IllegalArgumentException(String.format("PMID \"%s\" is not valid.", pmid));
        }
        if (grantNumber == null || grantNumber.length()<3) {
            throw new IllegalArgumentException(String.format("Grant number \"%s\" is not valid.", grantNumber));
        }
        if (submissionStatus == null) {
            throw new IllegalArgumentException(String.format("Submission status cannot be null."));
        }
        
        this.pmid = pmid;
        this.grantNumber = grantNumber;
        this.submissionStatus = submissionStatus;
        this.nihmsId = nihmsId;
        this.pmcId = pmcId;
        
    }

    
    /**
     * @return the pmid
     */
    public String getPmid() {
        return pmid;
    }

    
    /**
     * @param pmid the pmid to set
     */
    public void setPmid(String pmid) {
        this.pmid = pmid;
    }

    
    /**
     * @return the grantNumber
     */
    public String getGrantNumber() {
        return grantNumber;
    }

    
    /**
     * @param grantNumber the grantNumber to set
     */
    public void setGrantNumber(String grantNumber) {
        this.grantNumber = grantNumber;
    }

    
    /**
     * @return the submissionStatus
     */
    public Status getSubmissionStatus() {
        return submissionStatus;
    }

    
    /**
     * @param submissionStatus the submissionStatus to set
     */
    public void setSubmissionStatus(Status submissionStatus) {
        this.submissionStatus = submissionStatus;
    }  

    
    /**
     * @return the nihmsId
     */
    public String getNihmsId() {
        return nihmsId;
    }

    
    /**
     * @param nihmsId the nihmsId to set
     */
    public void setNihmsId(String nihmsId) {
        this.nihmsId = nihmsId;
    }  

    
    /**
     * @return the pmcId
     */
    public String getPmcId() {
        return pmcId;
    }

    
    /**
     * @param pmcId the pmcId to set
     */
    public void setPmcId(String pmcId) {
        this.pmcId = pmcId;
    }  
}
