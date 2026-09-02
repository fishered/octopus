package com.accuenergy.octopus.mgmt.infrastructure.persistence.catalog;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceTypeMapper {
    long countCode(@Param("code") String code);
    int insertType(@Param("deviceType") DeviceTypeEntity deviceType);
    DeviceTypeEntity findType(@Param("deviceTypeId") UUID deviceTypeId);
}
