package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.Coverage;
import com.dungphd.insuranceass.model.Location;

public interface PolicyCustomRepository {
    boolean addLocation(String policyNumber, Location location);
    boolean updateLocation(String policyNumber, String locationId, Location location);
    boolean removeLocation(String policyNumber, String locationId);

    boolean addCoverage(String policyNumber, String locationId, Coverage coverage);
    boolean updateCoverage(String policyNumber, String locationId, String coverageCode, Coverage coverage);
    boolean removeCoverage(String policyNumber, String locationId, String coverageCode);

    boolean updateTotalPremium(String policyNumber, Double totalPremium);
}
