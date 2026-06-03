package io.github.easytrans.demo.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.easytrans.demo.entity.po.OrderPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<OrderPO> {
}
