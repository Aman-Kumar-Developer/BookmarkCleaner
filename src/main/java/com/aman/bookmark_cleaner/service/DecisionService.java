package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.dto.DecisionUpdateResponse;
import com.aman.bookmark_cleaner.exception.ComparisonItemNotFoundException;
import com.aman.bookmark_cleaner.exception.InvalidDecisionException;
import com.aman.bookmark_cleaner.model.ComparisonItem;
import com.aman.bookmark_cleaner.model.ScanSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service responsible for validating and updating user decisions on comparison items.
 */
@Service
public class DecisionService {

    private final ScanSessionManager sessionManager;

    @Autowired
    public DecisionService(ScanSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Updates the decision state (ON/OFF) for an actionable comparison item.
     *
     * @param scanId  the scan session ID
     * @param itemId  the comparison item ID
     * @param enabled the new decision state
     * @return the updated decision response
     * @throws InvalidDecisionException if the item has no applicable action
     * @throws ComparisonItemNotFoundException if the item does not exist in the session
     */
    public DecisionUpdateResponse updateDecision(String scanId, String itemId, Boolean enabled) {
        if (enabled == null) {
            throw new InvalidDecisionException("Decision 'enabled' flag must not be null");
        }

        ScanSession session = sessionManager.getSession(scanId);
        ComparisonItem item = session.getComparisonItem(itemId);

        if (item == null) {
            throw new ComparisonItemNotFoundException(scanId, itemId);
        }

        if (!item.isApplicable()) {
            throw new InvalidDecisionException("Cannot modify decision for disabled/non-applicable item: " + itemId);
        }

        item.setEnabled(enabled);

        String stateStr = enabled ? "enabled (apply change)" : "disabled (retain original)";
        return new DecisionUpdateResponse(itemId, item.isApplicable(), item.isEnabled(), "Decision updated to: " + stateStr);
    }
}
