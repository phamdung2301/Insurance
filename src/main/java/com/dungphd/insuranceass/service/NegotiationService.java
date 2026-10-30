package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.NegotiationOfferRequest;
import com.dungphd.insuranceass.dto.response.NegotiationResponse;

import java.util.List;

public interface NegotiationService {

    NegotiationResponse createNegotiation(String policyNumber, NegotiationOfferRequest request, String actor);

    List<NegotiationResponse> listNegotiations(String policyNumber);

    NegotiationResponse addOffer(String policyNumber, String negotiationId, NegotiationOfferRequest request, String actor);

    NegotiationResponse acceptNegotiation(String policyNumber, String negotiationId, String actor);

    NegotiationResponse rejectNegotiation(String policyNumber, String negotiationId, String actor);
}
