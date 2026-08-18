package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EsTopicsServletTest {

    @Test
    void overviewFilterLinksUseSameSelectorsAsSidebarFilters() {
        assertEquals("/hub/es/topics?space=emerging-standards&s=1",
                EsTopicsServlet.buildOverviewFilterUrl("/hub", "emerging-standards", 1L, null, null));
        assertEquals("/hub/es/topics?space=emerging-standards&p=2",
                EsTopicsServlet.buildOverviewFilterUrl("/hub", "emerging-standards", null, 2L, null));
        assertEquals("/hub/es/topics?space=emerging-standards&n=Innovators",
                EsTopicsServlet.buildOverviewFilterUrl("/hub", "emerging-standards", null, null, "Innovators"));
    }
}
