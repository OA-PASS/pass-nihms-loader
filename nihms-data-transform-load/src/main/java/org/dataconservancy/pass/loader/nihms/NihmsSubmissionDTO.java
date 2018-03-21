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

import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.ext.nihms.NihmsSubmission;

/**
 * Data transfer object to hold the various components of a NIHMS Submission up to the point of
 * update or create.
 * @author Karen Hanson
 */
public class NihmsSubmissionDTO {

    private NihmsSubmission nihmsSubmission = null;
    
    private Deposit deposit = null;

    private URI grantUri = null;

    
    /**
     * @return the nihmsSubmission
     */
    public NihmsSubmission getNihmsSubmission() {
        return nihmsSubmission;
    }

    
    /**
     * @param nihmsSubmission the nihmsSubmission to set
     */
    public void setNihmsSubmission(NihmsSubmission nihmsSubmission) {
        this.nihmsSubmission = nihmsSubmission;
    }

    
    /**
     * @return the deposit
     */
    public Deposit getDeposit() {
        return deposit;
    }

    
    /**
     * @param deposit the deposit to set
     */
    public void setDeposit(Deposit deposit) {
        this.deposit = deposit;
    }


    
    /**
     * @return the grantUri
     */
    public URI getGrantUri() {
        return grantUri;
    }

    
    /**
     * @param grantUri the grantUri to set
     */
    public void setGrantUri(URI grantUri) {
        this.grantUri = grantUri;
    }
    
}
