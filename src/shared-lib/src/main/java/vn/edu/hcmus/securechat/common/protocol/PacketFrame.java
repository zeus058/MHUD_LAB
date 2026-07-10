package vn.edu.hcmus.securechat.common.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import vn.edu.hcmus.securechat.common.exception.FramingException;

public class PacketFrame {
    public static final int HEADER_SIZE = 8; // 1 + 1 + 2 + 4
    
    public static final byte TYPE_CSR_REQUEST = 0x01;
    public static final byte TYPE_CERT_RESPONSE = 0x02;
    public static final byte TYPE_TGT_REQUEST = 0x03;
    public static final byte TYPE_TGT_RESPONSE = 0x04;
    public static final byte TYPE_ST_REQUEST = 0x05;
    public static final byte TYPE_ST_RESPONSE = 0x06;
    public static final byte TYPE_CHAT_HANDSHAKE = 0x07;
    public static final byte TYPE_CHAT_MESSAGE = 0x08;
    public static final byte TYPE_OCSP_REQUEST = 0x09;
    public static final byte TYPE_OCSP_RESPONSE = 0x0A;
    public static final byte TYPE_USER_LIST = 0x0B;
    public static final byte TYPE_PREKEY_UPLOAD = 0x0C;
    public static final byte TYPE_PREKEY_REQUEST = 0x0D;
    public static final byte TYPE_PREKEY_RESPONSE = 0x0E;
    public static final byte TYPE_E2EE_INIT = 0x0F;

    // === Tính năng mới: Group Chat, File Transfer, Call ===
    /** Client gửi tin nhắn nhóm (fan-out payload với nhiều mục tiêu). */
    public static final byte TYPE_GROUP_MESSAGE = 0x10;
    /** Client thông báo bắt đầu gửi file (metadata + FileKey mã hóa). */
    public static final byte TYPE_FILE_INIT = 0x11;
    /** Client gửi chunk nhị phân mã hóa của file. */
    public static final byte TYPE_FILE_CHUNK = 0x12;

    /** Client yêu cầu thu hồi chứng chỉ. */
    public static final byte TYPE_REVOKE_REQUEST = 0x16;
    /** Admin yêu cầu xem audit log (chỉ ADMIN). */
    public static final byte TYPE_AUDIT_LOG_REQUEST  = 0x20;
    /** Server trả audit log cho Admin. */
    public static final byte TYPE_AUDIT_LOG_RESPONSE = 0x21;
    /** Client yêu cầu gia hạn TGT (renewal). */
    public static final byte TYPE_RENEW_TGT_REQUEST  = 0x22;

    public static final byte TYPE_ERROR = (byte) 0xFF;

    private byte type;
    private byte version;
    private short flags;
    private byte[] payload;

    public PacketFrame(byte type, byte version, short flags, byte[] payload) {
        this.type = type;
        this.version = version;
        this.flags = flags;
        this.payload = payload;
    }

    public static void write(OutputStream out, byte type, byte[] payload) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeByte(type);
        dos.writeByte(0x01);          // VERSION
        dos.writeShort(0x0000);       // FLAGS (reserved)
        dos.writeInt(payload.length); // PAYLOAD LENGTH (big-endian)
        dos.write(payload);
        dos.flush();
    }

    public static PacketFrame read(InputStream in) throws IOException, FramingException {
        DataInputStream dis = new DataInputStream(in);
        byte type    = dis.readByte();
        byte version = dis.readByte();
        short flags  = dis.readShort();
        int length   = dis.readInt();
        if (length < 0 || length > 50 * 1024 * 1024) { // max 50 MB (hỗ trợ file chunk)
            throw new FramingException("Invalid payload length: " + length);
        }
        byte[] payload = dis.readNBytes(length);
        if (payload.length != length) {
            throw new FramingException("Failed to read expected number of bytes");
        }
        return new PacketFrame(type, version, flags, payload);
    }

    public byte getType() {
        return type;
    }

    public byte getVersion() {
        return version;
    }

    public short getFlags() {
        return flags;
    }

    public byte[] getPayload() {
        return payload;
    }
}
