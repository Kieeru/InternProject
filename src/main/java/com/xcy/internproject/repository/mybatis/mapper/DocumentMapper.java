package com.xcy.internproject.repository.mybatis.mapper;

import com.xcy.internproject.repository.mybatis.record.DocumentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentMapper {

    int insert(DocumentRecord record);

    DocumentRecord findById(@Param("id") Long id);
}
