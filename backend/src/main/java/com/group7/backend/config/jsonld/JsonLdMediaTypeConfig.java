package com.group7.backend.config.jsonld;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class JsonLdMediaTypeConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (!(converter instanceof MappingJackson2HttpMessageConverter jackson)) {
                continue;
            }
            List<MediaType> supported = jackson.getSupportedMediaTypes();
            boolean handlesPlainJson = supported.stream().anyMatch(MediaType.APPLICATION_JSON::equals);
            if (!handlesPlainJson || supported.contains(JsonLdMediaType.APPLICATION_LD_JSON)) {
                continue;
            }
            List<MediaType> updated = new ArrayList<>(supported);
            updated.add(JsonLdMediaType.APPLICATION_LD_JSON);
            jackson.setSupportedMediaTypes(updated);
        }
    }
}
