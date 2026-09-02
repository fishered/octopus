package com.accuenergy.octopus.mgmt.infrastructure.persistence.catalog;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.UUID;

@TableName("parameter_definition")
public class ParameterDefinitionEntity {
    @TableId private UUID id;
    private UUID tenantId;
    private String code;
    private String displayName;
    private String quantityKind;
    private String valueSemantics;
    private String dataType;
    private UUID canonicalUnitId;
    private Integer decimalScale;
    private String status;

    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID value) { tenantId = value; }
    public String getCode() { return code; } public void setCode(String value) { code = value; }
    public String getDisplayName() { return displayName; } public void setDisplayName(String value) { displayName = value; }
    public String getQuantityKind() { return quantityKind; } public void setQuantityKind(String value) { quantityKind = value; }
    public String getValueSemantics() { return valueSemantics; } public void setValueSemantics(String value) { valueSemantics = value; }
    public String getDataType() { return dataType; } public void setDataType(String value) { dataType = value; }
    public UUID getCanonicalUnitId() { return canonicalUnitId; } public void setCanonicalUnitId(UUID value) { canonicalUnitId = value; }
    public Integer getDecimalScale() { return decimalScale; } public void setDecimalScale(Integer value) { decimalScale = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
}
