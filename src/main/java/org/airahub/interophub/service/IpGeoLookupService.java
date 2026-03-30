package org.airahub.interophub.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Looks up approximate geographic location (city, country) for an IP address
 * using the free ip-api.com service. Falls back gracefully to returning the raw
 * IP if the lookup fails or times out.
 */
public class IpGeoLookupService {
    private static final Logger LOGGER = Logger.getLogger(IpGeoLookupService.class.getName());
    private static final int TIMEOUT_MS = 3000;

    /**
     * Returns a human-readable location for the given IP, e.g. "Chicago, United
     * States".
     * Returns the raw IP string if geo lookup fails, or null if ip is null/blank.
     */
    public String describe(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        // Loopback — no need to look up
        if ("127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return "localhost";
        }
        // Basic sanity-check: only allow characters valid in IP addresses before
        // embedding in URL
        if (!ip.matches("[0-9a-fA-F.:]+")) {
            return ip;
        }
        try {
            URL url = new URL("http://ip-api.com/json/" + ip + "?fields=status,country,city");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) {
                return ip;
            }

            String body = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining());

            // ip-api returns {"status":"fail"} for private ranges, reserved addresses, etc.
            if (body.contains("\"status\":\"fail\"")) {
                return ip;
            }

            String city = extractJsonString(body, "city");
            String country = extractJsonString(body, "country");

            if (city != null && !city.isBlank() && country != null && !country.isBlank()) {
                return city + ", " + country;
            }
            if (country != null && !country.isBlank()) {
                return country;
            }
            return ip;
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "IP geo lookup failed for: " + ip, ex);
            return ip;
        }
    }

    private String extractJsonString(String body, String key) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"");
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }
}
