package com.company.cps.service;

import com.company.cps.domain.CpsPushConfig;
import com.company.cps.dto.CpsPushConfigRequest;
import com.company.cps.mapper.CpsPushConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * F 线推送渠道配置（D-22）：
 * - 单行 GLOBAL：缺失/禁用 → "推送未配置"，记忆体系按此判定是否消费推送链；
 * - enabled=true 时 endpoint 必填非空；
 * - secret_ref 仅 KMS/外部密钥引用，明文不入库。
 */
@Service
public class CpsPushConfigService {

    private static final Set<String> ALLOWED_CHANNELS = Set.of(
            CpsPushConfig.CHANNEL_INTERFACE,
            CpsPushConfig.CHANNEL_EMAIL,
            CpsPushConfig.CHANNEL_SMS,
            CpsPushConfig.CHANNEL_WEBHOOK
    );

    private final CpsPushConfigMapper mapper;

    public CpsPushConfigService(CpsPushConfigMapper mapper) {
        this.mapper = mapper;
    }

    /** 读取当前推送配置（无行/禁用=推送未配置）。 */
    public Map<String, Object> getConfig() {
        Map<String, Object> body = new LinkedHashMap<>();
        CpsPushConfig cfg = mapper.findGlobal();
        if (cfg == null) {
            body.put("configured", Boolean.FALSE);
            return body;
        }
        body.put("configured", Boolean.TRUE);
        body.put("channel", cfg.getChannel());
        body.put("endpoint", cfg.getEndpoint());
        body.put("secret_ref", cfg.getSecretRef());
        body.put("enabled", cfg.getEnabled());
        body.put("updated_by", cfg.getUpdatedBy());
        body.put("updated_at", cfg.getUpdatedAt());
        body.put("created_at", cfg.getCreatedAt());
        return body;
    }

    /**
     * Upsert 单行配置（PUT 全量替换）：
     * channel 必填且需在白名单；enabled=true 时 endpoint 必填非空（推送启用必须可达）。
     */
    @Transactional
    public Map<String, Object> updateConfig(CpsPushConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        String channel = trimToNull(request.getChannel());
        if (channel == null) {
            throw new IllegalArgumentException("channel is required");
        }
        if (!ALLOWED_CHANNELS.contains(channel)) {
            throw new IllegalArgumentException("Unsupported channel: " + request.getChannel()
                    + " (allowed: INTERFACE / EMAIL / SMS / WEBHOOK)");
        }
        Boolean enabled = request.getEnabled() != null && Boolean.TRUE.equals(request.getEnabled());
        String endpoint = trimToNull(request.getEndpoint());
        if (enabled && endpoint == null) {
            throw new IllegalArgumentException("endpoint is required when enabled=true");
        }
        CpsPushConfig cfg = new CpsPushConfig();
        cfg.setChannel(channel);
        cfg.setEndpoint(endpoint);
        cfg.setSecretRef(trimToNull(request.getSecretRef()));
        cfg.setEnabled(enabled);
        cfg.setUpdatedBy(trimToDefault(request.getUpdatedBy(), "ADMIN"));
        mapper.upsert(cfg);
        return getConfig();
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private static String trimToDefault(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }
}