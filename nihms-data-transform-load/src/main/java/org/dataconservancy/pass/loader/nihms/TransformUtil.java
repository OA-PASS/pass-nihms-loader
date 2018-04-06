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

import java.util.Set;

import org.dataconservancy.pass.model.Deposit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Karen Hanson
 */
public class TransformUtil {

    private static final Logger LOG = LoggerFactory.getLogger(TransformUtil.class);
    
    /**
     * Determines a new deposit status based on various dates populated in the NIHMS publication
     * If the status registered in PASS is further along than NIHMS thinks we are, roll back to
     * what NIHMS says and log the fact as a warning.
     * @param pub
     * @param currDepositStatus
     * @return
     */
    public static Deposit.Status calcDepositStatus(NihmsPublication pub, Deposit.Status currDepositStatus) {
        if (pub.getNihmsStatus().equals(NihmsStatus.COMPLIANT)) {
            return Deposit.Status.ACCEPTED;
        }
        
        if (pub.isTaggingComplete() || pub.hasInitialApproval()) {
            return Deposit.Status.IN_PROGRESS;
        }

        if (pub.isFileDeposited()) {
            return Deposit.Status.RECEIVED;
        }
        
        // if the current status implies we are further along than we really are, roll back to submitted and log.
        if (currDepositStatus.equals(Deposit.Status.IN_PROGRESS) 
                || currDepositStatus.equals(Deposit.Status.ACCEPTED)
                || currDepositStatus.equals(Deposit.Status.RECEIVED)) {
            LOG.warn("Deposit.Status in PASS was at a later stage than the current NIHMS status would imply. "
                    + "Rolled back from \"{}\" to \"submitted\" for pmid {}", currDepositStatus.getValue(), pub.getPmid());
            return Deposit.Status.SUBMITTED;
        }
        
        return currDepositStatus;
    }
    
    /**
     * Uses NIHMS publication information to determine whether a user action is required
     * This happens when there is an indication that a submission was started (has a file deposited date)
     * but the item is appearing in the non-compliant list.
     * @param pub
     * @return true if a user action is required
     */
    public static boolean isDepositUserActionRequired(NihmsPublication pub) {
        if (pub.isFileDeposited() && pub.getNihmsStatus().equals(NihmsStatus.NON_COMPLIANT)) {
            return true;
        } else {
            return false;
        }
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
     * Determines whether the NIHMS record indicates a Deposit is needed, for example, if the submission
     * is in process, or it has a NIHMS ID that should be captured.
     * @param pub
     * @return true if a NIHMS deposit should be created
     */
    public static boolean needNihmsDeposit(NihmsPublication pub) {
        if (pub.getPmcId()!=null && pub.getPmcId().length()>0) {return true;}
        if (pub.getNihmsId()!=null && pub.getNihmsId().length()>0) {return true;}
        if (pub.getNihmsStatus().equals(NihmsStatus.COMPLIANT) || pub.getNihmsStatus().equals(NihmsStatus.IN_PROCESS)) {return true;}
        return false;
    }

}
