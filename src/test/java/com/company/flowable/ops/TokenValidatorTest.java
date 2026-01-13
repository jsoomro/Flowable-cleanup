package com.company.flowable.ops;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class TokenValidatorTest {

    @Test
    void validateTerminateToken() {
        TokenValidator validator = new TokenValidator();
        assertTrue(validator.validateTerminateToken("DELETE_TERMINATE"));
        assertFalse(validator.validateTerminateToken("DELETE_ALL_TERMINATE"));
    }

    @Test
    void validateTerminateAllToken() {
        TokenValidator validator = new TokenValidator();
        assertTrue(validator.validateTerminateAllToken("DELETE_ALL_TERMINATE"));
        assertFalse(validator.validateTerminateAllToken("DELETE_TERMINATE"));
    }
}
