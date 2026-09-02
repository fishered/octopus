package com.accuenergy.octopus.mgmt.infrastructure.persistence.catalog;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.UUID;

@TableName("unit_catalog")
public class UnitEntity {
    @TableId private UUID id;
    private String code;
    private String symbol;
    private String dimension;
    private BigDecimal scale;
    private BigDecimal offsetValue;
    private String description;

    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public String getCode() { return code; } public void setCode(String value) { code = value; }
    public String getSymbol() { return symbol; } public void setSymbol(String value) { symbol = value; }
    public String getDimension() { return dimension; } public void setDimension(String value) { dimension = value; }
    public BigDecimal getScale() { return scale; } public void setScale(BigDecimal value) { scale = value; }
    public BigDecimal getOffsetValue() { return offsetValue; } public void setOffsetValue(BigDecimal value) { offsetValue = value; }
    public String getDescription() { return description; } public void setDescription(String value) { description = value; }
}
