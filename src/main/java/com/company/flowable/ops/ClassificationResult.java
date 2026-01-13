package com.company.flowable.ops;

public class ClassificationResult {
    private final Classification classification;
    private final RecommendedAction recommendedAction;

    public ClassificationResult(Classification classification, RecommendedAction recommendedAction) {
        this.classification = classification;
        this.recommendedAction = recommendedAction;
    }

    public Classification getClassification() {
        return classification;
    }

    public RecommendedAction getRecommendedAction() {
        return recommendedAction;
    }
}
