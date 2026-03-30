package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.AppLoginEventDao;
import org.airahub.interophub.dao.AppRegistryDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.AppLoginEvent;
import org.airahub.interophub.model.AppRegistry;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.IpGeoLookupService;

public class AdminAppLoginStatsServlet extends HttpServlet {
    private static final DateTimeFormatter DETAIL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthFlowService authFlowService;
    private final AppRegistryDao appRegistryDao;
    private final AppLoginEventDao appLoginEventDao;
    private final UserDao userDao;
    private final IpGeoLookupService ipGeoLookupService;

    public AdminAppLoginStatsServlet() {
        this.authFlowService = new AuthFlowService();
        this.appRegistryDao = new AppRegistryDao();
        this.appLoginEventDao = new AppLoginEventDao();
        this.userDao = new UserDao();
        this.ipGeoLookupService = new IpGeoLookupService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();

        YearMonth m0 = YearMonth.now();
        YearMonth m1 = m0.minusMonths(1);
        YearMonth m2 = m0.minusMonths(2);
        YearMonth[] monthColumns = { m0, m1, m2 };

        List<AppRegistry> apps = appRegistryDao.findAllOrdered();

        // Build count map for summary table: key = "appId-year-month"
        Map<String, Long> countMap = new HashMap<>();
        for (AppRegistry app : apps) {
            for (YearMonth ym : monthColumns) {
                String key = countKey(app.getAppId(), ym);
                countMap.put(key, appLoginEventDao.countByAppAndMonth(
                        app.getAppId(), ym.getYear(), ym.getMonthValue()));
            }
        }

        // Parse optional detail params
        Long selectedAppId = parseId(trimToNull(request.getParameter("appId")));
        Integer selectedYear = parseIntOrNull(trimToNull(request.getParameter("year")));
        Integer selectedMonth = parseIntOrNull(trimToNull(request.getParameter("month")));
        boolean showDetail = selectedAppId != null && selectedYear != null && selectedMonth != null;

        AppRegistry selectedApp = null;
        List<AppLoginEvent> detailEvents = List.of();
        Map<Long, User> userMap = Map.of();
        Map<String, String> geoMap = Map.of();

        if (showDetail) {
            final Long fAppId = selectedAppId;
            selectedApp = apps.stream()
                    .filter(a -> fAppId.equals(a.getAppId()))
                    .findFirst().orElse(null);
            if (selectedApp == null) {
                showDetail = false;
            } else {
                detailEvents = appLoginEventDao.findByAppAndMonth(
                        selectedAppId, selectedYear, selectedMonth);

                Set<Long> userIds = detailEvents.stream()
                        .map(AppLoginEvent::getUserId)
                        .collect(Collectors.toSet());
                List<User> users = userDao.findByIds(new ArrayList<>(userIds));
                userMap = new HashMap<>();
                for (User u : users) {
                    userMap.put(u.getUserId(), u);
                }

                // Geo-lookup unique non-null user IPs
                Set<String> uniqueIps = new LinkedHashSet<>();
                for (AppLoginEvent evt : detailEvents) {
                    if (evt.getUserIp() != null && !evt.getUserIp().isBlank()) {
                        uniqueIps.add(evt.getUserIp());
                    }
                }
                geoMap = new HashMap<>();
                for (String ip : uniqueIps) {
                    geoMap.put(ip, ipGeoLookupService.describe(ip));
                }
            }
        }

        renderPage(response, contextPath, apps, monthColumns, countMap,
                showDetail, selectedApp, selectedYear, selectedMonth,
                detailEvents, userMap, geoMap);
    }

