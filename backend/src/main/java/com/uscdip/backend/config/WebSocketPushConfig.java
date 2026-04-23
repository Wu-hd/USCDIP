package com.uscdip.backend.config;

import com.uscdip.backend.model.WebSocketUserPrincipal;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketPushProperties.class)
public class WebSocketPushConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketPushProperties properties;
    private final WebSocketAuthHandshakeInterceptor handshakeInterceptor;
    private final WebSocketAuthChannelInterceptor channelInterceptor;

    public WebSocketPushConfig(
            WebSocketPushProperties properties,
            WebSocketAuthHandshakeInterceptor handshakeInterceptor,
            WebSocketAuthChannelInterceptor channelInterceptor
    ) {
        this.properties = properties;
        this.handshakeInterceptor = handshakeInterceptor;
        this.channelInterceptor = channelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/push")
                .setAllowedOriginPatterns("*")
                .addInterceptors(handshakeInterceptor)
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Principal determineUser(
                            org.springframework.http.server.ServerHttpRequest request,
                            org.springframework.web.socket.WebSocketHandler wsHandler,
                            Map<String, Object> attributes
                    ) {
                        Object principal = attributes.get(WebSocketAuthHandshakeInterceptor.ATTR_PRINCIPAL);
                        return principal instanceof WebSocketUserPrincipal wsPrincipal ? wsPrincipal : null;
                    }
                });
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue", "/user")
                .setTaskScheduler(webSocketMessageBrokerTaskScheduler())
                .setHeartbeatValue(new long[]{properties.getHeartbeatIntervalMs(), properties.getHeartbeatIntervalMs()});
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Bean
    public TaskScheduler webSocketMessageBrokerTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-broker-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(channelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(Math.max(1024, properties.getFrameSizeLimitBytes()));
        registry.setSendBufferSizeLimit(Math.max(1024, properties.getSendBufferSizeLimitBytes()));
        registry.setSendTimeLimit(Math.max(1000, properties.getSendTimeLimitMs()));
    }
}
