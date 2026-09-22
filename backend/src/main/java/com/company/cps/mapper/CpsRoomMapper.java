package com.company.cps.mapper;

import com.company.cps.domain.CpsRoom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CpsRoomMapper {

    List<CpsRoom> findAll(
            @Param("factory") String factory,
            @Param("roomType") String roomType,
            @Param("enabled") Boolean enabled
    );

    Optional<CpsRoom> findById(@Param("id") Long id);

    Optional<CpsRoom> findByCode(@Param("roomCode") String roomCode);

    int insert(CpsRoom room);

    int update(CpsRoom room);

    int setEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled, @Param("updatedBy") String updatedBy);
}
