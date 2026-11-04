package com.dungphd.insuranceass.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Track 2 — TransactionSideEffects.afterCommit:
 * không có transaction → chạy ngay (hành vi cũ, unit test mock);
 * có transaction → hoãn tới afterCommit, không chạy trước commit.
 */
class TransactionSideEffectsTest {

    @AfterEach
    void clearTransactionState() {
        // Dọn state static để không rò sang test khác trong cùng JVM.
        // Cả hai call đều throw nếu không active nên phải guard trước.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void noTransaction_runsImmediately() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        AtomicBoolean ran = new AtomicBoolean(false);
        TransactionSideEffects.afterCommit(() -> ran.set(true));
        assertTrue(ran.get());
    }

    @Test
    void activeTransaction_defersUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        AtomicBoolean ran = new AtomicBoolean(false);
        TransactionSideEffects.afterCommit(() -> ran.set(true));
        // Chưa commit → chưa chạy (mail không được gửi trước commit).
        assertFalse(ran.get());

        // Giả lập commit: kích hoạt afterCommit của mọi synchronization.
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        assertTrue(ran.get());
    }

    @Test
    void sideEffectThrowing_isSwallowedNeverBreaksCaller() {
        assertDoesNotThrow(() -> TransactionSideEffects.afterCommit(() -> {
            throw new RuntimeException("simulated mail failure");
        }));
        TransactionSideEffects.afterCommit(null); // null là no-op, không NPE.
    }
}
