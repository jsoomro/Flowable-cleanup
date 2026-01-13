package com.company.flowable.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ClassificationServiceTest {

    @Test
    void classifyTerminateWhenNoTasksOrTimers() {
        ClassificationService service = new ClassificationService();
        OpsCleanupProperties props = new OpsCleanupProperties();
        Candidate candidate = new Candidate();
        candidate.setTimerCount(0);
        candidate.setOverdueTimerCount(0);
        candidate.setOverdueJobCount(0);

        ClassificationResult result = service.classify(candidate, props);
        assertEquals(RecommendedAction.TERMINATE, result.getRecommendedAction());
        assertEquals(Classification.SAFE_TO_DELETE, result.getClassification());
    }

    @Test
    void classifyWaitWhenTaskYoungerThanEscalation() {
        ClassificationService service = new ClassificationService();
        OpsCleanupProperties props = new OpsCleanupProperties();
        props.setTaskEscalationHours(6);
        Candidate candidate = new Candidate();
        candidate.getTasks().add(new TaskSummary("t1", "Task", null, null, 2));

        ClassificationResult result = service.classify(candidate, props);
        assertEquals(RecommendedAction.WAIT, result.getRecommendedAction());
        assertEquals(Classification.REVIEW_ONLY, result.getClassification());
    }

    @Test
    void classifyEscalateWhenOverdueJob() {
        ClassificationService service = new ClassificationService();
        OpsCleanupProperties props = new OpsCleanupProperties();
        Candidate candidate = new Candidate();
        candidate.setOverdueJobCount(1);

        ClassificationResult result = service.classify(candidate, props);
        assertEquals(RecommendedAction.ESCALATE, result.getRecommendedAction());
        assertEquals(Classification.REVIEW_ONLY, result.getClassification());
    }
}
