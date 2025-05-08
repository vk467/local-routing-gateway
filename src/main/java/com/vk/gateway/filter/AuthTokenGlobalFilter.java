package com.vk.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

@Component
public class AuthTokenGlobalFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;
    private final String basicAuthHeader;
    private final String authUrl;
    private final String remoteUser;
    private final String remoteUserHeader;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private static String cachedAccessToken = null;
    private static String cachedTokenType = null;
    private static Instant tokenExpiryTime = Instant.MIN;

    public AuthTokenGlobalFilter(
            @Value("${auth.api.url}") String authUrl,
            @Value("${auth.username}") String username,
            @Value("${auth.password}") String password,
            @Value("${remote.user}") String remoteUser,
            @Value("${remote.user.header}") String remoteUserHeader
    ) {
        this.authUrl = authUrl;
        this.remoteUser = remoteUser;
        this.remoteUserHeader = remoteUserHeader;
        this.basicAuthHeader = "Basic " +
                java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
        this.webClient = WebClient.builder().build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        return getValidToken()
                .flatMap(tokenResponse -> {
                    String bearerToken = tokenResponse.getTokenType() + " " + tokenResponse.getAccessToken();

                    System.out.println("["+sdf.format(new Date())+"] Injected Authorization header");
                    System.out.println("["+sdf.format(new Date())+"] Injected X-Remote-User header: " + remoteUser);
                    System.out.println("["+sdf.format(new Date())+"] Forwarding request from: " + exchange.getRequest().getURI());

                    ServerHttpResponse originalResponse = exchange.getResponse();
                    DataBufferFactory bufferFactory = originalResponse.bufferFactory();

                    ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                        @Override
                        public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                            URI downstreamUrl = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);

                            if (body instanceof Flux) {
                                Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;

                                return super.writeWith(
                                        fluxBody.map(dataBuffer -> {
                                            byte[] content = new byte[dataBuffer.readableByteCount()];
                                            dataBuffer.read(content);
                                            DataBufferUtils.release(dataBuffer);

                                            String responseBody = new String(content, StandardCharsets.UTF_8);
                                            int statusCode = getStatusCode() != null ? getStatusCode().value() : 0;

                                            System.out.println("["+sdf.format(new Date())+"] Response Status Code: " + statusCode + "; Downstream URL:" + downstreamUrl);
//                                            System.out.println("["+sdf.format(new Date())+"] Response Body: " + responseBody);

                                            return bufferFactory.wrap(content);
                                        })
                                );
                            }
                            return super.writeWith(body);
                        }
                    };


                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(builder -> builder
                                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                                    .header(remoteUserHeader, remoteUser)
                            )
                            .response(decoratedResponse)
                            .build();

                    return chain.filter(mutatedExchange);
                });
    }

    private Mono<TokenResponse> getValidToken() {
        if (cachedAccessToken != null && Instant.now().isBefore(tokenExpiryTime.minusSeconds(60))) {
           System.out.println("Using cached AuthToken..");
            return Mono.just(new TokenResponse(cachedAccessToken, cachedTokenType, 0));
        }

        System.out.println("Fetching AuthToken.......");
        // Fetch new token
        return fetchNewToken().doOnNext(tokenResponse -> {
            cachedAccessToken = tokenResponse.getAccessToken();
            cachedTokenType = tokenResponse.getTokenType();
            tokenExpiryTime = Instant.now().plusSeconds(tokenResponse.getExpiresIn()-60);
        });
    }

    private Mono<TokenResponse> fetchNewToken() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("scope", "urn:opc:idm:__myscopes__");

        return webClient.post()
                .uri(authUrl)
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(TokenResponse.class);
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private static class TokenResponse {
        private String access_token;
        private String token_type;
        private long expires_in;

        public TokenResponse() {}

        public TokenResponse(String access_token, String token_type, long expires_in) {
            this.access_token = access_token;
            this.token_type = token_type;
            this.expires_in = expires_in;
        }

        public String getAccessToken() {
            return access_token;
        }

        public void setAccess_token(String access_token) {
            this.access_token = access_token;
        }

        public String getTokenType() {
            return token_type;
        }

        public void setToken_type(String token_type) {
            this.token_type = token_type;
        }

        public long getExpiresIn() {
            return expires_in;
        }

        public void setExpires_in(long expires_in) {
            this.expires_in = expires_in;
        }
    }
}
