package io.github.easytrans.demo.entity.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

@Data
@TableName("t_archive_order")
public class ArchiveOrderPO {

    @TableId
    private Long id;

    private Long userId;

    @TableField(exist = false)
    private List<ArchiveOrderItemPO> items;
}
