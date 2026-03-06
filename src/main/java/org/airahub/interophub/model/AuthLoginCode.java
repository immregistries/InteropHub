package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "auth_login_code")
public class AuthLoginCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "login_code_id")
    private Long loginCodeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "code_hash", nullable = false, unique = true, length = 32)
    private byte[] codeHash;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "return_to", length = 500)
    private String returnTo;

    @Column(name = "state_nonce", length = 255)
    private String stateNonce;

    @Column(name = "requested_url", length = 500)
    private String requestedUrl;

    @PrePersist
    protected void onCreate() {
        if (issuedAt == null) {
            issuedAt = LocalDateTime.now();
        }
    }

    public Long getLoginCodeId() {
        return loginCodeId;
    }

    public void setLoginCodeId(Long loginCodeId) {
        this.loginCodeId = loginCodeId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public byte[] getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(byte[] codeHash) {
        this.codeHash = codeHash;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(LocalDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }

    public String getReturnTo() {
        return returnTo;
    }

    public void setReturnTo(String returnTo) {
        this.returnTo = returnTo;
    }

    public String getStateNonce() {
        return stateNonce;
    }

    public void setStateNonce(String stateNonce) {
        this.stateNonce = stateNonce;
    }

    public String getRequestedUrl() {
        return requestedUrl;
    }

    public void setRequestedUrl(String requestedUrl) {
        this.requestedUrl = requestedUrl;
    }
}
