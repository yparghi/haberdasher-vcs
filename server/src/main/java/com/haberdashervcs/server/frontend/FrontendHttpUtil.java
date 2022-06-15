package com.haberdashervcs.server.frontend;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.html.HtmlEscapers;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.eclipse.jetty.http.HttpStatus;


public final class FrontendHttpUtil {

    // TODO: Return an Optional
    public static String getOneUrlParam(String key, Map<String, String[]> map) {
        if (!map.containsKey(key)) {
            return null;
        }
        String[] value = map.get(key);
        if (value.length != 1) {
            return null;
        }
        return value[0];
    }


    /**
     * Puts a boolean value for key into pageData that's true if the key is in params with the value "true", false
     * otherwise.
     *
     * This lets you copy url params like "?success=true" into the pageData template.
     */
    public static void putBooleanUrlParam(String key, Map<String, String[]> params, Map<String, Object> pageData) {
        String paramValue = getOneUrlParam(key, params);
        boolean boolValue = (paramValue != null && paramValue.equals("true"));
        pageData.put(key, boolValue);
    }


    public static void notFound(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.NOT_FOUND_404);
        response.getWriter().print("<html><body><p>404 Not Found</p></body></html>");
    }


    public static void notAuthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED_401);
        response.getWriter().print(
                String.format("<html><body><p>401 Not Authorized</p><p>%s</p></body></html>", htmlEnc(message)));
    }


    public static void methodNotAllowed(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
        response.getWriter().print("<html><body><p>405 Method not allowed</p></body></html>");
    }


    public static void badRequest(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST_400);
        response.getWriter().print("<html><body><p>400 Bad request</p></body></html>");
    }


    static String htmlEnc(String s) {
        return HtmlEscapers.htmlEscaper().escape(s);
    }


    public static String urlEnc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }


    public static void redirectToPath(
            HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        Preconditions.checkArgument(path.startsWith("/"));
        if (request.getServerName().startsWith("localhost")) {
            response.sendRedirect(path);
        } else {
            response.sendRedirect(String.format("https://%s%s", request.getServerName(), path));
        }
    }


    public static String getHostUrl(HttpServletRequest request) {
        if (request.getServerName().startsWith("localhost")) {
            return String.format("http://%s:%d", request.getServerName(), request.getServerPort());
        } else {
            return String.format("https://%s", request.getServerName());
        }
    }


    /**
     * Set a value of null to clear the cookie.
     */
    public static void setCookie(
            HttpServletRequest request, HttpServletResponse response, String name, String value)
            throws IOException {
        Preconditions.checkNotNull(name);
        Cookie cookie = new Cookie(name, value);
        if (value == null) {
            cookie.setMaxAge(0);
        }
        if (request.getServerName().equals("localhost")) {
            response.addCookie(cookie);
        } else {
            cookie.setDomain("haberdashervcs.com");
            response.addCookie(cookie);
        }
    }


    public static Optional<String> getFormParam(
            String name, HttpServletRequest request, Map<String, String[]> urlParams)
            throws Exception {
        if (request.getHeader("Content-Type").equals("application/x-www-form-urlencoded")) {
            return Optional.ofNullable(getOneUrlParam(name, urlParams));
        } else if (request.getHeader("Content-Type").equals("multipart/form-data")) {
            Collection<Part> formParts = request.getParts();
            for (Part part : formParts) {
                if (part.getName().equals(name)) {
                    return Optional.of(new String(part.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            return Optional.empty();

        } else {
            throw new IllegalStateException("Unknown content type: " + request.getHeader("Content-Type"));
        }
    }

}