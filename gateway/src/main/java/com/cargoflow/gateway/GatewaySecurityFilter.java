package com.cargoflow.gateway;

import com.cargoflow.common.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class GatewaySecurityFilter extends OncePerRequestFilter {
  final GatewayController gateway;

  public GatewaySecurityFilter(GatewayController gateway) {
    this.gateway = gateway;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String original = req.getRequestURI();
    if (!original.startsWith("/api/")
        || Set.of("/api/demo/login", "/api/system").contains(original)) {
      chain.doFilter(req, res);
      return;
    }
    try {
      Actor a = gateway.actor(req.getHeader("Authorization"));
      if (!Set.of("GET", "POST", "PUT", "PATCH").contains(req.getMethod()))
        throw new Problem(405, "METHOD_INVALID", "不支持的方法");
      byte[] body = req.getInputStream().readNBytes(65537);
      if (body.length > 65536) throw new Problem(413, "BODY_TOO_LARGE", "请求内容过大");
      String
          path =
              original.substring(4)
                  + (req.getQueryString() == null ? "" : "?" + req.getQueryString()),
          time = String.valueOf(System.currentTimeMillis()),
          nonce = UUID.randomUUID().toString(),
          trace = UUID.randomUUID().toString();
      Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      headers.putAll(
          Map.of(
              "X-CF-Tenant",
              a.tenant(),
              "X-CF-User",
              a.user(),
              "X-CF-Role",
              a.role(),
              "X-CF-Time",
              time,
              "X-CF-Nonce",
              nonce,
              "X-Trace-Id",
              trace,
              "X-CF-Signature",
              Crypto.sign(
                  gateway.secret,
                  Crypto.canonical(
                      req.getMethod(),
                      path,
                      new String(body, StandardCharsets.UTF_8),
                      a,
                      time,
                      nonce,
                      trace))));
      res.setHeader("X-Trace-Id", trace);
      res.setHeader("X-Gateway-Engine", "Spring-Cloud-Gateway");
      HttpServletRequestWrapper wrapped =
          new HttpServletRequestWrapper(req) {
            @Override
            public String getHeader(String name) {
              return headers.containsKey(name) ? headers.get(name) : super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
              return headers.containsKey(name)
                  ? Collections.enumeration(List.of(headers.get(name)))
                  : super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
              Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
              names.addAll(headers.keySet());
              return Collections.enumeration(names);
            }

            @Override
            public ServletInputStream getInputStream() {
              ByteArrayInputStream in = new ByteArrayInputStream(body);
              return new ServletInputStream() {
                public int read() {
                  return in.read();
                }

                public boolean isFinished() {
                  return in.available() == 0;
                }

                public boolean isReady() {
                  return true;
                }

                public void setReadListener(ReadListener l) {}
              };
            }

            @Override
            public BufferedReader getReader() {
              return new BufferedReader(
                  new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
          };
      chain.doFilter(wrapped, res);
    } catch (Problem e) {
      res.setStatus(e.status);
      res.setContentType("application/json;charset=UTF-8");
      res.getWriter().write(Json.write(Map.of("code", e.code, "message", e.getMessage())));
    }
  }
}
