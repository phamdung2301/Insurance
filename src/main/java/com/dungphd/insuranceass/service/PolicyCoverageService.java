package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.model.Coverage;
import com.dungphd.insuranceass.model.Policy;

import java.util.List;

public interface PolicyCoverageService {

    List<Coverage> getCoverages(String policyIdOrNumber, String locationId);

    Coverage getCoverageByCode(String policyIdOrNumber, String locationId, String coverageCode);

    Policy addCoverage(String policyIdOrNumber, String locationId, CoverageDto coverageDto);

    Policy updateCoverage(String policyIdOrNumber, String locationId, String coverageCode, CoverageDto coverageDto);

    Policy removeCoverage(String policyIdOrNumber, String locationId, String coverageCode);
}
