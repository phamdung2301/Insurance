package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.MonthlyPremiumReportDto;
import com.dungphd.insuranceass.dto.response.PolicyStatusCountReportDto;
import com.dungphd.insuranceass.dto.response.StatusPremiumReportDto;
import com.dungphd.insuranceass.dto.response.TopPolicyReportDto;

import java.util.List;

public interface ReportService {

    /**
     * FR-REP-01: Groups policies by status and counts total records in each status.
     */
    List<PolicyStatusCountReportDto> getPolicyCountByStatus();

    /**
     * FR-REP-02: Reports the sum of totalPremium for each policy status.
     */
    List<StatusPremiumReportDto> getTotalPremiumByStatus();

    /**
     * FR-REP-03: Reports the top five insurance policies with the highest premium amounts.
     */
    List<TopPolicyReportDto> getTop5HighestPremiumPolicies();

    /**
     * FR-REP-04: Reports total premium volume by month using MongoDB $month on effectiveDate.
     */
    List<MonthlyPremiumReportDto> getPremiumByEffectiveMonth();
}
