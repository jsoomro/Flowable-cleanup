package com.company.flowable.ops;

import org.springframework.stereotype.Component;

@Component
public class TokenValidator {
    public boolean validateTerminateToken(String token) {
        return token != null && token.equalsIgnoreCase("DELETE_TERMINATE");
    }

    public boolean validateTerminateAllToken(String token) {
        return token != null && token.equalsIgnoreCase("DELETE_ALL_TERMINATE");
    }
}
