package lk.ijse.dep9.api.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.*;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class CorsFilter extends HttpFilter {

    @Override
    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Headers", "X-Total-Count");
        response.addHeader("Access-Control-Expose-Headers", "X-Total-Count");

        response.setHeader("Access-Control-Allow-Methods", "POST, GET, PATCH, DELETE, HEAD, OPTIONS, PUT");

        String headers = request.getHeader("Access-Control-Request-Headers");
        if (headers != null){
            response.setHeader("Access-Control-Allow-Headers", headers);
            response.setHeader("Access-Control-Expose-Headers", headers);
        }

        chain.doFilter(request, response);
    }
}
