package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.model.PolicyTransaction;
import com.dungphd.insuranceass.model.TransactionType;

import java.util.List;

public interface TransactionLedgerService {

    PolicyTransaction recordTransaction(String policyNumber, Integer version, TransactionType type, String actor, String description);

    List<PolicyTransaction> getTransactions(String policyNumber);
}
