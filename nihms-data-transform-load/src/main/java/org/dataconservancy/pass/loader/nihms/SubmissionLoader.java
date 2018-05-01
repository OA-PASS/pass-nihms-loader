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

import org.dataconservancy.pass.client.nihms.NihmsPassClientService;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Publication;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
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
        if (dto==null || dto.getSubmission()==null) {
            throw new RuntimeException("A null Submission object was passed to the loader.");
        }
        
        LOG.info("Loading information for Submission with PMID {}", dto.getPublication().getPmid());
                       
        Publication publication = dto.getPublication();
        URI publicationUri = publication.getId();
        if (publicationUri==null) {
            publicationUri = clientService.createPublication(publication);
        } else {
            clientService.updatePublication(publication);
        }
        
        Submission submission = dto.getSubmission();
        URI submissionUri = submission.getId();
        if (submissionUri==null) {
            submission.setPublication(publicationUri);
            submissionUri = clientService.createSubmission(submission);
        } else {
            clientService.updateSubmission(submission);
        }

        RepositoryCopy repositoryCopy = dto.getRepositoryCopy();
        if (repositoryCopy!=null) {
            URI repositoryCopyUri = repositoryCopy.getId();
            if (repositoryCopyUri==null) {
                repositoryCopy.setPublication(publicationUri);
                clientService.createRepositoryCopy(repositoryCopy);
            } else {
                clientService.updateRepositoryCopy(repositoryCopy);
            }
            
            // If repository copy is changing, check Deposit to make sure the RepositoryCopyId is present
            Deposit deposit = clientService.findDepositBySubmissionAndRepositoryId(submission.getId(), TransformUtil.getNihmsRepositoryUri());
            if (deposit!=null && deposit.getRepositoryCopy()==null) {
                deposit.setRepositoryCopy(repositoryCopyUri);
                clientService.updateDeposit(deposit);
            }
        }
    }
    
}
