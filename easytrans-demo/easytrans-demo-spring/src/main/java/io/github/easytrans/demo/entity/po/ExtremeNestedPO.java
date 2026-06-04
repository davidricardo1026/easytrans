package io.github.easytrans.demo.entity.po;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class ExtremeNestedPO {
    private Long id;

    // Nest 1: List inside Map inside Map
    private Map<String, Map<String, List<OrderItemPO>>> poMapMapList;

    // Nest 2: Map of Set inside a List
    private List<Map<String, Set<OrderItemPO>>> poListMapSet;

    // Nest 3: Map with translatable Key and complex nested Map of List as Value
    private Map<OrderItemPO, Map<String, List<OrderItemPO>>> poTransMapComplex;
}
