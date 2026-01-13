package com.company.flowable.ops;

import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Service;

@Service
public class VerificationService {
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final ManagementService managementService;

    public VerificationService(RuntimeService runtimeService, TaskService taskService, ManagementService managementService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.managementService = managementService;
    }

    public VerificationSnapshot verify(String processInstanceId) {
        long procCount = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).count();
        long taskCount = taskService.createTaskQuery().processInstanceId(processInstanceId).count();
        long jobCount = managementService.createJobQuery().processInstanceId(processInstanceId).count();
        long timerCount = managementService.createTimerJobQuery().processInstanceId(processInstanceId).count();
        long execCount = runtimeService.createExecutionQuery().processInstanceId(processInstanceId).count();
        return new VerificationSnapshot(procCount, taskCount, jobCount, timerCount, execCount);
    }
}
