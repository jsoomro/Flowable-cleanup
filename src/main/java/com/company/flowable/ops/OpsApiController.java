package com.company.flowable.ops;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops")
public class OpsApiController {
    private final OpsCleanupService cleanupService;
    private final DeleteOrchestrator deleteOrchestrator;
    private final TokenValidator tokenValidator;
    private final OpsCleanupProperties props;

    public OpsApiController(OpsCleanupService cleanupService,
                            DeleteOrchestrator deleteOrchestrator,
                            TokenValidator tokenValidator,
                            OpsCleanupProperties props) {
        this.cleanupService = cleanupService;
        this.deleteOrchestrator = deleteOrchestrator;
        this.tokenValidator = tokenValidator;
        this.props = props;
    }

    @GetMapping("/processes")
    public PageResult<ProcessSummaryDto> listProcesses(
        @RequestParam(value = "hours", required = false) Integer hours,
        @RequestParam(value = "action", required = false) String action,
        @RequestParam(value = "procDefKey", required = false) String procDefKey,
        @RequestParam(value = "starterUserId", required = false) String starterUserId,
        @RequestParam(value = "hasTasks", required = false) Boolean hasTasks,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        FilterCriteria criteria = new FilterCriteria();
        criteria.setHours(hours == null ? props.getDefaultHours() : hours);
        criteria.setAction(action);
        criteria.setProcDefKey(procDefKey);
        criteria.setStarterUserId(starterUserId);
        criteria.setHasTasks(hasTasks);
        criteria.setPage(page == null ? 0 : page);
        criteria.setSize(size == null ? 50 : size);
        return cleanupService.findCandidates(criteria);
    }

    @GetMapping("/processes/summary")
    public SummaryCounts summaryCounts(
        @RequestParam(value = "hours", required = false) Integer hours,
        @RequestParam(value = "action", required = false) String action,
        @RequestParam(value = "procDefKey", required = false) String procDefKey,
        @RequestParam(value = "starterUserId", required = false) String starterUserId,
        @RequestParam(value = "hasTasks", required = false) Boolean hasTasks) {
        FilterCriteria criteria = new FilterCriteria();
        criteria.setHours(hours == null ? props.getDefaultHours() : hours);
        criteria.setAction(action);
        criteria.setProcDefKey(procDefKey);
        criteria.setStarterUserId(starterUserId);
        criteria.setHasTasks(hasTasks);
        criteria.setPage(0);
        criteria.setSize(1);
        return cleanupService.getSummaryCounts(criteria);
    }

    @GetMapping("/processes/{pid}")
    public ProcessDetailDto getProcess(@PathVariable("pid") String pid,
                                       @RequestParam(value = "hours", required = false) Integer hours) {
        return cleanupService.getDetails(pid, hours == null ? props.getDefaultHours() : hours);
    }

    @PreAuthorize("hasRole('FLOWABLE_OPS_ADMIN')")
    @PostMapping("/processes/terminate")
    public TerminateResponse terminate(@RequestBody TerminateRequest request, Principal principal) {
        if (!tokenValidator.validateTerminateToken(request.getToken())) {
            throw new OpsException(400, "Invalid confirmation token");
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new OpsException(400, "Reason is required");
        }
        List<DeleteResultDto> results = deleteOrchestrator.terminateSelected(request.getPids(),
            request.getReason(), request.isVerify(), principal.getName());
        return new TerminateResponse(results);
    }

    @PreAuthorize("hasRole('FLOWABLE_OPS_ADMIN')")
    @PostMapping("/processes/terminateAll")
    public TerminateResponse terminateAll(@RequestBody TerminateAllRequest request, Principal principal) {
        if (!tokenValidator.validateTerminateAllToken(request.getToken())) {
            throw new OpsException(400, "Invalid confirmation token");
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new OpsException(400, "Reason is required");
        }
        FilterCriteria criteria = new FilterCriteria();
        criteria.setHours(request.getHours() == null ? props.getDefaultHours() : request.getHours());
        criteria.setAction(request.getAction() == null ? "TERMINATE" : request.getAction());
        criteria.setProcDefKey(request.getProcDefKey());
        criteria.setPage(0);
        criteria.setSize(props.getMaxBulkDelete());

        List<DeleteResultDto> results = deleteOrchestrator.terminateAll(criteria, request.getReason(), principal.getName());
        return new TerminateResponse(results);
    }

    @GetMapping("/processes/export")
    public void export(@RequestParam(value = "hours", required = false) Integer hours,
                       @RequestParam(value = "action", required = false) String action,
                       @RequestParam(value = "procDefKey", required = false) String procDefKey,
                       @RequestParam(value = "starterUserId", required = false) String starterUserId,
                       @RequestParam(value = "hasTasks", required = false) Boolean hasTasks,
                       HttpServletResponse response) throws IOException {
        FilterCriteria criteria = new FilterCriteria();
        criteria.setHours(hours == null ? props.getDefaultHours() : hours);
        criteria.setAction(action);
        criteria.setProcDefKey(procDefKey);
        criteria.setStarterUserId(starterUserId);
        criteria.setHasTasks(hasTasks);
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=flowable-ops-export.csv");
        cleanupService.exportCsv(criteria, response.getOutputStream());
    }
}
