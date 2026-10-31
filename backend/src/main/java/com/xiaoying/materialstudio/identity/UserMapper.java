package com.xiaoying.materialstudio.identity;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    List<UserRow> findAll();

    UserRow findByDemoKey(@Param("demoKey") String demoKey);

    UserRow findById(@Param("id") String id);
}
