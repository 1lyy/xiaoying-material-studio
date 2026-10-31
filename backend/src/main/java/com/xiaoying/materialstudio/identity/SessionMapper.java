package com.xiaoying.materialstudio.identity;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SessionMapper {
    @Insert("""
            INSERT INTO sessions (token_hash, user_id, created_at, expires_at)
            VALUES (#{tokenHash}, #{userId}, #{createdAtUtc}, #{expiresAtUtc})
            """)
    int insert(@Param("tokenHash") String tokenHash, @Param("userId") String userId,
               @Param("createdAtUtc") LocalDateTime createdAtUtc,
               @Param("expiresAtUtc") LocalDateTime expiresAtUtc);

    @Select("""
            SELECT user_id FROM sessions
            WHERE token_hash = #{tokenHash} AND expires_at > #{nowUtc}
            """)
    String findValidUserId(@Param("tokenHash") String tokenHash, @Param("nowUtc") LocalDateTime nowUtc);

    @Delete("DELETE FROM sessions WHERE token_hash = #{tokenHash}")
    int deleteByHash(@Param("tokenHash") String tokenHash);
}
