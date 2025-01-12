package io.github.minemon.android.config;

import io.github.minemon.input.AndroidTouchInput;
import io.github.minemon.ui.AndroidUIFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AndroidConfig {
    
    @Bean
    public AndroidTouchInput androidTouchInput() {
        return new AndroidTouchInput();
    }
}