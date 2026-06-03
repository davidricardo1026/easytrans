package io.github.easytrans.demo.entity.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

@Data
@TableName("t_order")
public class OrderPO {

    @TableId
    private Long id;

    private Long userId;

    @TableField(exist = false)
    private List<OrderItemPO> items;
}
