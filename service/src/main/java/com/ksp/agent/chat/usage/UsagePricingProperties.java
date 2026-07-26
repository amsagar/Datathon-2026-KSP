package com.ksp.agent.chat.usage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-million-token pricing used purely for the "estimated cost" figures shown on the Usage page —
 * Datathon's in-house LLM (Zoho QuickML) doesn't bill per-token itself, so these are indicative
 * rates an admin can tune to approximate the underlying compute cost, not a real invoice.
 *
 * <p>Defaults ({@code $2.50}/M input, {@code $10.00}/M output tokens) mirror a mid-tier hosted
 * frontier model's list price at time of writing — a reasonable placeholder until an admin sets
 * real rates for the models actually deployed via {@code ksp.usage.pricing.models.<model-name>}.
 *
 * <pre>
 * ksp:
 *   usage:
 *     pricing:
 *       default-rates:
 *         input-per-million: 2.50
 *         output-per-million: 10.00
 *       models:
 *         quickml-gpt-x:
 *           input-per-million: 1.00
 *           output-per-million: 4.00
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "ksp.usage.pricing")
public class UsagePricingProperties {

    private Rates defaultRates = new Rates(2.50, 10.00);

    /** Per-model overrides, keyed by {@code model_name} as stored on {@code llm_usage_event}. */
    private Map<String, Rates> models = new LinkedHashMap<>();

    /** Exact-match -> case-insensitive-match -> {@link #defaultRates}. */
    public Rates resolve(String modelName) {
        if (modelName == null) {
            return defaultRates;
        }
        Rates exact = models.get(modelName);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, Rates> entry : models.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(modelName)) {
                return entry.getValue();
            }
        }
        return defaultRates;
    }

    @Data
    public static class Rates {
        private double inputPerMillion;
        private double outputPerMillion;

        public Rates() {
        }

        public Rates(double inputPerMillion, double outputPerMillion) {
            this.inputPerMillion = inputPerMillion;
            this.outputPerMillion = outputPerMillion;
        }
    }
}
