package com.accuenergy.octopus.ca.interfaces;

import com.accuenergy.octopus.ca.application.EmqxAuthorizationService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** EMQX HTTP auth/ACL webhook. Every failure is represented as an explicit deny. */
@RestController
@RequestMapping("/internal/emqx")
public final class EmqxAuthorizationController {
    private final EmqxAuthorizationService authorization;
    private final String apiKey;

    public EmqxAuthorizationController(EmqxAuthorizationService authorization,
            @Value("${octopus.ca.emqx.webhook-api-key:}") String apiKey) {
        this.authorization = authorization;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @PostMapping("/auth")
    public ResponseEntity<Map<String, Object>> authenticate(
            @RequestHeader(value = "X-EMQX-API-Key", required = false) String suppliedKey,
            @RequestBody EmqxRequest request) {
        if (!authorized(suppliedKey)) return denied();
        boolean allowed = authorization.authenticate(certificateSerial(request));
        return decision(allowed);
    }

    @PostMapping("/acl")
    public ResponseEntity<Map<String, Object>> authorize(
            @RequestHeader(value = "X-EMQX-API-Key", required = false) String suppliedKey,
            @RequestBody EmqxRequest request) {
        if (!authorized(suppliedKey)) return denied();
        boolean allowed = authorization.authorize(certificateSerial(request), request.action(), request.topic());
        return decision(allowed);
    }

    private boolean authorized(String suppliedKey) {
        return !apiKey.isBlank() && suppliedKey != null
                && java.security.MessageDigest.isEqual(apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        suppliedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String certificateSerial(EmqxRequest request) {
        if (request == null) return null;
        if (request.peerCertificate() != null) {
            if (request.peerCertificate().length() > 32_768) return null;
            try {
                var factory = CertificateFactory.getInstance("X.509");
                var certificate = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(
                        request.peerCertificate().getBytes(StandardCharsets.US_ASCII)));
                return certificate.getSerialNumber().toString(16);
            } catch (Exception invalidCertificate) {
                return null;
            }
        }
        return request.certificateSerial() != null && request.certificateSerial().length() <= 200
                ? request.certificateSerial() : null;
    }

    private static ResponseEntity<Map<String, Object>> decision(boolean allowed) {
        return ResponseEntity.ok(Map.of("result", allowed ? "allow" : "deny"));
    }

    private static ResponseEntity<Map<String, Object>> denied() { return decision(false); }

    public record EmqxRequest(String certificateSerial, String peerCertificate, String action, String topic) {
        public EmqxRequest {
            certificateSerial = normalize(certificateSerial);
            peerCertificate = normalize(peerCertificate);
            action = action == null ? null : action.strip();
            topic = topic == null ? null : topic.strip();
        }

        private static String normalize(String value) {
            return value == null || value.isBlank() ? null : value.strip();
        }
    }
}
