package com.xcy.internproject.repository.mybatis.mapper;

import com.xcy.internproject.model.TicketStatus;
import com.xcy.internproject.repository.TicketPageQuery;
import com.xcy.internproject.repository.mybatis.record.TicketRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TicketMapper {

    int insert(TicketRecord record);

    TicketRecord findById(@Param("id") Long id);

    TicketRecord findByIdForUpdate(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") TicketStatus status);

    List<TicketRecord> findPage(TicketPageQuery query);

    long count(TicketPageQuery query);
}
