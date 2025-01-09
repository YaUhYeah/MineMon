package io.github.minemon.context;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;


public class AndroidSafeApplicationContext extends GenericApplicationContext {

    public AndroidSafeApplicationContext() {
        setDisplayName("AndroidSafeApplicationContext");
        setId("androidSafeContext");
    }

    @Override
    protected ResourcePatternResolver getResourcePatternResolver() {
        return new NoOpResourcePatternResolver();
    }

    
    static class NoOpResourcePatternResolver implements ResourcePatternResolver {
        private final ResourceLoader resourceLoader = new DefaultResourceLoader();

        @Override
        public Resource getResource(String location) {
            return resourceLoader.getResource(location);
        }

        @Override
        public Resource[] getResources(String locationPattern) throws IOException {
            return new Resource[0]; 
        }

        @Override
        public ClassLoader getClassLoader() {
            return resourceLoader.getClassLoader();
        }
    }
}
