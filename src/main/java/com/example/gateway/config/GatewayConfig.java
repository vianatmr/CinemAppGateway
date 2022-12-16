package com.example.gateway.config;

import com.example.gateway.filter.AuthenticationPreFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Value("${service.client.userService.url}")
    private String userClient;
    @Value("${service.client.orderService.url}")
    private String orderClient;
    @Value("${service.client.filmService.url}")
    private String filmClient;

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder, AuthenticationPreFilter authFilter){
        return builder.routes()
                .route("user-service", routes -> routes.path("/user/**")
                        .filters(f ->
                                f.rewritePath("/user/(?<segment>/?.*)", "/user/$\\{segment}"))
                        .uri(userClient))
                .route("order-service", routes -> routes.path("/order/**", "/invoice/**")
                        .filters(f ->
                                f.rewritePath("/order/(?<segment>/?.*)", "/order/$\\{segment}")
                                        .rewritePath("/invoice/(?<segment>/?.*)", "/invoice/$\\{segment}"))
                        .uri(orderClient))

                .route("film-service", route -> route.path("/film/**", "/schedule/**")
                        .filters(f ->
                                f.rewritePath("/film/(?<segment>/?.*)", "/film/$\\{segment}")
                                        .rewritePath("/schedule/(?<segment>/?.*","/schedule/$\\{segment}" ))
                        .uri(filmClient))
                .build();
    }
}
