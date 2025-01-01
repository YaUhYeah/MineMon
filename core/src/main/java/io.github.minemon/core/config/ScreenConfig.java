package io.github.minemon.core.config;

import com.badlogic.gdx.Game;
import io.github.minemon.core.service.*;
import io.github.minemon.core.service.impl.ScreenManagerImpl;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScreenConfig {

    @Bean
    public ScreenManager screenManager(ApplicationContext applicationContext, Game game) {
        return new ScreenManagerImpl(applicationContext, game);
    }



}
