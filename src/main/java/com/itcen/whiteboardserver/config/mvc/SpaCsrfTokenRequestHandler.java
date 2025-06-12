package com.itcen.whiteboardserver.config.mvc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       Supplier<CsrfToken> csrfToken) {
        // 1) 항상 xor 핸들러를 써서 request attribute에 인코딩된 토큰을 넣는다
        this.xor.handle(request, response, csrfToken);

        // 2) csrfToken.get() 을 호출해 쿠키에도 토큰을 내려주게 트리거
        csrfToken.get();
    }


    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        // 요청 헤더에 토큰이 있는지 확인
        String headerValue = request.getHeader(csrfToken.getHeaderName());

        return (StringUtils.hasText(headerValue)
                // (A) 헤더가 있으면 plain 방식으로 그대로 꺼내고
                ? this.plain
                // (B) 헤더가 없으면 xor 방식으로 파라미터값을 디코딩해서 꺼낸다
                : this.xor
        ).resolveCsrfTokenValue(request, csrfToken);    }
}