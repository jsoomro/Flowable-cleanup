package com.company.flowable.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ops.security")
public class OpsSecurityProperties {
    private String adminUsername = "opsadmin";
    private String adminPassword = "";
    private String viewerUsername = "opsviewer";
    private String viewerPassword = "";

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getViewerUsername() {
        return viewerUsername;
    }

    public void setViewerUsername(String viewerUsername) {
        this.viewerUsername = viewerUsername;
    }

    public String getViewerPassword() {
        return viewerPassword;
    }

    public void setViewerPassword(String viewerPassword) {
        this.viewerPassword = viewerPassword;
    }
}
