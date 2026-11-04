package com.dungphd.insuranceass.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Side-effect chỉ được chạy SAU KHI transaction commit — dùng cho mail và
 * ledger best-effort trong các luồng {@code @Transactional}.
 *
 * <p>Vì sao cần: mail không rollback được. Nếu gửi mail TRONG method
 * {@code @Transactional} mà bước sau fail → transaction rollback nhưng mail
 * "thành công" đã bay đi. Đăng ký callback afterCommit thì mail chỉ gửi khi
 * commit thật sự thành công; nếu rollback, callback không chạy, không có
 * mail sai.
 *
 * <p>Khi không có transaction đang active (unit test mock — annotation
 * {@code @Transactional} vô tác dụng khi bean không qua Spring proxy — hoặc
 * caller không-transactional), side-effect chạy NGAY LẬP TỨC để giữ đúng
 * hành vi cũ: test cũ verify mail/ledger được gọi vẫn pass, và caller
 * ngoài transaction không bị mất side-effect.
 *
 * <p>Side-effect tự nuốt exception (log warn): mail/ledger là ghi nhận phụ,
 * failure-isolated theo đúng pattern codebase — lỗi của chúng không được
 * biến một commit đã xong thành lỗi, và trong callback afterCommit thì
 * exception cũng không thể rollback được nữa.
 */
@Slf4j
public final class TransactionSideEffects {

    private TransactionSideEffects() {
    }

    public static void afterCommit(Runnable sideEffect) {
        if (sideEffect == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSafely(sideEffect);
                }
            });
        } else {
            runSafely(sideEffect);
        }
    }

    private static void runSafely(Runnable sideEffect) {
        try {
            sideEffect.run();
        } catch (Exception e) {
            log.warn("Transaction side-effect (afterCommit/immediate) failed and was swallowed: {}", e.getMessage());
        }
    }
}
