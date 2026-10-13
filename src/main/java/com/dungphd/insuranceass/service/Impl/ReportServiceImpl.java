package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.response.MonthlyPremiumReportDto;
import com.dungphd.insuranceass.dto.response.PolicyStatusCountReportDto;
import com.dungphd.insuranceass.dto.response.StatusPremiumReportDto;
import com.dungphd.insuranceass.dto.response.TopPolicyReportDto;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<PolicyStatusCountReportDto> getPolicyCountByStatus() {
        try {
            Aggregation agg = Aggregation.newAggregation(
                    Aggregation.group("status").count().as("count"),
                    Aggregation.project("count").and("_id").as("status"),
                    Aggregation.sort(Sort.Direction.DESC, "count")
            );

            AggregationResults<Document> results = mongoTemplate.aggregate(agg, "policies", Document.class);
            List<Document> docs = results.getMappedResults();

            long total = docs.stream().mapToLong(d -> ((Number) d.getOrDefault("count", 0)).longValue()).sum();

            List<PolicyStatusCountReportDto> dtos = new ArrayList<>();
            for (Document doc : docs) {
                String status = doc.getString("status");
                long count = ((Number) doc.getOrDefault("count", 0)).longValue();
                double pct = total > 0 ? Math.round((count * 100.0 / total) * 10.0) / 10.0 : 0.0;

                dtos.add(PolicyStatusCountReportDto.builder()
                        .status(status != null ? status : "UNKNOWN")
                        .count(count)
                        .percentage(pct)
                        .build());
            }

            return dtos;
        } catch (Exception e) {
            log.error("Error executing policyCountByStatus aggregation", e);
            return List.of();
        }
    }

    @Override
    public List<StatusPremiumReportDto> getTotalPremiumByStatus() {
        try {
            Aggregation agg = Aggregation.newAggregation(
                    Aggregation.group("status")
                            .sum("totalPremium").as("totalPremium")
                            .count().as("policyCount")
                            .avg("totalPremium").as("averagePremium"),
                    Aggregation.project("totalPremium", "policyCount", "averagePremium").and("_id").as("status"),
                    Aggregation.sort(Sort.Direction.DESC, "totalPremium")
            );

            AggregationResults<Document> results = mongoTemplate.aggregate(agg, "policies", Document.class);
            List<Document> docs = results.getMappedResults();

            List<StatusPremiumReportDto> dtos = new ArrayList<>();
            for (Document doc : docs) {
                String status = doc.getString("status");
                double premium = ((Number) doc.getOrDefault("totalPremium", 0.0)).doubleValue();
                long count = ((Number) doc.getOrDefault("policyCount", 0)).longValue();
                double avg = ((Number) doc.getOrDefault("averagePremium", 0.0)).doubleValue();

                dtos.add(StatusPremiumReportDto.builder()
                        .status(status != null ? status : "UNKNOWN")
                        .totalPremium(Math.round(premium * 100.0) / 100.0)
                        .policyCount(count)
                        .averagePremium(Math.round(avg * 100.0) / 100.0)
                        .build());
            }

            return dtos;
        } catch (Exception e) {
            log.error("Error executing totalPremiumByStatus aggregation", e);
            return List.of();
        }
    }

    @Override
    public List<TopPolicyReportDto> getTop5HighestPremiumPolicies() {
        try {
            Aggregation agg = Aggregation.newAggregation(
                    Aggregation.match(Criteria.where("totalPremium").gt(0)),
                    Aggregation.sort(Sort.Direction.DESC, "totalPremium"),
                    Aggregation.limit(5)
            );

            AggregationResults<Policy> results = mongoTemplate.aggregate(agg, "policies", Policy.class);
            List<Policy> policies = results.getMappedResults();

            List<TopPolicyReportDto> dtos = new ArrayList<>();
            for (Policy p : policies) {
                int locCount = p.getLocations() != null ? p.getLocations().size() : 0;
                int covCount = 0;
                if (p.getLocations() != null) {
                    covCount = p.getLocations().stream()
                            .mapToInt(l -> l.getCoverages() != null ? l.getCoverages().size() : 0)
                            .sum();
                }

                String effDateStr = p.getEffectiveDate() != null
                        ? DateTimeFormatter.ISO_INSTANT.format(p.getEffectiveDate())
                        : "N/A";

                dtos.add(TopPolicyReportDto.builder()
                        .policyNumber(p.getPolicyNumber())
                        .insuredName(p.getInsured() != null ? p.getInsured().getName() : "N/A")
                        .status(p.getStatus() != null ? p.getStatus().name() : "DRAFT")
                        .totalPremium(p.getTotalPremium() != null ? p.getTotalPremium() : 0.0)
                        .locationCount(locCount)
                        .coverageCount(covCount)
                        .effectiveDate(effDateStr)
                        .build());
            }

            return dtos;
        } catch (Exception e) {
            log.error("Error executing top5HighestPremiumPolicies aggregation", e);
            return List.of();
        }
    }

    @Override
    public List<MonthlyPremiumReportDto> getPremiumByEffectiveMonth() {
        try {
            Aggregation agg = Aggregation.newAggregation(
                    Aggregation.match(Criteria.where("effectiveDate").ne(null)),
                    Aggregation.project("totalPremium")
                            .andExpression("year(effectiveDate)").as("year")
                            .andExpression("month(effectiveDate)").as("month"),
                    Aggregation.group("year", "month")
                            .sum("totalPremium").as("totalPremium")
                            .count().as("policyCount"),
                    Aggregation.sort(Sort.Direction.ASC, "_id.year", "_id.month")
            );

            AggregationResults<Document> results = mongoTemplate.aggregate(agg, "policies", Document.class);
            List<Document> docs = results.getMappedResults();

            List<MonthlyPremiumReportDto> dtos = new ArrayList<>();
            for (Document doc : docs) {
                Object idObj = doc.get("_id");
                int year = 2026;
                int month = 1;
                if (idObj instanceof Document idDoc) {
                    year = ((Number) idDoc.getOrDefault("year", 2026)).intValue();
                    month = ((Number) idDoc.getOrDefault("month", 1)).intValue();
                }

                double premium = ((Number) doc.getOrDefault("totalPremium", 0.0)).doubleValue();
                long count = ((Number) doc.getOrDefault("policyCount", 0)).longValue();

                dtos.add(MonthlyPremiumReportDto.builder()
                        .year(year)
                        .month(month)
                        .monthLabel(String.format("T%02d/%d", month, year))
                        .totalPremium(Math.round(premium * 100.0) / 100.0)
                        .policyCount(count)
                        .build());
            }

            return dtos;
        } catch (Exception e) {
            log.error("Error executing premiumByEffectiveMonth aggregation", e);
            return List.of();
        }
    }
}
