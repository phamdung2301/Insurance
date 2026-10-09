package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.Coverage;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.Policy;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PolicyCustomRepositoryImpl implements PolicyCustomRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public boolean addLocation(String policyNumber, Location location) {
        Query query = new Query(policyCriteria(policyNumber));
        Update update = new Update()
                .push("locations", location)
                .currentDate("updatedAt");
        UpdateResult result = mongoTemplate.updateFirst(query, update, Policy.class);
        return result.getModifiedCount() > 0;
    }

    @Override
    public boolean updateLocation(String policyNumber, String locationId, Location location) {
        Object locIdObj = parseLocationId(locationId);
        Query query = new Query(new Criteria().andOperator(
                policyCriteria(policyNumber),
                new Criteria().orOperator(
                        Criteria.where("locations.locationId").is(locIdObj),
                        Criteria.where("locations.locationId").is(locationId)
                )
        ));

        Update update = new Update()
                .set("locations.$.address", location.getAddress())
                .currentDate("updatedAt");
        UpdateResult result = mongoTemplate.updateFirst(query, update, Policy.class);
        return result.getModifiedCount() > 0;
    }

    @Override
    public boolean removeLocation(String policyNumber, String locationId) {
        Object locIdObj = parseLocationId(locationId);
        Query query = new Query(policyCriteria(policyNumber));
        Update update = new Update()
                .pull("locations", new Query(new Criteria().orOperator(
                        Criteria.where("locationId").is(locIdObj),
                        Criteria.where("locationId").is(locationId)
                )))
                .currentDate("updatedAt");
        UpdateResult result = mongoTemplate.updateFirst(query, update, Policy.class);
        return result.getModifiedCount() > 0;
    }

    @Override
    public boolean addCoverage(String policyNumber, String locationId, Coverage coverage) {
        Object locIdObj = parseLocationId(locationId);
        Query query = new Query(new Criteria().andOperator(
                policyCriteria(policyNumber),
                new Criteria().orOperator(
                        Criteria.where("locations.locationId").is(locIdObj),
                        Criteria.where("locations.locationId").is(locationId)
                )
        ));
        Update update = new Update()
                .push("locations.$.coverages", coverage)
                .currentDate("updatedAt");
        UpdateResult result = mongoTemplate.updateFirst(query, update, Policy.class);
        return result.getModifiedCount() > 0;
    }

    @Override
    public boolean updateCoverage(String policyNumber, String locationId, String coverageCode, Coverage coverage) {
        Query query = new Query(policyCriteria(policyNumber));
        Object locIdObj = parseLocationId(locationId);

        Update update = new Update()
                .set("locations.$[loc].coverages.$[cov].coverageName", coverage.getCoverageName())
                .set("locations.$[loc].coverages.$[cov].coverageType", coverage.getCoverageType())
                .set("locations.$[loc].coverages.$[cov].limit", coverage.getLimit())
                .set("locations.$[loc].coverages.$[cov].deductible", coverage.getDeductible())
                .set("locations.$[loc].coverages.$[cov].termMonths", coverage.getTermMonths())
                .set("locations.$[loc].coverages.$[cov].baseRate", coverage.getBaseRate())
                .set("locations.$[loc].coverages.$[cov].premium", coverage.getPremium())
                .filterArray(new Criteria().orOperator(
                        Criteria.where("loc.locationId").is(locIdObj),
                        Criteria.where("loc.locationId").is(locationId)
                ))
                .filterArray(Criteria.where("cov.coverageCode").is(coverageCode))
                .currentDate("updatedAt");

        UpdateResult result = mongoTemplate.updateFirst(query, update, Policy.class);
        return result.getModifiedCount() > 0;
    }

    @Override
    public boolean removeCoverage(String policyNumber, String locationId, String coverageCode) {
        Object locIdObj = parseLocationId(locationId);
        Query query = new Query(new Criteria().andOperator(
                policyCriteria(policyNumber),
                new Criteria().orOperator(
                        Criteria.where("locations.locationId").is(locIdObj),
                        Criteria.where("locations.locationId").is(locationId)
                )
        ));
        Update update = new Update()
                .pull("locations.$.coverages", new Query(Criteria.where("coverageCode").is(coverageCode)))
                .currentDate("updatedAt");
        UpdateResult result = mongoTemplate.updateFirst(query, update, Policy.class);
        return result.getModifiedCount() > 0;
    }

    @Override
    public boolean updateTotalPremium(String policyNumber, Double totalPremium) {
        Query query = new Query(policyCriteria(policyNumber));
        Update update = new Update()
                .set("totalPremium", totalPremium)
                .currentDate("updatedAt");
        UpdateResult result = mongoTemplate.updateFirst(query, update, Policy.class);
        return result.getModifiedCount() > 0;
    }

    private Criteria policyCriteria(String policyIdOrNumber) {
        return new Criteria().orOperator(
                Criteria.where("policyNumber").is(policyIdOrNumber),
                Criteria.where("_id").is(policyIdOrNumber)
        );
    }

    private Object parseLocationId(String locationId) {
        if (locationId == null) {
            return null;
        }
        try {
            return Integer.parseInt(locationId);
        } catch (NumberFormatException e) {
            return locationId;
        }
    }
}
