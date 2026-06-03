package io.github.easytrans.demo.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.easytrans.demo.entity.po.ArchiveOrderPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArchiveOrderMapper extends BaseMapper<ArchiveOrderPO> {
}
