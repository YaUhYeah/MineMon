package io.github.minemon.lwjgl3;

import lombok.Getter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("io.github.minemon")  // Adjust package to match your code
public class PokemeetupApplication {

    @Getter
    private static ApplicationContext springContext;

    /**
     * Initialize the Spring context if not already done.
     */
    public static void initSpring() {
        if (springContext == null) {
            springContext = SpringApplication.run(PokemeetupApplication.class);
        }
    }

}
