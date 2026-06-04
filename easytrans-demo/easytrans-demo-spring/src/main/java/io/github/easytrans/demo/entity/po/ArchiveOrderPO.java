package io.github.easytrans.demo.entity.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@TableName("t_archive_order")
public class ArchiveOrderPO {

    @TableId
    private Long id;

    private Long userId;

    @TableField(exist = false)
    private List<ArchiveOrderItemPO> items;

    // 1. Single Value (Nested Object)
    @TableField(exist = false)
    private ArchiveOrderItemPO singleItem;

    // 2. Collection (Set)
    @TableField(exist = false)
    private Set<ArchiveOrderItemPO> itemSet;

    // 3. Map
    @TableField(exist = false)
    private Map<String, ArchiveOrderItemPO> itemMap;

    // 4. Map of Collection
    @TableField(exist = false)
    private Map<String, List<ArchiveOrderItemPO>> nestedItemMap;

    // 5. Deep Nested Map (Map of Map of List)
    @TableField(exist = false)
    private Map<String, Map<String, List<ArchiveOrderItemPO>>> deepNestedItemMap;
}
