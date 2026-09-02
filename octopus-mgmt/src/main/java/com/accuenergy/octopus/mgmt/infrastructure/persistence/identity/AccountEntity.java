package com.accuenergy.octopus.mgmt.infrastructure.persistence.identity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

@TableName("account")
public class AccountEntity {
    @TableId private UUID id;
    private String loginName;
    private String passwordHash;
    private String status;
    private long sessionGeneration;
    private long authorizationGeneration;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getLoginName() { return loginName; }
    public void setLoginName(String loginName) { this.loginName = loginName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getSessionGeneration() { return sessionGeneration; }
    public void setSessionGeneration(long value) { this.sessionGeneration = value; }
    public long getAuthorizationGeneration() { return authorizationGeneration; }
    public void setAuthorizationGeneration(long value) { this.authorizationGeneration = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { this.createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { this.updatedAt = value; }
}

