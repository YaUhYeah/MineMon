package io.github.minemon.android.config;

import io.github.minemon.context.GameApplicationContext;
import io.github.minemon.input.AndroidTouchInput;
import io.github.minemon.input.InputService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AndroidConfig {

    @Bean
    public AndroidTouchInput androidTouchInput() {
        return new AndroidTouchInput(GameApplicationContext.getContext().getBean(InputService.class));
    }
}
