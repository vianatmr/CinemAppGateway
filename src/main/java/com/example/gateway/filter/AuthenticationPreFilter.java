package com.example.gateway.filter;

import com.example.gateway.dto.ValidateTokenResponse;
import com.example.gateway.utility.Constant;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;

@Component
public class AuthenticationPreFilter extends AbstractGatewayFilterFactory<AuthenticationPreFilter.Config> {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationPreFilter.class);

    @Value("${myapp.jwtSecret}")
    private byte[] jwtSecret;

    @Value("${service.client.userService.url}")
    private String userClient;

    private final WebClient.Builder webClientBuilder;

    public AuthenticationPreFilter(WebClient.Builder webClientBuilder){
        super(Config.class);
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest serverHttpRequest = exchange.getRequest();

            String bearerToken = serverHttpRequest.getHeaders().getFirst(Constant.HEADER);

            try {
                    Jwts.parserBuilder().setSigningKey(jwtSecret).build()
                        .parseClaimsJws(bearerToken.split(" ")[1]);
            } catch (MalformedJwtException e) {
                logger.error("Invalid JWT token: {}", e.getMessage());
                return onError(exchange, e.getMessage());
            } catch (ExpiredJwtException e) {
                logger.error("JWT token is expired {}", e.getMessage());
                return onError(exchange, e.getMessage());
            } catch (UnsupportedJwtException e) {
                logger.error("JWT token is unsupported: {}", e.getMessage());
                return onError(exchange, e.getMessage());
            } catch (IllegalArgumentException e) {
                logger.error("JWT claims string is empty {}", e.getMessage());
                return onError(exchange, e.getMessage());
            }

            return webClientBuilder.build().get()
                    .uri(userClient + "/api/auth/validateToken")
                    .header(Constant.HEADER, bearerToken)
                    .retrieve().bodyToMono(ValidateTokenResponse.class)
                    .map(response -> {
                        logger.info("Authority - {}", response.getAuthority());
                        logger.info("Authorization - {}", response.getToken());

                        ServerHttpRequest request = exchange.getRequest().mutate()
                                .header("Authorization", bearerToken)
                                .header("authority", response.getAuthority())
                                .build();

                        return exchange.mutate().request(request).build();
                    }).flatMap(chain::filter)
                    .onErrorResume(error -> onError(exchange, error.getMessage()));
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message) {
        DataBufferFactory dataBufferFactory = exchange.getResponse().bufferFactory();
        logger.error("Error - {}",message);
        ServerHttpResponse response = exchange.getResponse();

        response.getHeaders().add("Content-Type","application/json");
        response.setStatusCode(HttpStatus.BAD_GATEWAY);

        HashMap<String, Object> body = new HashMap<>();
        body.put("statusCode", HttpStatus.BAD_GATEWAY);
        body.put("timeStamp", LocalDateTime.now().toString());
        body.put("message", message);
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            byte[] b = objectMapper.writeValueAsBytes(body);
            return response.writeWith(Mono.just(b).map(t -> dataBufferFactory.wrap(b)));
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return response.setComplete();
    }

    public static class Config{};
}