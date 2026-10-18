package com.guidinglight.nexusquant.api.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Set;

/** 请求体和方法参数校验均脱敏，普通业务字段的诊断值仍保留。 */
class SensitiveValidationErrorTest {
    private void fixture(Object value) {}

    @Test
    void bodyValidationRedactsSensitiveFieldsAndNestedPaths() throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "request");
        for (String field :
                new String[] {
                    "apiKey",
                    "secretKey",
                    "passphrase",
                    "privateKeyPem",
                    "newPassword",
                    "nested.refreshToken"
                })
            binding.addError(
                    new FieldError(
                            "request",
                            field,
                            "synthetic-sensitive-marker",
                            false,
                            null,
                            null,
                            "invalid field"));
        binding.addError(
                new FieldError("request", "accountId", -1L, false, null, null, "invalid account"));
        var parameter =
                new MethodParameter(getClass().getDeclaredMethod("fixture", Object.class), 0);
        var response =
                new ApiExceptionHandler()
                        .handleMethodArgumentNotValid(
                                new MethodArgumentNotValidException(parameter, binding),
                                new MockHttpServletRequest());
        assertEquals(7, response.fieldErrors().size());
        for (int i = 0; i < 6; i++) assertNull(response.fieldErrors().get(i).rejectedValue());
        assertEquals(-1L, response.fieldErrors().get(6).rejectedValue());
    }

    @Test
    void methodValidationAlsoRedactsSensitiveValues() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("execute.request.secretKey");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getInvalidValue()).thenReturn("synthetic-sensitive-marker");
        when(violation.getMessage()).thenReturn("invalid secret length");
        var response =
                new ApiExceptionHandler()
                        .handleConstraintViolation(
                                new ConstraintViolationException(Set.of(violation)),
                                new MockHttpServletRequest());
        assertEquals(1, response.fieldErrors().size());
        assertNull(response.fieldErrors().getFirst().rejectedValue());
        assertEquals("secretKey", response.fieldErrors().getFirst().field());
    }
}
