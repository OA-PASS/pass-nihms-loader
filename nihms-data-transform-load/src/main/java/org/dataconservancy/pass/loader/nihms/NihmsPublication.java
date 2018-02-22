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
     * Status of NIHMS submission
     */
    private Status submissionStatus;
    
    /**
     * Constructor populates the required fields for a NIHMS publication
     * @param pmid
     * @param grantNumber
     * @param submissionStatus
     */
    public NihmsPublication(String pmid, String grantNumber, Status submissionStatus) {
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
}
