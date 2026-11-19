package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EsMeetingTopicNoteStreamServletShutdownTest {

    @Test
    void destroyStopsTheKeepaliveThread() {
        // Tomcat reported interophub-note-sse-keepalive still running after undeploy.
        new EsMeetingTopicNoteStreamServlet().destroy();
        assertTrue(EsMeetingTopicNoteStreamServlet.keepaliveStopped());
    }
}
