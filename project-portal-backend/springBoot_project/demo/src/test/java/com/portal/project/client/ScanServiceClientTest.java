package com.portal.project.client;

import com.portal.project.dto.DashboardSummaryDTO;
import com.portal.project.dto.ScanHistoryDTO;
import com.portal.project.dto.ScanResponseDTO;
import com.portal.project.dto.ScanStatusDTO;
import com.portal.project.dto.VulnerabilityDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the project -> scan service HTTP client. The RestTemplate is mocked, so no
 * network is involved; the tests check URLs, forwarded JWT, request body and error handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScanServiceClientTest {

    private static final String BASE = "http://scan:8083";

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ScanServiceClient client;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(client, "scanServiceUrl", BASE);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void incomingRequestWithAuthHeader(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (value != null) {
            request.addHeader("Authorization", value);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private VulnerabilityDTO vulnerability(String severity) {
        VulnerabilityDTO dto = new VulnerabilityDTO();
        dto.setSeverity(severity);
        return dto;
    }

    // ---------------------------------------------------------------- vulnerabilities by project / scan

    @Test
    void getVulnerabilitiesByProjectId_returnsBody_andCallsExpectedUrl() {
        VulnerabilityDTO[] body = {vulnerability("CRITICAL"), vulnerability("MAJOR")};
        when(restTemplate.exchange(eq(BASE + "/api/scans/projects/10/vulnerabilities"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(VulnerabilityDTO[].class))).thenReturn(ResponseEntity.ok(body));

        List<VulnerabilityDTO> result = client.getVulnerabilitiesByProjectId(10L);

        assertEquals(2, result.size());
        assertEquals("CRITICAL", result.get(0).getSeverity());
    }

    @Test
    void getVulnerabilitiesByProjectId_returnsEmpty_whenBodyIsNull() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(VulnerabilityDTO[].class))).thenReturn(ResponseEntity.ok(null));

        assertTrue(client.getVulnerabilitiesByProjectId(10L).isEmpty());
    }

    @Test
    void getVulnerabilitiesByProjectId_returnsEmpty_whenScanServiceFails() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(VulnerabilityDTO[].class))).thenThrow(new RestClientException("connection refused"));

        assertTrue(client.getVulnerabilitiesByProjectId(10L).isEmpty());
    }

    @Test
    void getVulnerabilitiesByScanId_returnsBody() {
        VulnerabilityDTO[] body = {vulnerability("BLOCKER")};
        when(restTemplate.exchange(eq(BASE + "/api/scans/7/vulnerabilities"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(VulnerabilityDTO[].class))).thenReturn(ResponseEntity.ok(body));

        assertEquals(1, client.getVulnerabilitiesByScanId(7L).size());
    }

    @Test
    void getVulnerabilitiesByScanId_returnsEmpty_onError() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(VulnerabilityDTO[].class))).thenThrow(new RestClientException("boom"));

        assertTrue(client.getVulnerabilitiesByScanId(7L).isEmpty());
    }

    @Test
    void getVulnerabilitiesByScanId_returnsEmpty_whenBodyIsNull() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(VulnerabilityDTO[].class))).thenReturn(ResponseEntity.ok(null));

        assertTrue(client.getVulnerabilitiesByScanId(7L).isEmpty());
    }

    // ---------------------------------------------------------------- single vulnerability

    @Test
    void getVulnerabilityById_returnsBody() {
        VulnerabilityDTO dto = vulnerability("CRITICAL");
        when(restTemplate.exchange(eq(BASE + "/api/scans/vulnerabilities/3"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(VulnerabilityDTO.class))).thenReturn(ResponseEntity.ok(dto));

        assertSame(dto, client.getVulnerabilityById(3L));
    }

    @Test
    void getVulnerabilityById_returnsNull_onNon2xx() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(VulnerabilityDTO.class))).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build());

        assertNull(client.getVulnerabilityById(3L));
    }

    @Test
    void getVulnerabilityById_returnsNull_onError() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(VulnerabilityDTO.class))).thenThrow(new RestClientException("boom"));

        assertNull(client.getVulnerabilityById(3L));
    }

    // ---------------------------------------------------------------- dashboard, history, status

    @Test
    void getDashboardSummary_returnsBody() {
        DashboardSummaryDTO summary = new DashboardSummaryDTO();
        when(restTemplate.exchange(eq(BASE + "/api/scans/dashboard/projects/10/summary"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(DashboardSummaryDTO.class))).thenReturn(ResponseEntity.ok(summary));

        assertSame(summary, client.getDashboardSummary(10L));
    }

    @Test
    void getDashboardSummary_returnsNull_onNon2xxOrError() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(DashboardSummaryDTO.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
                .thenThrow(new RestClientException("boom"));

        assertNull(client.getDashboardSummary(10L));
        assertNull(client.getDashboardSummary(10L));
    }

    @Test
    void getScanHistory_returnsBody_orEmpty() {
        ScanHistoryDTO[] body = {new ScanHistoryDTO()};
        when(restTemplate.exchange(eq(BASE + "/api/scans/repositories/4/history"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(ScanHistoryDTO[].class)))
                .thenReturn(ResponseEntity.ok(body))
                .thenThrow(new RestClientException("boom"));

        assertEquals(1, client.getScanHistory(4L).size());
        assertTrue(client.getScanHistory(4L).isEmpty());
    }

    @Test
    void getScanHistory_returnsEmpty_whenBodyIsNull() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(ScanHistoryDTO[].class))).thenReturn(ResponseEntity.ok(null));

        assertTrue(client.getScanHistory(4L).isEmpty());
    }

    @Test
    void getScanStatus_returnsBody_orNull() {
        ScanStatusDTO status = new ScanStatusDTO();
        when(restTemplate.exchange(eq(BASE + "/api/scans/9/status"), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(ScanStatusDTO.class)))
                .thenReturn(ResponseEntity.ok(status))
                .thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build())
                .thenThrow(new RestClientException("boom"));

        assertSame(status, client.getScanStatus(9L));
        assertNull(client.getScanStatus(9L));
        assertNull(client.getScanStatus(9L));
    }

    // ---------------------------------------------------------------- JWT forwarding on reads

    @SuppressWarnings({"unchecked", "rawtypes"})
    private HttpEntity<?> captureEntityOfProjectVulnerabilitiesCall() {
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), captor.capture(), eq(VulnerabilityDTO[].class));
        return captor.getValue();
    }

    @Test
    void reads_forwardTheCallersBearerToken() {
        incomingRequestWithAuthHeader("Bearer my.jwt.token");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(VulnerabilityDTO[].class))).thenReturn(ResponseEntity.ok(new VulnerabilityDTO[0]));

        client.getVulnerabilitiesByProjectId(10L);

        assertEquals("Bearer my.jwt.token", captureEntityOfProjectVulnerabilitiesCall().getHeaders().getFirst("Authorization"));
    }

    @Test
    void reads_sendNoAuthorizationHeader_whenThereIsNoIncomingRequest() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(VulnerabilityDTO[].class))).thenReturn(ResponseEntity.ok(new VulnerabilityDTO[0]));

        client.getVulnerabilitiesByProjectId(10L);

        assertNull(captureEntityOfProjectVulnerabilitiesCall().getHeaders().getFirst("Authorization"));
    }

    @Test
    void reads_ignoreNonBearerAuthorizationHeaders() {
        incomingRequestWithAuthHeader("Basic abc");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(VulnerabilityDTO[].class))).thenReturn(ResponseEntity.ok(new VulnerabilityDTO[0]));

        client.getVulnerabilitiesByProjectId(10L);

        assertNull(captureEntityOfProjectVulnerabilitiesCall().getHeaders().getFirst("Authorization"));
    }

    // ---------------------------------------------------------------- trigger scan

    @SuppressWarnings({"unchecked", "rawtypes"})
    private HttpEntity<Map<String, Object>> captureTriggerEntity() {
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(BASE + "/api/scans/analyze"), eq(HttpMethod.POST), captor.capture(),
                eq(ScanResponseDTO.class));
        return captor.getValue();
    }

    @Test
    void triggerScan_postsBodyWithBearerToken_andReturnsResponse() {
        ScanResponseDTO response = new ScanResponseDTO();
        response.setScanId(77L);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(ScanResponseDTO.class))).thenReturn(ResponseEntity.accepted().body(response));

        ScanResponseDTO result = client.triggerScan(10L, 100L, "https://github.com/acme/app.git", "develop", "tok");

        assertEquals(77L, result.getScanId());
        HttpEntity<Map<String, Object>> sent = captureTriggerEntity();
        assertEquals("Bearer tok", sent.getHeaders().getFirst("Authorization"));
        assertEquals(10L, sent.getBody().get("projectId"));
        assertEquals(100L, sent.getBody().get("repositoryId"));
        assertEquals("https://github.com/acme/app.git", sent.getBody().get("repositoryUrl"));
        assertEquals("develop", sent.getBody().get("branch"));
    }

    @Test
    void triggerScan_defaultsBranchToMain_whenNull() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(ScanResponseDTO.class))).thenReturn(ResponseEntity.accepted().body(new ScanResponseDTO()));

        client.triggerScan(10L, 100L, "https://github.com/acme/app.git", null, "tok");

        assertEquals("main", captureTriggerEntity().getBody().get("branch"));
    }

    @Test
    void triggerScan_sendsNoAuthorization_whenTokenIsNull() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(ScanResponseDTO.class))).thenReturn(ResponseEntity.accepted().body(new ScanResponseDTO()));

        client.triggerScan(10L, 100L, "https://github.com/acme/app.git", "main", null);

        assertNull(captureTriggerEntity().getHeaders().getFirst("Authorization"));
    }

    @Test
    void triggerScan_returnsNull_onNon2xx() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(ScanResponseDTO.class))).thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());

        assertNull(client.triggerScan(10L, 100L, "https://github.com/acme/app.git", "main", "tok"));
    }

    @Test
    void triggerScan_returnsNull_onForbidden() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(ScanResponseDTO.class))).thenThrow(new RestClientException("403 Forbidden"));

        assertNull(client.triggerScan(10L, 100L, "https://github.com/acme/app.git", "main", "tok"));
    }

    @Test
    void triggerScan_returnsNull_whenScanServiceIsUnreachable() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(ScanResponseDTO.class))).thenThrow(new RestClientException("Connection refused"));

        assertNull(client.triggerScan(10L, 100L, "https://github.com/acme/app.git", "main", "tok"));
    }
}
