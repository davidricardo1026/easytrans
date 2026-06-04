package io.github.easytrans.demo.entity.vo;

import io.github.easytrans.core.annotation.TranslateFrom;
import io.github.easytrans.demo.entity.po.ExtremeNestedPO;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@TranslateFrom(ExtremeNestedPO.class)
public class ExtremeNestedVO {
    private Long id;

    // Nest 1: List inside Map inside Map
    private Map<String, Map<String, List<OrderItemVO>>> poMapMapList;

    // Nest 2: Map of Set inside a List
    private List<Map<String, Set<OrderItemVO>>> poListMapSet;

    // Nest 3: Map with translatable Key and complex nested Map of List as Value
    private Map<OrderItemVO, Map<String, List<OrderItemVO>>> poTransMapComplex;
}
