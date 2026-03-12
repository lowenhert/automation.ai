package com.lowenhert.patchmanagement.core.config;

import com.lowenhert.patchmanagement.core.auth.JwtTokenUtil;
import com.lowenhert.patchmanagement.core.auth.CustomUserDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenUtil jwtTokenUtil;
    private final CustomUserDetailsService userDetailsService;

    public WebSocketConfig(JwtTokenUtil jwtTokenUtil, CustomUserDetailsService userDetailsService) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.userDetailsService = userDetailsService;
        log.info("WebSocketConfig initialized");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        log.info("Configuring message broker with topics and application prefixes");
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
        log.info("Message broker configuration completed");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        log.info("Registering STOMP endpoint /ws-scripts with SockJS");
        registry.addEndpoint("/ws-scripts")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        log.info("STOMP endpoint registration completed");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        log.info("Configuring client inbound channel with JWT interceptor");
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                    message, StompHeaderAccessor.class
                );
                
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    log.info("Processing STOMP CONNECT command");
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        log.debug("Extracted JWT token: {}", token);
                        
                        try {
                            if (jwtTokenUtil.validateToken(token)) {
                                String username = jwtTokenUtil.getUsernameFromToken(token);
                                log.info("Valid JWT token for user: {}", username);
                                
                                var userDetails = userDetailsService.loadUserByUsername(username);
                                var authentication = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities()
                                );
                                accessor.setUser(authentication);
                                log.info("Authentication set for WebSocket session");
                            } else {
                                log.warn("Invalid JWT token received");
                            }
                        } catch (AuthenticationException e) {
                            log.error("WebSocket authentication failed: {}", e.getMessage());
                            throw new RuntimeException("WebSocket authentication failed");
                        }
                    } else {
                        log.warn("No Bearer token found in Authorization header");
                    }
                }
                return message;
            }

            @Override
            public void afterSendCompletion(Message<?> message, MessageChannel channel, 
                                         boolean sent, Exception ex) {
                if (ex != null) {
                    log.error("Error during message sending: {}", ex.getMessage());
                }
            }
        });
        log.info("Client inbound channel configuration completed");
    }
}