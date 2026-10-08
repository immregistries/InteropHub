package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.service.CommunicationBundleService.OrientationResources;
import org.junit.jupiter.api.Test;

class CommunicationBundleServletTest {
    @Test
    void directBundleUrlEnforcesSameAccessAsManagement() throws IOException {
        var f = new StarterPacketServletTest.Fixture();
        f.path = "/12";
        f.allowed = false;
        servlet(f).doGet(f.request(), f.response());
        assertEquals(403, f.status);
        assertFalse(f.rendered);
        f.allowed = true;
        f.user = null;
        servlet(f).doGet(f.request(), f.response());
        assertEquals(403, f.status);
        assertFalse(f.rendered);
    }

    @Test
    void authorizedBundleUsesItsOwnTopicAndOriginalTemplateContent() throws IOException {
        var f = new StarterPacketServletTest.Fixture();
        f.path = "/12";
        f.packet.bundle().setEsTopicId(20L);
        servlet(f).doGet(f.request(), f.response());
        assertTrue(f.rendered);
        assertEquals(200, f.status);
    }

    @Test
    void malformedBundleUrlReturnsValidationErrorWithoutRendering() throws IOException {
        var f = new StarterPacketServletTest.Fixture();
        f.path = "/12/other";
        servlet(f).doGet(f.request(), f.response());
        assertEquals(400, f.status);
        assertFalse(f.rendered);
    }

    @Test
    void operationalRenderingFailureReturnsServiceUnavailable() throws IOException {
        var f = new StarterPacketServletTest.Fixture();
        f.path = "/12";
        var servlet = new CommunicationBundleServlet(f.auth(), f.service(), new EsTopicSpaceDao()) {
            @Override void render(HttpServletRequest request, HttpServletResponse response, EsTopic topic,
                    OrientationResources packet) throws IOException {
                throw new IOException("File unavailable");
            }
        };
        servlet.doGet(f.request(), f.response());
        assertEquals(503, f.status);
        assertTrue(f.responseError.contains("temporarily unavailable"));
    }

    private static CommunicationBundleServlet servlet(StarterPacketServletTest.Fixture f) {
        return new CommunicationBundleServlet(f.auth(), f.service(), new EsTopicSpaceDao()) {
            @Override void render(HttpServletRequest request, HttpServletResponse response, EsTopic topic,
                    OrientationResources packet) {
                assertEquals(packet.bundle().getEsTopicId(), topic.getEsTopicId());
                assertEquals(700L, packet.bundle().getTemplateId());
                assertSame(f.packet, packet);
                f.rendered = true;
            }
        };
    }
}
