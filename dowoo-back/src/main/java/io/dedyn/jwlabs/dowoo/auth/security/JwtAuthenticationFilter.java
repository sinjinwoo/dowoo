package io.dedyn.jwlabs.dowoo.auth.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            try {
                UUID userId = jwtTokenProvider.parseUserId(header.substring(BEARER_PREFIX.length()));
                var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * SseEmitter(TranslateController)는 컨트롤러 스레드가 즉시 반환되고 별도 스레드에서 emitter를
     * 완료/에러 처리하는데, 그때 서블릿 컨테이너가 원래 요청을 ASYNC로 재디스패치한다. OncePerRequestFilter는
     * 기본적으로 ASYNC 디스패치에서 스스로를 건너뛰므로(SecurityContext가 스레드로컬이라 재디스패치를
     * 처리하는 스레드에는 없음), Spring Security의 AuthorizationFilter(ASYNC에서도 항상 실행됨)가 빈
     * SecurityContext를 보고 AuthorizationDeniedException을 던진다(사용자가 번역 중지를 눌러 클라이언트가
     * 먼저 연결을 끊었을 때 로그에 나타나는 게 바로 이 케이스). ASYNC 디스패치에서도 이 필터가 다시 실행되도록
     * 강제해 Authorization 헤더로 SecurityContext를 재구성한다.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
}
