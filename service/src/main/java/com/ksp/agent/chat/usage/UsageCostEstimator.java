package com.ksp.agent.chat.usage;

import org.springframework.stereotype.Component;

/**
 * Estimates a $ cost for an LLM call from its token counts and the configured per-model rate.
 * Deliberately has no cached-token term: Datathon's in-house LLM (Zoho QuickML, see
 * {@code QuickMlChatModel}) never reports a cached-token count in its usage payload, so a
 * cache-aware pricing term would always be zero — this was a considered omission, not an
 * oversight.
 */
@Component
public class UsageCostEstimator {

    private final UsagePricingProperties pricingProperties;

    public UsageCostEstimator(UsagePricingProperties pricingProperties) {
        this.pricingProperties = pricingProperties;
    }

    public double estimate(String modelName, long promptTokens, long completionTokens) {
        UsagePricingProperties.Rates rates = pricingProperties.resolve(modelName);
        return (promptTokens * rates.getInputPerMillion() + completionTokens * rates.getOutputPerMillion())
                / 1_000_000.0;
    }
}
