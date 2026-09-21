package com.company.cps.service;

import com.company.cps.domain.CpsIssueStatus;
import com.company.cps.dto.CpsAdminOverviewResponse;
import com.company.cps.dto.CpsIssueAdminPageResponse;
import com.company.cps.dto.CpsIssueListItemResponse;
import com.company.cps.mapper.CpsIssueMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CpsAdminIssueService {

    private static final int RECENT_ISSUE_LIMIT = 8;
    private static final int MAX_EXPORT_ROWS = 10000;
    private final CpsIssueMapper issueMapper;

    public CpsAdminIssueService(CpsIssueMapper issueMapper) {
        this.issueMapper = issueMapper;
    }

    public CpsIssueAdminPageResponse listIssues(
            String status, String factory, String area, String line, String process, String currentHandler, String createdFrom, String createdTo, String keyword, int page, int pageSize
    ) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        CpsIssueStatus issueStatus = parseStatus(status);
        int offset = (safePage - 1) * safePageSize;
        List<CpsIssueListItemResponse> records =
                issueMapper.listForAdmin(issueStatus, trimToNull(factory), trimToNull(area), trimToNull(line), trimToNull(process), trimToNull(currentHandler), trimToNull(createdFrom), trimToNull(createdTo), trimToNull(keyword), safePageSize, offset);
        long total = issueMapper.countForAdmin(issueStatus, trimToNull(factory), trimToNull(area), trimToNull(line), trimToNull(process), trimToNull(currentHandler), trimToNull(createdFrom), trimToNull(createdTo), trimToNull(keyword));
        return new CpsIssueAdminPageResponse(records, total, safePage, safePageSize);
    }

    public CpsAdminOverviewResponse overview() {
        CpsAdminOverviewResponse result = new CpsAdminOverviewResponse();
        result.setOpenIssueCount(issueMapper.countOpenIssues());
        result.setPendingFeedbackCount(issueMapper.countByStatus(CpsIssueStatus.PENDING_FEEDBACK));
        result.setPendingRectifyCount(issueMapper.countByStatus(CpsIssueStatus.PENDING_RECTIFY));
        result.setPendingReviewCount(issueMapper.countByStatus(CpsIssueStatus.PENDING_REVIEW));
        result.setOverdueCount(issueMapper.countOverdueIssues());
        result.setClosedThisMonthCount(issueMapper.countClosedThisMonth());
        result.setRecentIssues(issueMapper.listForAdmin(null, null, null, null, null, null, null, null, null, RECENT_ISSUE_LIMIT, 0));
        return result;
    }

    public List<CpsIssueListItemResponse> exportIssues(String status, String factory, String area, String line, String process, String currentHandler, String createdFrom, String createdTo, String keyword) {
        return issueMapper.listForAdminExport(parseStatus(status), trimToNull(factory), trimToNull(area), trimToNull(line), trimToNull(process), trimToNull(currentHandler), trimToNull(createdFrom), trimToNull(createdTo), trimToNull(keyword), MAX_EXPORT_ROWS);
    }

    private CpsIssueStatus parseStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        try {
            return CpsIssueStatus.valueOf(status.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported issue status: " + status);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
