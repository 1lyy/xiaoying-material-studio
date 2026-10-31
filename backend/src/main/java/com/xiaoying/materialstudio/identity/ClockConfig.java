package com.xiaoying.materialstudio.identity;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
    @Bean
    public Clock appClock() {
        return Clock.systemUTC();
    }
}
