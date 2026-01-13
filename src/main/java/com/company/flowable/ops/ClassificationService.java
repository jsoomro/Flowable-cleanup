package com.company.flowable.ops;

import org.springframework.stereotype.Service;

@Service
public class ClassificationService {
    public ClassificationResult classify(Candidate candidate, OpsCleanupProperties props) {
        int openTasks = candidate.getOpenTasksCount();
        int timerCount = candidate.getTimerCount();
        int overdueJobCount = candidate.getOverdueJobCount();
        int overdueTimerCount = candidate.getOverdueTimerCount();
        Long oldestTaskAge = candidate.getOldestTaskAgeHours();

        boolean safe = openTasks == 0 && timerCount == 0 && overdueJobCount == 0 && overdueTimerCount == 0;
        Classification classification = safe ? Classification.SAFE_TO_DELETE : Classification.REVIEW_ONLY;

        RecommendedAction action;
        if (safe) {
            action = RecommendedAction.TERMINATE;
        } else if (timerCount > 0 && overdueTimerCount == 0) {
            action = RecommendedAction.WAIT;
        } else if (openTasks > 0 && oldestTaskAge != null && oldestTaskAge < props.getTaskEscalationHours()) {
            action = RecommendedAction.WAIT;
        } else {
            action = RecommendedAction.ESCALATE;
        }

        return new ClassificationResult(classification, action);
    }
}
