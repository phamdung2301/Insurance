package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.LoginHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoginHistoryRepository extends MongoRepository<LoginHistory, String> {

    /**
     * 50 bản ghi mới nhất của đúng một email, mới nhất trước.
     * Giới hạn 50 được đẩy xuống query (Top50) thay vì cắt ở bộ nhớ.
     */
    List<LoginHistory> findTop50ByEmailOrderByLoggedInAtDesc(String email);
}
