package com.company.flowable.ops;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.RepositoryService;

public class BpmnModelCache {
    private final Map<String, BpmnModel> cache;
    private final RepositoryService repositoryService;

    public BpmnModelCache(RepositoryService repositoryService, int maxSize) {
        this.repositoryService = repositoryService;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<String, BpmnModel>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, BpmnModel> eldest) {
                return size() > maxSize;
            }
        });
    }

    public String resolveActivityName(String processDefinitionId, String activityId) {
        if (processDefinitionId == null || activityId == null) {
            return null;
        }
        BpmnModel model = cache.computeIfAbsent(processDefinitionId, repositoryService::getBpmnModel);
        if (model == null) {
            return null;
        }
        FlowElement element = model.getFlowElement(activityId);
        if (element == null) {
            return null;
        }
        return element.getName();
    }
}
