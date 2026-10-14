package com.cargoflow.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalFilter extends OncePerRequestFilter {
  private final String secret;
  private final Map<String, Long> nonces = new ConcurrentHashMap<>();

  public InternalFilter(@Value("${cf.secret}") String secret) {
    if (secret.length() < 32) throw new IllegalStateException("CF_SECRET 至少32字符");
    this.secret = secret;
  }

  public static Actor actor(HttpServletRequest req) {
    Actor actor = (Actor) req.getAttribute("actor");
    if (actor == null) throw new Problem(401, "UNAUTHORIZED", "缺少服务身份");
    return actor;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (req.getRequestURI().equals("/health")) {
      chain.doFilter(req, res);
      return;
    }
    byte[] bytes = req.getInputStream().readNBytes(65537);
    if (bytes.length > 65536) {
      res.setStatus(413);
      return;
    }
    try {
      String time = req.getHeader("X-CF-Time"),
          nonce = req.getHeader("X-CF-Nonce"),
          trace = req.getHeader("X-Trace-Id");
      long now = System.currentTimeMillis();
      Actor actor =
          new Actor(
              req.getHeader("X-CF-Tenant"), req.getHeader("X-CF-User"), req.getHeader("X-CF-Role"));
      if (actor.tenant() == null
          || actor.user() == null
          || actor.role() == null
          || nonce == null
          || nonce.length() > 80
          || trace == null
          || trace.length() > 80
          || Math.abs(now - Long.parseLong(time)) > 60000)
        throw new Problem(401, "SIGNATURE_INVALID", "服务签名已失效");
      String path =
          req.getRequestURI() + (req.getQueryString() == null ? "" : "?" + req.getQueryString());
      if (!Crypto.equal(
          req.getHeader("X-CF-Signature"),
          Crypto.sign(
              secret,
              Crypto.canonical(
                  req.getMethod(),
                  path,
                  new String(bytes, StandardCharsets.UTF_8),
                  actor,
                  time,
                  nonce,
                  trace)))) throw new Problem(401, "SIGNATURE_INVALID", "服务签名无效");
      nonces.entrySet().removeIf(e -> now - e.getValue() > 120000);
      if (nonces.putIfAbsent(nonce, now) != null) throw new Problem(401, "REPLAY", "请求已处理");
      req.setAttribute("actor", actor);
      req.setAttribute("trace", trace);
      res.setHeader("X-Trace-Id", trace);
      HttpServletRequestWrapper wrapped =
          new HttpServletRequestWrapper(req) {
            @Override
            public ServletInputStream getInputStream() {
              ByteArrayInputStream in = new ByteArrayInputStream(bytes);
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

                public void setReadListener(ReadListener listener) {}
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
    } catch (NumberFormatException e) {
      res.setStatus(401);
    }
  }
}
