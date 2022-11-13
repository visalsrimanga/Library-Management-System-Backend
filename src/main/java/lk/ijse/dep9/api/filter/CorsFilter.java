package lk.ijse.dep9.api.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.*;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CorsFilter extends HttpFilter {

    private List<String> origins;

    @Override
    public void init() throws ServletException {
        String origin = getFilterConfig().getInitParameter("origins");
        origins = Arrays.asList(origin.split(", "));
    }

    @Override
    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {

        String requestedOrigin = request.getHeader("Origin");
        for (String origin : origins) {
            if (requestedOrigin.startsWith(origin.trim())){
                response.setHeader("Access-Control-Allow-Origin", requestedOrigin);
                break;
            }
        }

        if(request.getMethod().equalsIgnoreCase("OPTIONS")){
            /* To handle the pre-flighted requests */
            response.setHeader("Access-Control-Allow-Methods", "POST, GET, PATCH, DELETE, HEAD, OPTIONS, PUT");

            String requestMethod = request.getHeader("Access-Control-Request-Method");
            String requestHeader = request.getHeader("Access-Control-Request-Headers");

            if ((requestMethod.equalsIgnoreCase("POST") ||
                    requestMethod.equalsIgnoreCase("PATCH")) &&
                    requestHeader.toLowerCase().contains("content-type")){
                response.setHeader("Access-Control-Allow-Headers", "content-type");
            }
        } else {
            if (request.getMethod().equalsIgnoreCase("GET") ||
                    request.getMethod().equalsIgnoreCase("HEAD")){
                response.setHeader("Access-Control-Expose-Headers", "X-Total-Count");
            }
        }

        chain.doFilter(request, response);
    }
}
