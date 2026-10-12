package com.orbitcrm.shared.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers side effects until the surrounding transaction has committed.
 *
 * <p>Purpose: never produce an external effect for data that ends up rolled back. Used for sending invitation
 * and password reset emails, pushing server-sent notifications, invalidating the principal and picklist caches,
 * deleting stored attachment files and starting background import jobs (which must see the committed job row).
 * When no transaction synchronization is active, the action runs immediately.
 *
 * @author Abhay
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
