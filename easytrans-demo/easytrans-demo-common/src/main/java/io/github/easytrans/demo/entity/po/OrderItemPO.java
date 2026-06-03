package io.github.easytrans.demo.entity.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_order_item")
public class OrderItemPO {

    @TableId
    private Long id;

    private Long orderId;

    private Long goodsId;
}
