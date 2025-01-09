package io.github.minemon.context;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.SimpleApplicationEventMulticaster;


public class AndroidEventPublisher implements ApplicationEventPublisher {

    private final SimpleApplicationEventMulticaster multicaster =
        new SimpleApplicationEventMulticaster();

    @Override
    public void publishEvent(ApplicationEvent event) {
        multicaster.multicastEvent(event);
    }

    @Override
    public void publishEvent(Object event) {
        multicaster.multicastEvent(new ApplicationEvent(event) {
            private static final long serialVersionUID = 1L;
        });
    }
}
