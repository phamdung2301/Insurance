package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.AdminAuditLog;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminAuditLogRepository extends MongoRepository<AdminAuditLog, String> {

    default List<AdminAuditLog> findAllNewestFirst() {
        return findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
