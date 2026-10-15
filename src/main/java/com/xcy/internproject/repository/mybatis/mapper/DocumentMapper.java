package com.xcy.internproject.repository.mybatis.mapper;

import com.xcy.internproject.repository.DocumentPageQuery;
import com.xcy.internproject.repository.mybatis.record.DocumentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentMapper {

    int insert(DocumentRecord record);

    DocumentRecord findById(@Param("id") Long id);

    List<DocumentRecord> findPage(DocumentPageQuery query);

    long count(DocumentPageQuery query);
}
