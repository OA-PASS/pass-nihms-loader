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

import java.util.List;

import org.dataconservancy.pass.client.nihms.NihmsPassClientService;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.ext.nihms.NihmsSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Karen Hanson
 */
public class SubmissionLoader {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionLoader.class);
    
    private NihmsPassClientService clientService;

    /**
     * Initiates with default client service
     */
    public SubmissionLoader() {
        this.clientService = new NihmsPassClientService();
    }
    
    /**
     * Supports initiation with specific client service
     * @param clientService
     */
    public SubmissionLoader(NihmsPassClientService clientService) {
        this.clientService = clientService;
    }
    
 
    /**
     * Load the data in the NihmsSubmissionDTO to the database. Deal with any conflicts that occur during the updates
     * by implementing retries, failing gracefully etc.
     * @param dto
     */
    //TODO: need to add the code for dealing with conflicts in Fedora once that functionality is added to the pass-client
    public void load(NihmsSubmissionDTO dto) {
        if (dto==null || dto.getNihmsSubmission()==null) {
            throw new RuntimeException("A null Submission object was passed to the loader.");
        }
        //make sure there is a grant URI, if not something has gone wrong while forming the DTO.
        if (dto.getGrantUri()==null) {
            throw new RuntimeException(String.format("No Grant URI was provided for the Submission with PMID: %s.", dto.getNihmsSubmission().getPmid()));            
        }
       
        Deposit deposit = dto.getDeposit();
        NihmsSubmission submission = dto.getNihmsSubmission();
        URI submissionUri = submission.getId();
        boolean updateNihmsSubmission = false;
        
        if (submissionUri==null) {
            submissionUri = clientService.createNihmsSubmission(submission, dto.getGrantUri());
            LOG.info("A new submission was created with URI {}", submissionUri);
        } else {
            //create needs so be done in order to process deposits, so it will be done right away, but to 
            //updating twice, set a flag and update everything at the end. This can be rearranged once we 
            //remove circular references
            updateNihmsSubmission = true;               
        }
        
        if (deposit!=null) {
            URI depositUri = deposit.getId();
            if (depositUri==null) {
                //creating a new deposit, need to set submission
                deposit.setSubmission(submissionUri);
                depositUri = clientService.createDeposit(deposit);
                //while there is a circular reference, we need to double back and update the Submission deposits list.
                List<URI> deposituris = submission.getDeposits();
                deposituris.add(depositUri);
                submission.setDeposits(deposituris);
                updateNihmsSubmission = true;      
            } else {
                clientService.updateDeposit(deposit);
            }
        }
        
        if (updateNihmsSubmission) {
            clientService.updateNihmsSubmission(submission);     
        }
        
    }
    
}
