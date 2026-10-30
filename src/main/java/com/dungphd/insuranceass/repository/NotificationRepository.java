package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    /** Mới nhất trước, chỉ của đúng recipient. */
    List<Notification> findByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

    List<Notification> findByRecipientEmailAndReadOrderByCreatedAtDesc(String recipientEmail, boolean read);

    long countByRecipientEmailAndRead(String recipientEmail, boolean read);

    /** Tra cứu kèm recipient để mark-as-read sai chủ trả 404 mà không lộ sự tồn tại. */
    Optional<Notification> findByIdAndRecipientEmail(String id, String recipientEmail);
}
