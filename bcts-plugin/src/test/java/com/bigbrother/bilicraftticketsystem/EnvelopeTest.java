package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.web.Envelope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnvelopeTest {
    @Test
    void decodePingEnvelopeWithoutData() throws Exception {
        Envelope env = Envelope.decode("{\"type\":\"ping\",\"ts\":1782478725924}");

        assertEquals("ping", env.type);
        assertEquals(1782478725924L, env.ts);
        assertNull(env.id);
        assertNull(env.data);
    }
}
