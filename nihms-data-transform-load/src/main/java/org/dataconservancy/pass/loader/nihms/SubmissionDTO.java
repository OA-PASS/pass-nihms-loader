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

import org.dataconservancy.pass.model.Publication;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;

/**
 * Data transfer object to hold the various components of a NIHMS Submission up to the point of
 * update or create.
 * @author Karen Hanson
 */
public class SubmissionDTO {

    private Submission submission = null;
    
    private Publication publication = null;
        
    private RepositoryCopy repositoryCopy = null;
    
    private URI grantId = null;

    
    /**
     * @return the submission
     */
    public Submission getSubmission() {
        return submission;
    }

    
    /**
     * @param submission the submission to set
     */
    public void setSubmission(Submission submission) {
        this.submission = submission;
    }

    
    /**
     * @return the publication
     */
    public Publication getPublication() {
        return publication;
    }

    
    /**
     * @param publication the publication to set
     */
    public void setPublication(Publication publication) {
        this.publication = publication;
    }

    /**
     * @return the repositoryCopy
     */
    public RepositoryCopy getRepositoryCopy() {
        return repositoryCopy;
    }

    
    /**
     * @param repositoryCopy the repositoryCopy to set
     */
    public void setRepositoryCopy(RepositoryCopy repositoryCopy) {
        this.repositoryCopy = repositoryCopy;
    }


    /**
     * @return the grantId
     */
    public URI getGrantId() {
        return grantId;
    }


    /**
     * @param grantId the grantId to set
     */
    public void setGrantId(URI grantId) {
        this.grantId = grantId;
    }
    
}
