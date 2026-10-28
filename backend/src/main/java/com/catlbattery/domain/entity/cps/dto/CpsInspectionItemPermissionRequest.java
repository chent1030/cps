package com.catlbattery.domain.entity.cps.dto;

import java.util.List;
import com.catlbattery.domain.entity.cps.CpsInspectionItemPermission;

public class CpsInspectionItemPermissionRequest {
    private List<CpsInspectionItemPermission> permissions;

    public List<CpsInspectionItemPermission> getPermissions() { return permissions; }
    public void setPermissions(List<CpsInspectionItemPermission> permissions) { this.permissions = permissions; }
}
