package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentRepository extends MongoRepository<Agent, String> {

    Optional<Agent> findByCode(String code);

    boolean existsByCode(String code);

    /** Tra Agent theo slug public (link /a/<slug>); agent chưa có slug không bao giờ khớp. */
    Optional<Agent> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** List agents lọc theo trạng thái (ADMIN xem agent SUSPENDED/CLOSED) — PLAN §2.3. */
    List<Agent> findByStatusOrderByCodeAsc(AgentStatus status);

    List<Agent> findAllByOrderByCodeAsc();
}
