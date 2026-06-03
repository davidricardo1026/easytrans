package io.github.easytrans.demo.entity.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_archive_order_item")
public class ArchiveOrderItemPO {

    @TableId
    private Long id;

    private Long orderId;

    private Long goodsId;
}
