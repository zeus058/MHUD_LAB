package vn.edu.hcmus.securechat.common.protocol;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import vn.edu.hcmus.securechat.common.exception.ProtocolException;

/**
 * Tiện ích serialize/deserialize JSON ↔ byte[] cho payload trong PacketFrame.
 * Dùng Jackson ObjectMapper — singleton thread-safe.
 *
 * Sử dụng:
 *   byte[] payload = JsonSerializer.toBytes(tgtRequest);
 *   TgtRequest req = JsonSerializer.fromBytes(payload, TgtRequest.class);
 */
public final class JsonSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private JsonSerializer() {}

    /**
     * Serialize object thành byte[] (JSON UTF-8).
     */
    public static byte[] toBytes(Object obj) throws ProtocolException {
        try {
            return MAPPER.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            throw new ProtocolException("JSON serialize failed", e);
        }
    }

    /**
     * Serialize object thành JSON String.
     */
    public static String toJsonString(Object obj) throws ProtocolException {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new ProtocolException("JSON serialize failed", e);
        }
    }

    /**
     * Deserialize byte[] (JSON UTF-8) thành object.
     */
    public static <T> T fromBytes(byte[] json, Class<T> clazz) throws ProtocolException {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new ProtocolException("JSON deserialize failed", e);
        }
    }

    /**
     * Deserialize JSON String thành object.
     */
    public static <T> T fromString(String json, Class<T> clazz) throws ProtocolException {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new ProtocolException("JSON deserialize failed", e);
        }
    }

    /**
     * Trả về ObjectMapper nội bộ (read-only dùng cho custom deserialization nếu cần).
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }
}
