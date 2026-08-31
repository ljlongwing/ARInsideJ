package arinside.ar.is;

import arinside.util.JsonReader;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal client for the BMC Helix Innovation Studio / {@code rx} REST API - the source for the
 * IS documentation layer (rules, processes, web APIs, associations, events, ...), which the AR
 * Java API cannot see. The endpoints and auth were worked out against a live server.
 *
 * <p>Auth: {@code POST /api/jwt/login} (form-encoded) returns a JWT and sets an {@code AR-JWT}
 * cookie; requests then send {@code Authorization: AR-JWT <jwt>} <em>and</em> the cookie. The
 * server 401s intermittently even with a fresh token, so every call retries with a re-login.
 * Listing goes through the DataPage mechanism ({@code /api/rx/application/datapage?dataPageType=...}).
 */
public final class IsClient implements AutoCloseable {

    // Several rx DataPage queries reject a finite page size ("must be set to -1 (infinite)"), and
    // the definition lists are small anyway, so ask for everything in one page.
    private static final int PAGE_SIZE = -1;

    private final String base;
    private final String user;
    private final String pass;
    private final HttpClient http;
    private volatile String token;

    public IsClient(String baseUrl, String user, String pass) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        this.base = b;
        this.user = user == null ? "" : user;
        this.pass = pass == null ? "" : pass;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    /** Authenticate (or re-authenticate). Throws on failure. */
    public void login() {
        String body = "username=" + enc(user) + "&password=" + enc(pass);
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/jwt/login"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> resp = send(req);
        if (resp.statusCode() != 200 || resp.body() == null || resp.body().isBlank()) {
            throw new IsException("IS login failed (HTTP " + resp.statusCode() + ") at " + base + "/api/jwt/login: " + trim(resp.body()));
        }
        this.token = resp.body().trim();
    }

    /** GET a path (relative to the base URL), parsed as JSON. Re-logs in and retries once on 401. */
    public Object get(String path) {
        if (token == null) login();
        for (int attempt = 1; attempt <= 4; attempt++) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .header("Authorization", "AR-JWT " + token)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(90))
                .GET().build();
            HttpResponse<String> resp = send(req);
            int sc = resp.statusCode();
            if (sc == 200) return JsonReader.parse(resp.body());
            if (sc == 401 && attempt < 4) { login(); continue; }
            if (sc >= 500 && attempt < 4) { sleep(1000L * attempt); continue; }
            throw new IsException("IS GET " + path + " -> HTTP " + sc + ": " + trim(resp.body()));
        }
        throw new IsException("IS GET " + path + " failed after retries");
    }

    /** All rows of a DataPage query (single infinite page - see {@link #PAGE_SIZE}). */
    public List<Object> dataPage(String dataPageType, Map<String, String> extraParams) {
        StringBuilder q = new StringBuilder("/api/rx/application/datapage?dataPageType=").append(enc(dataPageType))
            .append("&pageSize=").append(PAGE_SIZE).append("&startIndex=0");
        if (extraParams != null) extraParams.forEach((k, v) -> q.append('&').append(enc(k)).append('=').append(enc(v)));
        return new ArrayList<>(JsonReader.asList(JsonReader.at(get(q.toString()), "data")));
    }

    public List<Object> dataPage(String dataPageType) { return dataPage(dataPageType, null); }

    private HttpResponse<String> send(HttpRequest req) {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IsException("IS request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IsException("IS request interrupted");
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static String trim(String s) { return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) + "…" : s); }

    /** Not a pooled resource; nothing to release, but keeps try-with-resources tidy at call sites. */
    @Override public void close() { /* no-op */ }

    public String baseUrl() { return base; }

    public static final class IsException extends RuntimeException {
        public IsException(String m) { super(m); }
        public IsException(String m, Throwable c) { super(m, c); }
    }

    /** Convenience: the standard extra-param map for a bundle-scoped query. */
    public static Map<String, String> bundleScope(String bundleId) {
        Map<String, String> m = new LinkedHashMap<>();
        if (bundleId != null && !bundleId.isEmpty()) m.put("bundleId", bundleId);
        return m;
    }
}
