package io.github.easytrans.demo.feeder;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.easytrans.core.spi.TranslationFeeder;
import io.github.easytrans.demo.entity.po.GoodsPO;
import io.github.easytrans.demo.repository.GoodsMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class GoodsTranslationFeeder implements TranslationFeeder {

    private final GoodsMapper goodsMapper;

    public GoodsTranslationFeeder(GoodsMapper goodsMapper) {
        this.goodsMapper = goodsMapper;
    }

    @Override
    public String getType() {
        return "GOODS_SERVICE";
    }

    @Override
    public Map<Object, String> batchLoad(Set<Object> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Long> longIds = ids.stream()
                .map(id -> ((Number) id).longValue())
                .toList();

        return goodsMapper.selectList(
                Wrappers.<GoodsPO>lambdaQuery().in(GoodsPO::getId, longIds)
        ).stream().collect(Collectors.toMap(
                g -> (Object) g.getId(),
                GoodsPO::getName,
                (a, b) -> a,
                HashMap::new
        ));
    }
}
