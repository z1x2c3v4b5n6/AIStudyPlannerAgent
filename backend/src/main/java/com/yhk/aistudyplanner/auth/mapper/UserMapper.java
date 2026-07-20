package com.yhk.aistudyplanner.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yhk.aistudyplanner.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}

