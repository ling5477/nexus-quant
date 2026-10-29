package com.guidinglight.nexusquant.demo.registration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 匿名 JSON 入口先有界读取，分块请求也不能在认证前无限占用内存。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE+10)
public class DemoRegistrationBodyFilter extends OncePerRequestFilter {
    private static final int LIMIT=8192;
    private static final Set<String> PATHS=Set.of("/api/auth/register","/api/auth/verify-email","/api/auth/resend-verification");
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || !(PATHS.contains(path) || path.startsWith("/api/platform/"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        if(request.getContentLengthLong()>LIMIT){response.setStatus(413);return;}
        byte[] content=request.getInputStream().readNBytes(LIMIT+1);
        if(content.length>LIMIT){response.setStatus(413);return;}
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input=new ByteArrayInputStream(content);
                return new ServletInputStream() {
                    @Override public int read(){return input.read();}
                    @Override public int read(byte[] bytes,int offset,int length){return input.read(bytes,offset,length);}
                    @Override public boolean isFinished(){return input.available()==0;}
                    @Override public boolean isReady(){return true;}
                    @Override public void setReadListener(ReadListener listener){throw new IllegalStateException("synchronous registration only");}
                };
            }
            @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
        },response);
    }
}
