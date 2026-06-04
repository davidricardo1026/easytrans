package io.github.easytrans.demo.entity.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.easytrans.core.annotation.Translatable;
import io.github.easytrans.core.annotation.TranslateField;
import lombok.Data;

@Data
@TableName("t_archive_order_item")
@Translatable
public class ArchiveOrderItemPO {

    @TableId
    private Long id;

    private Long orderId;

    private Long goodsId;

    @TableField(exist = false)
    @TranslateField(source = "goodsId", type = "GOODS_SERVICE")
    private String goodsName;
}
