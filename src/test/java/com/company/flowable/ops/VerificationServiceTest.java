package com.company.flowable.ops;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ExecutionQuery;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.job.api.JobQuery;
import org.flowable.job.api.TimerJobQuery;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class VerificationServiceTest {

    @Test
    void verifyDeletedWhenAllCountsZero() {
        RuntimeService runtimeService = Mockito.mock(RuntimeService.class);
        TaskService taskService = Mockito.mock(TaskService.class);
        ManagementService managementService = Mockito.mock(ManagementService.class);

        ProcessInstanceQuery piq = Mockito.mock(ProcessInstanceQuery.class);
        TaskQuery tq = Mockito.mock(TaskQuery.class);
        JobQuery jq = Mockito.mock(JobQuery.class);
        TimerJobQuery tjq = Mockito.mock(TimerJobQuery.class);
        ExecutionQuery eq = Mockito.mock(ExecutionQuery.class);

        when(runtimeService.createProcessInstanceQuery()).thenReturn(piq);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(managementService.createJobQuery()).thenReturn(jq);
        when(managementService.createTimerJobQuery()).thenReturn(tjq);
        when(runtimeService.createExecutionQuery()).thenReturn(eq);

        when(piq.processInstanceId("pid")).thenReturn(piq);
        when(tq.processInstanceId("pid")).thenReturn(tq);
        when(jq.processInstanceId("pid")).thenReturn(jq);
        when(tjq.processInstanceId("pid")).thenReturn(tjq);
        when(eq.processInstanceId("pid")).thenReturn(eq);

        when(piq.count()).thenReturn(0L);
        when(tq.count()).thenReturn(0L);
        when(jq.count()).thenReturn(0L);
        when(tjq.count()).thenReturn(0L);
        when(eq.count()).thenReturn(0L);

        VerificationService service = new VerificationService(runtimeService, taskService, managementService);
        VerificationSnapshot snapshot = service.verify("pid");
        assertTrue(snapshot.isDeleted());
    }
}
