package io.github.easytrans.demo.feeder;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.easytrans.core.spi.TranslationFeeder;
import io.github.easytrans.demo.entity.po.UserPO;
import io.github.easytrans.demo.repository.UserMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserTranslationFeeder implements TranslationFeeder {

    private final UserMapper userMapper;

    public UserTranslationFeeder(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public String getType() {
        return "USER_SERVICE";
    }

    @Override
    public Map<Object, String> batchLoad(Set<Object> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Long> longIds = ids.stream()
                .map(id -> ((Number) id).longValue())
                .toList();

        return userMapper.selectList(
                Wrappers.<UserPO>lambdaQuery().in(UserPO::getId, longIds)
        ).stream().collect(Collectors.toMap(
                u -> (Object) u.getId(),
                UserPO::getName,
                (a, b) -> a,
                HashMap::new
        ));
    }
}
