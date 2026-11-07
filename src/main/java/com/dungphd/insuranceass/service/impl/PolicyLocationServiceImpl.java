package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Coverage;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.PolicyLocationService;
import com.dungphd.insuranceass.service.PremiumCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyLocationServiceImpl implements PolicyLocationService {

    private final PolicyRepository policyRepository;
    private final PremiumCalculationService premiumCalculationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dungphd.insuranceass.service.TransactionLedgerService transactionLedgerService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dungphd.insuranceass.service.PermissionService permissionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dungphd.insuranceass.repository.PriceNegotiationRepository negotiationRepository;

    @Override
    public List<Location> getLocations(String policyIdOrNumber) {
        Policy policy = findPolicy(policyIdOrNumber);
        checkRead(policy);
        return policy.getLocations() != null ? policy.getLocations() : Collections.emptyList();
    }

    @Override
    public Location getLocationById(String policyIdOrNumber, Integer locationId) {
        Policy policy = findPolicy(policyIdOrNumber);
        checkRead(policy);
        return findLocationInPolicy(policy, locationId);
    }

    @Override
    public Policy addLocation(String policyIdOrNumber, LocationDto locationDto) {
        Policy policy = findPolicy(policyIdOrNumber);
        validatePolicyModifiable(policy);

        if (policy.getLocations() == null) {
            policy.setLocations(new ArrayList<>());
        }

        // Determine or validate locationId
        int newLocationId;
        if (locationDto.getLocationId() != null) {
            final int requestedId = locationDto.getLocationId();
            boolean exists = policy.getLocations().stream()
                    .anyMatch(loc -> requestedId == loc.getLocationId());
            if (exists) {
                throw new BadRequestException("Địa điểm số " + requestedId + " đã tồn tại trong hợp đồng " + policy.getPolicyNumber());
            }
            newLocationId = requestedId;
        } else {
            // Auto-increment locationId: max + 1
            int maxId = policy.getLocations().stream()
                    .filter(loc -> loc.getLocationId() != null)
                    .mapToInt(Location::getLocationId)
                    .max()
                    .orElse(0);
            newLocationId = maxId + 1;
        }

        List<Coverage> coverages = new ArrayList<>();
        if (locationDto.getCoverages() != null) {
            coverages = locationDto.getCoverages().stream()
                    .map(CoverageDto::toEntity)
                    .peek(this::applyServerPricing)
                    .collect(Collectors.toList());
        }

        Location newLocation = Location.builder()
                .locationId(newLocationId)
                .address(locationDto.getAddress() != null ? locationDto.getAddress().trim() : "")
                .coverages(coverages)
                .build();

        policy.getLocations().add(newLocation);
        policy.recalculateTotalPremium();
        policy.setUpdatedAt(Instant.now());

        log.info("Added location {} to policy {}", newLocationId, policy.getPolicyNumber());
        Policy saved = policyRepository.save(policy);
        if (transactionLedgerService != null) {
            transactionLedgerService.recordTransaction(
                    saved.getPolicyNumber(),
                    saved.getVersion(),
                    com.dungphd.insuranceass.model.TransactionType.ADD_LOCATION,
                    "System",
                    "Added location " + newLocationId + ": " + newLocation.getAddress()
            );
        }
        return saved;
    }

    @Override
    public Policy updateLocation(String policyIdOrNumber, Integer locationId, LocationDto locationDto) {
        Policy policy = findPolicy(policyIdOrNumber);
        validatePolicyModifiable(policy);

        Location existingLocation = findLocationInPolicy(policy, locationId);

        if (locationDto.getAddress() != null && !locationDto.getAddress().isBlank()) {
            existingLocation.setAddress(locationDto.getAddress().trim());
        }

        if (locationDto.getCoverages() != null) {
            List<Coverage> updatedCoverages = locationDto.getCoverages().stream()
                    .map(CoverageDto::toEntity)
                    .peek(this::applyServerPricing)
                    .collect(Collectors.toList());
            existingLocation.setCoverages(updatedCoverages);
        }

        policy.recalculateTotalPremium();
        policy.setUpdatedAt(Instant.now());
        log.info("Updated location {} in policy {}", locationId, policy.getPolicyNumber());
        Policy saved = policyRepository.save(policy);
        if (transactionLedgerService != null) {
            transactionLedgerService.recordTransaction(
                    saved.getPolicyNumber(),
                    saved.getVersion(),
                    com.dungphd.insuranceass.model.TransactionType.UPDATE_LOCATION,
                    "System",
                    "Updated location " + locationId
            );
        }
        return saved;
    }

    @Override
    public Policy removeLocation(String policyIdOrNumber, Integer locationId) {
        Policy policy = findPolicy(policyIdOrNumber);
        validatePolicyModifiable(policy);

        if (policy.getLocations() == null || policy.getLocations().isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy địa điểm số " + locationId + " trong hợp đồng " + policy.getPolicyNumber());
        }

        boolean removed = policy.getLocations().removeIf(loc -> locationId.equals(loc.getLocationId()));
        if (!removed) {
            throw new ResourceNotFoundException("Không tìm thấy địa điểm số " + locationId + " trong hợp đồng " + policy.getPolicyNumber());
        }

        policy.recalculateTotalPremium();
        policy.setUpdatedAt(Instant.now());
        log.info("Removed location {} from policy {}", locationId, policy.getPolicyNumber());
        Policy saved = policyRepository.save(policy);
        if (transactionLedgerService != null) {
            transactionLedgerService.recordTransaction(
                    saved.getPolicyNumber(),
                    saved.getVersion(),
                    com.dungphd.insuranceass.model.TransactionType.REMOVE_LOCATION,
                    "System",
                    "Removed location " + locationId
            );
        }
        return saved;
    }

    private Policy findPolicy(String policyIdOrNumber) {
        if (policyIdOrNumber == null || policyIdOrNumber.isBlank()) {
            throw new BadRequestException("Vui lòng cung cấp số hợp đồng.");
        }

        // Try MongoDB ObjectId / String ID first
        Optional<Policy> optionalPolicy = policyRepository.findById(policyIdOrNumber);
        if (optionalPolicy.isPresent()) {
            return optionalPolicy.get();
        }

        // Try business policyNumber
        return policyRepository.findByPolicyNumber(policyIdOrNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng: " + policyIdOrNumber));
    }

    private Location findLocationInPolicy(Policy policy, Integer locationId) {
        if (locationId == null) {
            throw new BadRequestException("Vui lòng cung cấp mã địa điểm.");
        }

        if (policy.getLocations() == null || policy.getLocations().isEmpty()) {
            throw new ResourceNotFoundException("Hợp đồng " + policy.getPolicyNumber() + " chưa có địa điểm nào.");
        }

        return policy.getLocations().stream()
                .filter(loc -> locationId.equals(loc.getLocationId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm số " + locationId + " trong hợp đồng " + policy.getPolicyNumber()));
    }

    private void validatePolicyModifiable(Policy policy) {
        // Siết về DRAFT/QUOTED/BOUND (PLAN §1.4): ACTIVE phải đi endorsement, không sửa trực tiếp.
        if (policy.getStatus() != PolicyStatus.DRAFT && policy.getStatus() != PolicyStatus.QUOTED
                && policy.getStatus() != PolicyStatus.BOUND) {
            throw new BadRequestException("Không thể sửa địa điểm khi hợp đồng ở trạng thái " + policy.getStatus()
                    + ". Vui lòng dùng endorsement để thay đổi hợp đồng đã có hiệu lực.");
        }
        checkWrite(policy);
        // Khóa giá sau ACCEPTED, check TRƯỚC khi recalculateTotalPremium chạy (PLAN §2.3).
        if (negotiationRepository != null && policy.getStatus() == PolicyStatus.QUOTED
                && (negotiationRepository.existsByPolicyNumberAndStatus(policy.getPolicyNumber(), com.dungphd.insuranceass.model.NegotiationStatus.ACCEPTED)
                    || negotiationRepository.existsByPolicyNumberAndStatus(policy.getPolicyNumber(), com.dungphd.insuranceass.model.NegotiationStatus.PENDING_CONFIRMATION))) {
            throw new BadRequestException("Giá hợp đồng đã chốt hoặc đang chờ xác nhận qua thương lượng, không được sửa đổi phí khi còn ở trạng thái QUOTED.");
        }
    }

    /**
     * Giá do SERVER quyết định: luôn tính lại premium theo bảng giá nội bộ,
     * bỏ qua premium/baseRate client gửi (chống giả mạo phí bảo hiểm).
     */
    private void applyServerPricing(Coverage coverage) {
        if (coverage == null) return;
        coverage.setBaseRate(premiumCalculationService.resolveServerBaseRate(coverage.getCoverageCode()));
        coverage.setPremium(premiumCalculationService.calculateServerPriced(coverage));
    }

    private void checkRead(Policy policy) {
        if (permissionService != null) {
            permissionService.checkPolicyRead(policy);
        }
    }

    private void checkWrite(Policy policy) {
        if (permissionService != null) {
            permissionService.checkPolicyWrite(policy);
        }
    }
}
