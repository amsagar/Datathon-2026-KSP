package com.ksp.agent.document.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Catalyst Stratus (object storage) configuration, confirmed empirically against a live bucket:
 * objects are read/written directly on the bucket's own virtual-hosted domain (the "Bucket URL"
 * shown in the console, e.g. {@code https://ksp-bucket-development.zohostratus.in}) via
 * {@code PUT}/{@code GET}/{@code DELETE <bucket-url>/<key>}, authenticated the same way as every
 * other Catalyst API: {@code Authorization: Zoho-oauthtoken <token>} + {@code CATALYST-ORG} header.
 * When {@code bucket-url} is blank, {@link DocumentBlobStore} falls back to the local filesystem.
 */
@Data
@ConfigurationProperties(prefix = "agent.stratus")
public class StratusProperties {

    /** The bucket's own domain, exactly as shown in the console's "Bucket URL" dialog. */
    private String bucketUrl;

    /** Catalyst org id, sent as the {@code CATALYST-ORG} header. */
    private String catalystOrg;

    /** Static bearer token fallback. Ignored when the shared Zoho OAuth refresh is configured. */
    private String apiKey;
}
