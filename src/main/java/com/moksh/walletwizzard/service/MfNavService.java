package com.moksh.walletwizzard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fetches live mutual fund NAV and scheme search from mfapi.in (free, AMFI-backed).
 */
@Slf4j
@Service
public class MfNavService {

    private static final String BASE_URL = "https://api.mfapi.in";

    private final RestClient restClient = RestClient.create(BASE_URL);

    /** Returns the latest NAV for the given AMFI scheme code. */
    public BigDecimal getLatestNav(String schemeCode) {
        MfApiResponse response = restClient.get()
                .uri("/mf/{code}", schemeCode)
                .retrieve()
                .body(MfApiResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("No NAV data returned for scheme " + schemeCode);
        }
        String navStr = response.data().get(0).nav();
        log.debug("Fetched NAV for scheme {}: {}", schemeCode, navStr);
        return new BigDecimal(navStr);
    }

    /** Searches for schemes matching the query. Returns up to 10 results. */
    public List<SchemeResult> searchSchemes(String query) {
        List<SchemeResult> results = restClient.get()
                .uri("/mf/search?q={q}", query)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return results != null ? results : List.of();
    }

    // ── Response types ───────────────────────────────────────────────────────

    public record MfApiResponse(List<NavEntry> data) {}

    public record NavEntry(String date, String nav) {}

    public record SchemeResult(int schemeCode, String schemeName) {}
}
