package com.xcy.internproject.repository.mybatis.mapper;

import com.xcy.internproject.repository.mybatis.record.UserRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    int insert(UserRecord record);

    UserRecord findById(@Param("id") Long id);
}
