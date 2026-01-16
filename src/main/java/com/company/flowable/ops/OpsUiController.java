package com.company.flowable.ops;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class OpsUiController {
    private final OpsCleanupService cleanupService;
    private final OpsCleanupProperties props;

    public OpsUiController(OpsCleanupService cleanupService, OpsCleanupProperties props) {
        this.cleanupService = cleanupService;
        this.props = props;
    }

    @GetMapping("/ops")
    public String dashboard(Model model, CsrfToken csrfToken) {
        CsrfToken safeToken = csrfToken != null ? csrfToken : new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "");
        model.addAttribute("defaultHours", props.getDefaultHours());
        model.addAttribute("dryRun", props.isDryRun());
        model.addAttribute("_csrf", safeToken);
        return "ops-dashboard";
    }

    @GetMapping("/ops/{pid}")
    public String details(@PathVariable("pid") String pid, Model model, CsrfToken csrfToken) {
        CsrfToken safeToken = csrfToken != null ? csrfToken : new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "");
        ProcessDetailDto detail = cleanupService.getDetails(pid, props.getDefaultHours());
        model.addAttribute("detail", detail);
        model.addAttribute("dryRun", props.isDryRun());
        model.addAttribute("_csrf", safeToken);
        return "ops-details";
    }
}
