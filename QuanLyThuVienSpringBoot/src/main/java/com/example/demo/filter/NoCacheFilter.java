package com.example.demo.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class NoCacheFilter implements Filter {

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {

    HttpServletResponse resp = (HttpServletResponse) res;
    resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
    resp.setHeader("Pragma", "no-cache");
    resp.setDateHeader("Expires", 0);

    chain.doFilter(req, res);
  }
}
