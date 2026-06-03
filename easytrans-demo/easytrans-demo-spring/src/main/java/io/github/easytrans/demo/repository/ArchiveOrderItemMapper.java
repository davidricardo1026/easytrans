package io.github.easytrans.demo.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.easytrans.demo.entity.po.ArchiveOrderItemPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArchiveOrderItemMapper extends BaseMapper<ArchiveOrderItemPO> {
}
