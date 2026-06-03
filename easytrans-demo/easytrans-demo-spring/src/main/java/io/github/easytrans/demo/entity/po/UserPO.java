package io.github.easytrans.demo.entity.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_user")
public class UserPO {

    @TableId
    private Long id;

    private String name;
}
