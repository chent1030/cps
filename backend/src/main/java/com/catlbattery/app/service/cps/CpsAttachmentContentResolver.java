package com.catlbattery.app.service.cps;

import com.catlbattery.domain.entity.cps.CpsIssueAttachment;
import org.springframework.stereotype.Component;

/**
 * 附件内容解析器（R-N1/P0 附件断点修复）。
 *
 * 背景：附件一律走 MinIO 存储（cps_issue_attachment.file_url 直接存公开绝对 URL；content 列恒 NULL），
 * 而 CpsAgentFrameworkClient 旧逻辑对 content==null 静默跳过，导致 evidence 不再传 Python（线上无图巡检）。
 *
 * 解析规则：
 * 1. content 非空 → 直接返回（旧库 MEDIUMBLOB 存量数据的 base64 兼容路径）；
 * 2. content 为空且 file_url 非空 → 从 MinIO 读取（MinioStorageService.readByUrl）；
 * 3. 两者皆空 → 返回 null（真正无内容的空附件，保持旧语义跳过）；
 * 4. MinIO 读取失败 → 抛 IllegalStateException 快速失败：
 *    附件传递失败必须显式暴露，禁止静默降级为"无图巡检"（Phase 0 §⑦ 附件约定）。
 */
@Component
public class CpsAttachmentContentResolver {

    private final MinioStorageService storageService;

    public CpsAttachmentContentResolver(MinioStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 解析附件二进制内容；无可用内容返回 null，存储读取失败抛出异常（不静默跳过）。
     */
    public byte[] resolve(CpsIssueAttachment attachment) {
        if (attachment == null) {
            return null;
        }
        if (attachment.getContent() != null && attachment.getContent().length > 0) {
            return attachment.getContent();
        }
        String fileUrl = attachment.getFileUrl();
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return null;
        }
        try {
            return storageService.readByUrl(fileUrl);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to read attachment " + attachment.getId()
                            + " from MinIO object '" + fileUrl + "': " + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * 供测试/非 Spring 构造路径使用的占位实现：调用即抛错。
     * 仅在 agent framework 显式 disabled（不会触达附件转发）的场景下安全。
     */
    public static CpsAttachmentContentResolver unsupported() {
        return new CpsAttachmentContentResolver(null) {
            @Override
            public byte[] resolve(CpsIssueAttachment attachment) {
                throw new IllegalStateException("MinIO storage is not configured for attachment forwarding");
            }
        };
    }
}
