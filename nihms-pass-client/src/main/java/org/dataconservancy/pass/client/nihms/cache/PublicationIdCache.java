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
package org.dataconservancy.pass.client.nihms.cache;

import java.net.URI;

import java.util.HashMap;

/**
 * Caches pmid and publicationId combination for easy lookup
 * @author Karen Hanson
 */
public class PublicationIdCache {

    private HashMap<String, URI> publicationCache;
    private static PublicationIdCache publicationSpace = null;

    private PublicationIdCache() {
        publicationCache = new HashMap<String, URI>();
    }

    public static synchronized PublicationIdCache getInstance() {
      if (publicationSpace == null) {
          publicationSpace = new PublicationIdCache();
      }
      return publicationSpace;
    }
    
    /**
     * Add publication to map
     * @param key
     * @param value
     */
    public synchronized void put(String pmid, URI publicationId) {
        publicationCache.put(pmid, publicationId);
    }
    
    /**
     * Retrieve publicationId by pmid
     * @param key
     * @return
     */
    public synchronized URI get(String pmid) {
        return publicationCache.get(pmid);
    }

    /**
     * Remove a Publication from cache
     * @param pmid
     */
    public synchronized void remove(String pmid) {
        publicationCache.remove(pmid);
    }

    /**
     * Get number of cached publications
     * @return
     */
    public synchronized int size() {
        return publicationCache.size();
    }

    /**
     * Empty map
     */
    public synchronized void clear() {
        publicationCache.clear();
    }
    
}
