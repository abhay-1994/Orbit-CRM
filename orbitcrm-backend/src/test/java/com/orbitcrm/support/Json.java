package com.orbitcrm.support;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;

import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.json.JsonMapper;

/**
 * Small JSON helpers for tests.
 *
 * <p>Purpose: keep test code short: serialize objects, build JSON objects or maps from alternating key/value
 * arguments (keeping {@code null} values, which matters for partial updates), and read values from a response with a
 * JsonPath expression.
 *
 * @author Abhay
 */
public final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    /** Builds a JSON object from alternating key/value arguments; nulls are kept. */
    public static String obj(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return write(map);
    }

    public static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    public static <T> T read(MvcResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
