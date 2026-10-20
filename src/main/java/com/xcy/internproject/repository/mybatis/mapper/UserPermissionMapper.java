package com.xcy.internproject.repository.mybatis.mapper;

import com.xcy.internproject.repository.mybatis.record.UserPermissionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserPermissionMapper {

    List<UserPermissionRecord> findByUserId(@Param("userId") Long userId);
}