    private void renderPage(HttpServletResponse response, String contextPath,
            List<AppRegistry> apps, YearMonth[] monthColumns, Map<String, Long> countMap,
            boolean showDetail, AppRegistry selectedApp, Integer selectedYear, Integer selectedMonth,
            List<AppLoginEvent> detailEvents, Map<Long, User> userMap,
            Map<String, String> geoMap) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>App Login Statistics - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>App Login Statistics</h1>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">&larr; Back to Welcome</a></p>");

            // --- Summary table ---
            int totalCols = monthColumns.length + 1;
            out.println("    <table class=\"data-table\">");
            out.println("      <thead>");
            out.println("        <tr>");
            out.println("          <th>App</th>");
            for (YearMonth ym : monthColumns) {
                out.println("          <th>" + monthLabel(ym) + "</th>");
            }
            out.println("        </tr>");
            out.println("      </thead>");
            out.println("      <tbody>");
            if (apps.isEmpty()) {
                out.println("        <tr><td colspan=\"" + totalCols
                        + "\">No registered apps found.</td></tr>");
            }
            for (AppRegistry app : apps) {
                out.println("        <tr>");
                out.println("          <td>" + escapeHtml(orEmpty(app.getAppName())) + "</td>");
                for (YearMonth ym : monthColumns) {
                    long count = countMap.getOrDefault(countKey(app.getAppId(), ym), 0L);
                    String link = contextPath + "/admin/app-login-stats?appId=" + app.getAppId()
                            + "&amp;year=" + ym.getYear() + "&amp;month=" + ym.getMonthValue();
                    boolean isSelected = showDetail
                            && app.getAppId().equals(selectedApp.getAppId())
                            && ym.getYear() == selectedYear
                            && ym.getMonthValue() == selectedMonth;
                    String cellStyle = isSelected ? " style=\"font-weight:bold;\"" : "";
                    out.println("          <td" + cellStyle + "><a href=\"" + link + "\">"
                            + count + "</a></td>");
                }
                out.println("        </tr>");
            }
            out.println("      </tbody>");
            out.println("    </table>");

            // --- Detail section ---
            if (showDetail) {
                String monthName = Month.of(selectedMonth)
                        .getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                out.println("    <h2>" + escapeHtml(orEmpty(selectedApp.getAppName()))
                        + " &mdash; " + monthName + " " + selectedYear + "</h2>");

                if (detailEvents.isEmpty()) {
                    out.println("    <p>No login events recorded for this app and month.</p>");
                } else {
                    out.println("    <table class=\"data-table\">");
                    out.println("      <thead>");
                    out.println("        <tr>");
                    out.println("          <th>Display Name</th>");
                    out.println("          <th>Date</th>");
                    out.println("          <th>Location (User IP)</th>");
                    out.println("        </tr>");
                    out.println("      </thead>");
                    out.println("      <tbody>");
                    for (AppLoginEvent evt : detailEvents) {
                        User user = userMap.get(evt.getUserId());
                        String displayName = resolveDisplayName(user);
                        String dateStr = evt.getLoggedInAt() != null
                                ? DETAIL_FORMATTER.format(evt.getLoggedInAt())
                                : "";
                        String location = buildLocationDisplay(evt.getUserIp(), geoMap);

                        out.println("        <tr>");
                        out.println("          <td>" + escapeHtml(displayName) + "</td>");
                        out.println("          <td>" + escapeHtml(dateStr) + "</td>");
                        out.println("          <td>" + escapeHtml(location) + "</td>");
                        out.println("        </tr>");
                    }
                    out.println("      </tbody>");
                    out.println("    </table>");
                }
            }

            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private String resolveDisplayName(User user) {
        if (user == null) {
            return "(unknown user)";
        }
        String name = user.getDisplayName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return orEmpty(user.getEmail());
    }

    private String buildLocationDisplay(String userIp, Map<String, String> geoMap) {
        if (userIp == null || userIp.isBlank()) {
            return "\u2014"; // em dash
        }
        String geo = geoMap.get(userIp);
        // If geo is different from the raw IP, that means we got a city/country back
        if (geo != null && !geo.equals(userIp)) {
            return geo + " (" + userIp + ")";
        }
        return userIp;
    }

    private String monthLabel(YearMonth ym) {
        return ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear();
    }

    private String countKey(Long appId, YearMonth ym) {
        return appId + "-" + ym.getYear() + "-" + ym.getMonthValue();
    }

    private Optional<User> requireAdmin(HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }
        if (!authFlowService.isAdminUser(authenticatedUser.get())) {
            renderForbidden(response, request.getContextPath());
            return Optional.empty();
        }
        return authenticatedUser;
    }

    private void renderForbidden(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Access Denied - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Access Denied</h1>");
            out.println("    <p>You must be an InteropHub admin to view app login statistics.</p>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private Long parseId(String value) {
        if (value == null)
            return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseIntOrNull(String value) {
        if (value == null)
            return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null)
            return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String escapeHtml(String value) {
        if (value == null)
            return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
