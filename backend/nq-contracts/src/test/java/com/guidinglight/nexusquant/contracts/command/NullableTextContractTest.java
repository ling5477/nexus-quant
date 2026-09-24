package com.guidinglight.nexusquant.contracts.command;

import com.guidinglight.nexusquant.common.text.NullableText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NullableTextContractTest {

    @Test
    void preservesCommandFallbackVectors() {
        assertEquals("request", NullableText.firstNonBlank(" request ", "trace"));
        assertEquals("trace", NullableText.firstNonBlank("  ", " trace "));
        assertEquals("é", NullableText.firstNonBlank(null, " é "));
        assertNull(NullableText.firstNonBlank("\t", null));
    }

    @Test
    void preservesReadModelPriorityVectors() {
        assertEquals("third", NullableText.firstNonBlank(null, " ", " third ", "fourth"));
        assertEquals("first", NullableText.firstNonBlank(" first ", "second", "third"));
        assertNull(NullableText.firstNonBlank(null, " ", null));
    }
}
