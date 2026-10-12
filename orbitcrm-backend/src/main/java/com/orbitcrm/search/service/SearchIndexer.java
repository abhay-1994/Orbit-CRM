package com.orbitcrm.search.service;

import java.util.UUID;

import com.orbitcrm.search.dto.IndexDocument;

/**
 * Public API for maintaining the global search index.
 *
 * <p>Purpose: keep {@code search_index} in sync with the records. The record engine calls {@link #upsert} after every
 * create, update and restore and {@link #remove} after a delete, inside the write transaction, so the index is always
 * consistent with committed data.
 *
 * @author Abhay
 */
public interface SearchIndexer {

    void upsert(IndexDocument document);

    void remove(String moduleKey, UUID recordId);
}
