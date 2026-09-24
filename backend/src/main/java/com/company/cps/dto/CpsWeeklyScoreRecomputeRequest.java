package com.company.cps.dto;

import java.time.LocalDate;

/** B4 手动触发重算请求（可选指定 weekStartDate；null=上周）。 */
public class CpsWeeklyScoreRecomputeRequest {

    private LocalDate weekStartDate;
    private String operatorEmpNo;

    public LocalDate getWeekStartDate() { return weekStartDate; }
    public void setWeekStartDate(LocalDate weekStartDate) { this.weekStartDate = weekStartDate; }
    public String getOperatorEmpNo() { return operatorEmpNo; }
    public void setOperatorEmpNo(String operatorEmpNo) { this.operatorEmpNo = operatorEmpNo; }
}
