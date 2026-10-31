package com.xiaoying.materialstudio.template;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TemplateMapper {
    List<TemplateRow> findAll();

    List<TemplateRow> findByCategory(@Param("category") String category);

    TemplateRow findById(@Param("id") String id);
}
