package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.response.NegotiationResponse;
import com.dungphd.insuranceass.exception.InvalidRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.NegotiationMessage;
import com.dungphd.insuranceass.model.NegotiationOffer;
import com.dungphd.insuranceass.model.NegotiationSide;
import com.dungphd.insuranceass.model.NegotiationStatus;
import com.dungphd.insuranceass.model.OfferStatus;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.model.TransactionType;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.NegotiationService;
import com.dungphd.insuranceass.service.TransactionLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NegotiationServiceImpl implements NegotiationService {

    private final PolicyRepository policyRepository;
    private final TransactionLedgerService transactionLedgerService;

    // ==========================================
    // Read
    // ==========================================

    @Override
    public NegotiationResponse getNegotiation(String policyNumber, String actorEmail, boolean isAdmin) {
        requireSignedIn(actorEmail);
        Policy policy = loadPolicy(policyNumber);
        NegotiationSide side = requireParty(policy, actorEmail, isAdmin);
        return toResponse(policy, side);
    }

    // ==========================================
    // Floor price (gia goc)
    // ==========================================

    @Override
    public NegotiationResponse setFloorPrice(String policyNumber, BigDecimal floorPrice, String actorEmail, boolean isAdmin) {
        requireSignedIn(actorEmail);
        Policy policy = loadPolicy(policyNumber);
        requireQuoted(policy);
        requireNotLocked(policy);
        ensureNegotiationFields(policy);
        if (floorPrice == null || floorPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Floor price must be greater than 0");
        }
        NegotiationSide side = assignOrCheckAgent(policy, actorEmail, isAdmin);
        if (side != NegotiationSide.AGENT) {
            throw new InvalidRequestException("Only the agent side can set the floor price");
        }
        if (policy.getNegotiationStatus() != NegotiationStatus.NOT_STARTED) {
            throw new InvalidRequestException("Floor price can only be set before negotiation starts");
        }
        policy.setFloorPrice(floorPrice);
        save(policy);
        log.info("Floor price {} set for policy {} by {}", floorPrice, policy.getPolicyNumber(), actorEmail);
        return toResponse(policy, NegotiationSide.AGENT);
    }

    // ==========================================
    // Offers
    // ==========================================

    @Override
    public NegotiationResponse submitOffer(String policyNumber, BigDecimal price, String message, String actorEmail, boolean isAdmin) {
        requireSignedIn(actorEmail);
        Policy policy = loadPolicy(policyNumber);
        requireQuoted(policy);
        requireNotLocked(policy);
        ensureNegotiationFields(policy);
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Price must be greater than 0");
        }

        NegotiationSide side = assignOrCheckAgent(policy, actorEmail, isAdmin);
        if (side == NegotiationSide.CUSTOMER) {
            // Customer side is pinned to the insured email; nothing to assign.
        } else if (policy.getFloorPrice() != null && price.compareTo(policy.getFloorPrice()) < 0) {
            throw new InvalidRequestException("Price must not be below the floor price " + policy.getFloorPrice());
        }

        NegotiationOffer pending = pendingOffer(policy);
        boolean firstOffer = policy.getPriceOffers().isEmpty();
        if (policy.getNegotiationStatus() == NegotiationStatus.PENDING_CONFIRMATION) {
            throw new InvalidRequestException("An offer is awaiting confirmation, please confirm or decline it first");
        }
        if (pending != null) {
            if (pending.getSide() == side) {
                throw new InvalidRequestException("Please wait for the other side to respond to your offer");
            }
            // A counter supersedes the pending offer; it is never deleted.
            pending.setStatus(OfferStatus.COUNTERED);
        }

        NegotiationOffer offer = NegotiationOffer.builder()
                .offerId(UUID.randomUUID().toString())
                .side(side)
                .senderEmail(actorEmail)
                .price(price)
                .message(message)
                .status(OfferStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        policy.getPriceOffers().add(offer);
        policy.setNegotiationStatus(NegotiationStatus.IN_NEGOTIATION);
        save(policy);

        transactionLedgerService.recordTransaction(
                policy.getPolicyNumber(),
                policy.getVersion(),
                firstOffer ? TransactionType.PRICE_OFFER : TransactionType.PRICE_COUNTER,
                actorEmail,
                side + " proposed price " + price
        );
        log.info("Policy {}: {} proposed price {}", policy.getPolicyNumber(), side, price);
        return toResponse(policy, side);
    }

    @Override
    public NegotiationResponse acceptOffer(String policyNumber, String offerId, String actorEmail, boolean isAdmin) {
        requireSignedIn(actorEmail);
        Policy policy = loadPolicy(policyNumber);
        requireQuoted(policy);
        requireNotLocked(policy);
        ensureNegotiationFields(policy);
        NegotiationSide side = requireParty(policy, actorEmail, isAdmin);

        NegotiationOffer offer = findOffer(policy, offerId);
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new InvalidRequestException("Offer is no longer pending");
        }
        if (offer.getSide() == side) {
            throw new InvalidRequestException("You cannot accept your own offer");
        }
        if (side == NegotiationSide.AGENT
                && policy.getFloorPrice() != null
                && offer.getPrice().compareTo(policy.getFloorPrice()) < 0) {
            throw new InvalidRequestException("Cannot accept a price below the floor price " + policy.getFloorPrice());
        }

        offer.setStatus(OfferStatus.ACCEPTED);
        policy.setNegotiationStatus(NegotiationStatus.PENDING_CONFIRMATION);
        save(policy);

        transactionLedgerService.recordTransaction(
                policy.getPolicyNumber(),
                policy.getVersion(),
                TransactionType.PRICE_ACCEPT,
                actorEmail,
                side + " accepted price " + offer.getPrice() + " (awaiting confirmation)"
        );
        log.info("Policy {}: {} accepted price {} (1/2)", policy.getPolicyNumber(), side, offer.getPrice());
        return toResponse(policy, side);
    }

    @Override
    public NegotiationResponse rejectOffer(String policyNumber, String offerId, String actorEmail, boolean isAdmin) {
        requireSignedIn(actorEmail);
        Policy policy = loadPolicy(policyNumber);
        requireQuoted(policy);
        requireNotLocked(policy);
        ensureNegotiationFields(policy);
        NegotiationSide side = requireParty(policy, actorEmail, isAdmin);

        NegotiationOffer offer = findOffer(policy, offerId);
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new InvalidRequestException("Offer is no longer pending");
        }
        if (offer.getSide() == side) {
            throw new InvalidRequestException("You cannot reject your own offer");
        }

        offer.setStatus(OfferStatus.REJECTED);
        policy.setNegotiationStatus(NegotiationStatus.IN_NEGOTIATION);
        save(policy);

        transactionLedgerService.recordTransaction(
                policy.getPolicyNumber(),
                policy.getVersion(),
                TransactionType.PRICE_REJECT,
                actorEmail,
                side + " rejected price " + offer.getPrice()
        );
        return toResponse(policy, side);
    }

    // ==========================================
    // Dual confirmation -> lock
    // ==========================================

    @Override
    public NegotiationResponse confirmLock(String policyNumber, String actorEmail, boolean isAdmin) {
        requireSignedIn(actorEmail);
        Policy policy = loadPolicy(policyNumber);
        requireQuoted(policy);
        requireNotLocked(policy);
        ensureNegotiationFields(policy);
        NegotiationSide side = requireParty(policy, actorEmail, isAdmin);

        if (policy.getNegotiationStatus() != NegotiationStatus.PENDING_CONFIRMATION) {
            throw new InvalidRequestException("No accepted offer awaiting confirmation");
        }
        NegotiationOffer accepted = acceptedOffer(policy);
        if (accepted == null) {
            throw new InvalidRequestException("No accepted offer awaiting confirmation");
        }
        if (!accepted.getSenderEmail().equals(actorEmail)) {
            throw new InvalidRequestException("Only the proposer can confirm the lock");
        }

        policy.setNegotiationStatus(NegotiationStatus.AGREED_LOCKED);
        policy.setNegotiatedPremium(accepted.getPrice());
        save(policy);

        transactionLedgerService.recordTransaction(
                policy.getPolicyNumber(),
                policy.getVersion(),
                TransactionType.PRICE_CONFIRM,
                actorEmail,
                "Price locked at " + accepted.getPrice() + " after dual confirmation"
        );
        log.info("Policy {}: price locked at {} (2/2)", policy.getPolicyNumber(), accepted.getPrice());
        return toResponse(policy, side);
    }

    @Override
    public NegotiationResponse declineConfirmation(String policyNumber, String actorEmail, boolean isAdmin) {
        requireSignedIn(actorEmail);
        Policy policy = loadPolicy(policyNumber);
        requireQuoted(policy);
        requireNotLocked(policy);
        ensureNegotiationFields(policy);
        NegotiationSide side = requireParty(policy, actorEmail, isAdmin);

        if (policy.getNegotiationStatus() != NegotiationStatus.PENDING_CONFIRMATION) {
            throw new InvalidRequestException("No accepted offer awaiting confirmation");
        }
        NegotiationOffer accepted = acceptedOffer(policy);
        if (accepted == null) {
            throw new InvalidRequestException("No accepted offer awaiting confirmation");
        }
        if (!accepted.getSenderEmail().equals(actorEmail)) {
            throw new InvalidRequestException("Only the proposer can decline the confirmation");
        }

        accepted.setStatus(OfferStatus.REJECTED);
        policy.setNegotiationStatus(NegotiationStatus.IN_NEGOTIATION);
        save(policy);

        transactionLedgerService.recordTransaction(
                policy.getPolicyNumber(),
                policy.getVersion(),
                TransactionType.PRICE_REJECT,
                actorEmail,
                "Confirmation declined for price " + accepted.getPrice() + ", negotiation reopened"
        );
        return toResponse(policy, side);
    }

    // ==========================================
    // Chat (saved with the policy, read-only after lock)
    // ==========================================

    @Override
    public NegotiationResponse sendMessage(String policyNumber, String content, String actorEmail, boolean isAdmin) {
        requireSignedIn(actorEmail);
        Policy policy = loadPolicy(policyNumber);
        requireQuoted(policy);
        requireNotLocked(policy);
        ensureNegotiationFields(policy);
        if (content == null || content.isBlank()) {
            throw new InvalidRequestException("Message content is required");
        }
        NegotiationSide side = assignOrCheckAgent(policy, actorEmail, isAdmin);

        NegotiationMessage message = NegotiationMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .side(side)
                .senderEmail(actorEmail)
                .content(content)
                .createdAt(Instant.now())
                .build();
        policy.getNegotiationMessages().add(message);
        save(policy);
        return toResponse(policy, side);
    }

    // ==========================================
    // Helpers
    // ==========================================

    private Policy loadPolicy(String policyNumber) {
        return policyRepository.findByPolicyNumber(policyNumber)
                .or(() -> policyRepository.findById(policyNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with number or ID: " + policyNumber));
    }

    private String customerEmail(Policy policy) {
        return policy.getInsured() != null ? policy.getInsured().getEmail() : null;
    }

    /**
     * Infers the caller's side. CUSTOMER is pinned to the insured email.
     * AGENT is the assigned agent email, or an admin acting as staff.
     * Returns null when the caller is not a party to this negotiation.
     */
    private NegotiationSide inferSide(Policy policy, String actorEmail, boolean isAdmin) {
        String customer = customerEmail(policy);
        if (customer != null && !customer.isBlank() && customer.equals(actorEmail)) {
            return NegotiationSide.CUSTOMER;
        }
        if (isAdmin) {
            return NegotiationSide.AGENT;
        }
        if (policy.getAgentEmail() != null && policy.getAgentEmail().equals(actorEmail)) {
            return NegotiationSide.AGENT;
        }
        return null;
    }

    private NegotiationSide requireParty(Policy policy, String actorEmail, boolean isAdmin) {
        NegotiationSide side = inferSide(policy, actorEmail, isAdmin);
        if (side == null) {
            throw new InvalidRequestException("You are not a party to this negotiation");
        }
        return side;
    }

    /**
     * Like requireParty, but the first non-admin agent-side actor becomes
     * the assigned agent. Admins act as staff without stealing assignment.
     */
    private NegotiationSide assignOrCheckAgent(Policy policy, String actorEmail, boolean isAdmin) {
        String customer = customerEmail(policy);
        if (customer != null && !customer.isBlank() && customer.equals(actorEmail)) {
            return NegotiationSide.CUSTOMER;
        }
        if (isAdmin) {
            return NegotiationSide.AGENT;
        }
        if (policy.getAgentEmail() == null) {
            policy.setAgentEmail(actorEmail);
            return NegotiationSide.AGENT;
        }
        if (policy.getAgentEmail().equals(actorEmail)) {
            return NegotiationSide.AGENT;
        }
        throw new InvalidRequestException("You are not a party to this negotiation");
    }

    private void requireSignedIn(String actorEmail) {
        if (actorEmail == null || actorEmail.isBlank() || "System".equalsIgnoreCase(actorEmail)) {
            throw new InvalidRequestException("You need to sign in to negotiate");
        }
    }

    private void requireQuoted(Policy policy) {
        if (policy.getStatus() != PolicyStatus.QUOTED) {
            throw new InvalidRequestException("Only policies in QUOTED status can be negotiated. Current status is " + policy.getStatus());
        }
    }

    private void requireNotLocked(Policy policy) {
        if (policy.getNegotiationStatus() == NegotiationStatus.AGREED_LOCKED) {
            throw new InvalidRequestException("Negotiation is already locked");
        }
    }

    /** Fills negotiation fields for policies created before this feature. */
    private void ensureNegotiationFields(Policy policy) {
        if (policy.getNegotiationStatus() == null) {
            policy.setNegotiationStatus(NegotiationStatus.NOT_STARTED);
        }
        if (policy.getPriceOffers() == null) {
            policy.setPriceOffers(new ArrayList<>());
        }
        if (policy.getNegotiationMessages() == null) {
            policy.setNegotiationMessages(new ArrayList<>());
        }
    }

    private NegotiationOffer pendingOffer(Policy policy) {
        if (policy.getPriceOffers() == null) {
            return null;
        }
        return policy.getPriceOffers().stream()
                .filter(o -> o.getStatus() == OfferStatus.PENDING)
                .findFirst()
                .orElse(null);
    }

    private NegotiationOffer acceptedOffer(Policy policy) {
        if (policy.getPriceOffers() == null) {
            return null;
        }
        return policy.getPriceOffers().stream()
                .filter(o -> o.getStatus() == OfferStatus.ACCEPTED)
                .findFirst()
                .orElse(null);
    }

    private NegotiationOffer findOffer(Policy policy, String offerId) {
        if (policy.getPriceOffers() == null || offerId == null) {
            throw new ResourceNotFoundException("Offer not found: " + offerId);
        }
        return policy.getPriceOffers().stream()
                .filter(o -> offerId.equals(o.getOfferId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found: " + offerId));
    }

    private void save(Policy policy) {
        policy.setUpdatedAt(Instant.now());
        try {
            policyRepository.save(policy);
        } catch (OptimisticLockingFailureException ex) {
            // versionLock (@Version) detected a concurrent write: the caller
            // must reload and retry instead of silently overwriting.
            throw new InvalidRequestException("Data changed, please reload and try again");
        }
    }

    private NegotiationResponse toResponse(Policy policy, NegotiationSide viewerSide) {
        NegotiationOffer pending = pendingOffer(policy);
        NegotiationStatus status = policy.getNegotiationStatus() != null
                ? policy.getNegotiationStatus()
                : NegotiationStatus.NOT_STARTED;
        int confirmations = status == NegotiationStatus.AGREED_LOCKED ? 2
                : status == NegotiationStatus.PENDING_CONFIRMATION ? 1 : 0;
        BigDecimal currentPrice = pending != null ? pending.getPrice() : policy.getNegotiatedPremium();
        return NegotiationResponse.builder()
                .policyNumber(policy.getPolicyNumber())
                .policyStatus(policy.getStatus())
                .negotiationStatus(status)
                .viewerSide(viewerSide)
                .currentPrice(currentPrice)
                .lockedPrice(policy.getNegotiatedPremium())
                // Floor price is agent-only: null for customers (NON_NULL hides it).
                .floorPrice(viewerSide == NegotiationSide.AGENT ? policy.getFloorPrice() : null)
                .agentEmail(policy.getAgentEmail())
                .customerEmail(customerEmail(policy))
                .confirmations(confirmations)
                .pendingOffer(pending)
                .offers(policy.getPriceOffers() != null ? List.copyOf(policy.getPriceOffers()) : List.of())
                .messages(policy.getNegotiationMessages() != null ? List.copyOf(policy.getNegotiationMessages()) : List.of())
                .build();
    }
}
