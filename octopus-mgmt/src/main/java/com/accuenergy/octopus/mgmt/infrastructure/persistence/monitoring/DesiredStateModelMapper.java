package com.accuenergy.octopus.mgmt.infrastructure.persistence.monitoring;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DesiredStateModelMapper {
    Long findPublishedModelVersion(@Param("deviceId") UUID deviceId);
    List<PropertyRow> findWritableProperties(@Param("deviceId") UUID deviceId);

    record PropertyRow(String code, String dataType) { }
}
