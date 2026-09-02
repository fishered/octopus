package com.accuenergy.octopus.mgmt.infrastructure.persistence.asset;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceActualMapper extends BaseMapper<DeviceActualEntity> {
    DeviceContextRow findDeviceContext(@Param("deviceId") UUID deviceId);
    long countCertificateUse(@Param("certificateId") UUID certificateId,
                             @Param("excludingActualId") UUID excludingActualId);
    DeviceActualDetailsRow findDetailsByDeviceId(@Param("deviceId") UUID deviceId);

    record DeviceContextRow(UUID tenantId, String organizationPath) { }
    record DeviceActualDetailsRow(UUID id, UUID tenantId, UUID deviceId, String hardwareSerial,
                                  String manufacturer, String firmwareVersion, UUID certificateId,
                                  String connectivityStatus, Instant lastSeenAt, Instant commissionedAt,
                                  long version, Instant createdAt, Instant updatedAt,
                                  String organizationPath) { }
}
