# Quy Chuẩn Lập Trình & Ràng Buộc Kỹ Thuật
## Dự án SecureChat E2EE — Phiên bản 2.0

> **Môn học:** Mã hóa ứng dụng — CSC15003  
> **GVHD:** ThS. Mai Anh Tuấn  
> **Cập nhật:** 10/05/2026

Tài liệu này là ràng buộc kỹ thuật **BẮT BUỘC** cho toàn bộ 4 thành viên nhóm. Mọi nhánh code trước khi tạo Pull Request phải vượt qua toàn bộ checklist ở Phần 11. Phiên bản 2.0 bổ sung đầy đủ các khoảng trống so với bản gốc: interface contract, serialization format, wire protocol, secure coding, error handling, testing, và quy trình teamwork.

---

## Mục lục

- [0. Phân chia Module & Ownership](#0-phân-chia-module--ownership)
- [1. Môi trường & Workflow](#1-môi-trường--workflow)
- [2. Serialization Format & Wire Protocol](#2-serialization-format--wire-protocol)
- [3. Mật mã học Lõi](#3-mật-mã-học-lõi)
- [4. Hạ tầng PKI & Chứng chỉ](#4-hạ-tầng-pki--chứng-chỉ)
- [5. Kiến trúc Kerberos & Mạng](#5-kiến-trúc-kerberos--mạng)
- [6. Error Handling & Exception Hierarchy](#6-error-handling--exception-hierarchy)
- [7. Logging & Audit Log](#7-logging--audit-log)
- [8. Giao diện Người dùng (UI)](#8-giao-diện-người-dùng-ui)
- [9. Testing](#9-testing)
- [10. Secure Coding](#10-secure-coding)
- [11. Checklist Pull Request](#11-checklist-pull-request)

---

## 0. Phân chia Module & Ownership

> **Bắt buộc thống nhất trước Sprint 1. Không ai bắt đầu implement module chính khi chưa xong mục này.**

### 0.0 Danh sách thành viên

- **Gia Hiển:** Nhóm trưởng, đảm nhận implement chính.
- **Chị Bee:** Thành viên.
- **Trúc Ngọc:** Thành viên.
- **Phú Thọ:** Thành viên.

### 0.1 Bảng phân chia module

| Module | Branch name | Primary Owner | Secondary Reviewer | Phụ thuộc vào |
|--------|-------------|---------------|--------------------|----------------|
| CA Server (PKI) | `feature/ca-server` | Chị Bee | Trúc Ngọc | — (module gốc) |
| AS + TGS Server (KDC) | `feature/kdc-server` | Gia Hiển | Phú Thọ | CA Server |
| Chat Server | `feature/chat-server` | Phú Thọ | Gia Hiển | KDC Server |
| Client Application (UI + Crypto) | `feature/client-app` | Trúc Ngọc | Chị Bee | Tất cả server |
| **Shared Library** (common contracts) | `feature/shared-lib` | **Gia Hiển** & Tất cả | **Tất cả** | — |

### 0.2 Shared Library — Bắt buộc hoàn thành trước Sprint 2

`shared-lib` chứa toàn bộ Interface, constant, và data class dùng chung. Đây là **hợp đồng giao tiếp** giữa các module. **Không ai được bắt đầu implement module chính khi `shared-lib` chưa được review và merge vào `main`.**

Cấu trúc package bắt buộc:

```
securechat.common.crypto     → CryptoConstants, CryptoUtils (interface)
securechat.common.protocol   → TicketFormat, AuthenticatorFormat, PacketFrame
securechat.common.exception  → SecureChatException và toàn bộ subclass
securechat.common.config     → ServerConfig (ports, timeouts, constants)
```

> ⚠️ **CẢNH BÁO:** Nếu thay đổi bất kỳ Interface nào trong `shared-lib` SAU khi các module khác đã implement, bắt buộc phải thông báo qua group chat VÀ tạo issue trên repository trước khi commit. **Breaking change im lặng là nguyên nhân số 1 gây integration fail.**

### 0.3 Naming convention bắt buộc

| Đối tượng | Convention | Ví dụ |
|-----------|------------|-------|
| Branch | `feature/<module>-<chức-năng>` | `feature/kdc-tgt-issuance` |
| Commit message | `<type>(<scope>): <mô tả ngắn>` | `feat(kdc): add TGT issuance with CV` |
| Java class | `PascalCase` | `TicketGrantingServer` |
| Java method | `camelCase` | `issueServiceTicket()` |
| Java constant | `UPPER_SNAKE_CASE` | `MAX_TIME_SKEW_SECONDS` |
| Test class | `<ClassName>Test` | `AesGcmCipherTest` |
| Package | `securechat.<module>.<layer>` | `securechat.kdc.service` |

**Commit type hợp lệ:** `feat` · `fix` · `refactor` · `test` · `docs` · `chore`  
Commit message `fix`, `update`, `wip` sẽ bị yêu cầu rebase trước khi merge.

### 0.4 Định nghĩa "Done" cho mỗi Sprint

| Sprint | Module hoàn thành | Tiêu chí Done |
|--------|-------------------|---------------|
| Sprint 1 | Shared Library + Crypto core | Compile được, unit test coverage ≥ 80%, `shared-lib` merge vào `main` |
| Sprint 2 | CA Server | Cấp phát X.509 v3, OCSP responder hoạt động, integration test với stub client pass |
| Sprint 3 | AS + TGS Server | Luồng TGT → ST đầy đủ, chống replay pass, integration test với CA thật |
| Sprint 4 | Chat Server + Client | E2EE handshake hoàn chỉnh, 2 client chat được với nhau |
| Sprint 5 | Full integration + hardening | Tất cả attack scenario pass, audit log hoạt động, app đóng gói chạy được |

---

## 1. Môi trường & Workflow

### 1.1 Ngôn ngữ, thư viện, và build tool

| Thành phần | Phiên bản bắt buộc | Ghi chú |
|------------|-------------------|---------|
| Java | **25 LTS** (OpenJDK hoặc Eclipse Temurin) | Sử dụng bản LTS mới nhất |
| BouncyCastle | `bcprov-jdk18on:1.78.1` + `bcpkix-jdk18on:1.78.1` | Cả hai artifact bắt buộc |
| Build tool | **Maven 3.9+**, file `pom.xml` commit vào repo | Không dùng Gradle (thống nhất 1 tool) |
| SQLite JDBC | `org.xerial:sqlite-jdbc:3.45.3.0` | JDBC thuần Java |
| JUnit | `junit-jupiter:5.10.2` | Framework test bắt buộc |
| Mockito | `mockito-core:5.11.0` | Mock trong unit test |
| Jackson | `jackson-databind:2.17.1` | JSON serialization — thêm vào `pom.xml` chung |
| SLF4J + Logback | `slf4j-api:2.0.13` + `logback-classic:1.5.6` | Logging — không dùng `System.out.println` |

> ⚠️ **RÀNG BUỘC:** TUYỆT ĐỐI không import thêm bất kỳ thư viện mật mã nào khác ngoài BouncyCastle + SunMSCAPI. Các thư viện non-crypto được phép nhưng **PHẢI được thêm vào `pom.xml` chung** và thông báo cho cả nhóm. Không ai được thêm dependency cục bộ.

### 1.2 Platform support và Private Key protection

Do Windows DPAPI (SunMSCAPI) chỉ available trên Windows, nhóm **phải thống nhất một trong hai phương án** và ghi rõ vào `README.md` trước Sprint 1:

- **Phương án A (Khuyến nghị):** Toàn bộ develop và demo trên Windows 10/11. SunMSCAPI là bắt buộc, không có fallback.
- **Phương án B:** Trên non-Windows, Private Key được bảo vệ bởi Java PKCS12 KeyStore mã hóa bằng mật khẩu. SunMSCAPI chỉ kích hoạt khi `System.getProperty("os.name")` chứa `"Windows"`.

```java
// Phương án B — platform detection (đặt trong KeyStoreManager.java)
private static final boolean IS_WINDOWS =
    System.getProperty("os.name", "").toLowerCase().contains("windows");

public KeyStore loadKeyStore() throws KeyStoreException {
    if (IS_WINDOWS) {
        return KeyStore.getInstance("Windows-MY", "SunMSCAPI");
    } else {
        // Fallback: PKCS12 với mật khẩu bảo vệ
        return KeyStore.getInstance("PKCS12");
    }
}
```

> 🔒 **BẮT BUỘC:** Dù chọn phương án nào, Private Key **KHÔNG BAO GIỜ** được lưu dưới dạng plaintext trên disk hoặc xuất hiện dưới dạng plaintext trên RAM của application layer.

### 1.3 Quy trình Git

- Mỗi feature/bugfix làm trên branch riêng. **Không commit thẳng vào `main`.**
- Pull Request bắt buộc ít nhất **1 approve** từ Secondary Reviewer (theo bảng 0.1).
- CI phải pass (compile + unit test) trước khi merge. **Không merge khi test đỏ.**
- Tag phiên bản theo Semantic Versioning: `v0.1.0` (shared-lib) → `v0.2.0` (CA) → ... → `v1.0.0` (full integration).

---

## 2. Serialization Format & Wire Protocol

> **Đây là phần dễ gây integration fail nhất.** Người làm AS và người làm Client phải serialize/deserialize theo CÙNG MỘT format. Sai lệch dù 1 byte sẽ khiến decrypt fail hoàn toàn mà không có lỗi rõ ràng.

### 2.1 TCP Framing — Length-prefix bắt buộc

Tất cả dữ liệu truyền qua Java Socket phải dùng **length-prefix framing**. Không dùng delimiter.

```
+--------+---------+----------+----------------------------+-----------+
|  TYPE  | VERSION |  FLAGS   |     PAYLOAD LENGTH         |  PAYLOAD  |
| 1 byte |  1 byte |  2 bytes |  4 bytes (uint32 big-end)  |  N bytes  |
+--------+---------+----------+----------------------------+-----------+
```

**Bảng TYPE values:**

| Hex | Tên hằng số | Mô tả |
|-----|-------------|-------|
| `0x01` | `TYPE_CSR_REQUEST` | Client gửi CSR lên CA |
| `0x02` | `TYPE_CERT_RESPONSE` | CA trả Certificate |
| `0x03` | `TYPE_TGT_REQUEST` | Client xin TGT từ AS |
| `0x04` | `TYPE_TGT_RESPONSE` | AS trả TGT + session key |
| `0x05` | `TYPE_ST_REQUEST` | Client xin ST từ TGS |
| `0x06` | `TYPE_ST_RESPONSE` | TGS trả ST |
| `0x07` | `TYPE_CHAT_HANDSHAKE` | Handshake ECDHE + Kyber |
| `0x08` | `TYPE_CHAT_MESSAGE` | Tin nhắn đã mã hóa |
| `0x09` | `TYPE_OCSP_REQUEST` | Client xin OCSP status |
| `0x0A` | `TYPE_OCSP_RESPONSE` | Server trả OCSP response |
| `0xFF` | `TYPE_ERROR` | Generic error response |

```java
// PacketFrame.java trong shared-lib — cách đọc/ghi chuẩn
public class PacketFrame {
    public static final int HEADER_SIZE = 8; // 1 + 1 + 2 + 4

    public static void write(OutputStream out, byte type, byte[] payload) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeByte(type);
        dos.writeByte(0x01);          // VERSION
        dos.writeShort(0x0000);       // FLAGS (reserved)
        dos.writeInt(payload.length); // PAYLOAD LENGTH (big-endian)
        dos.write(payload);
        dos.flush();
    }

    public static PacketFrame read(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        byte type    = dis.readByte();
        byte version = dis.readByte();
        short flags  = dis.readShort();
        int length   = dis.readInt();
        if (length < 0 || length > 10 * 1024 * 1024) { // max 10 MB
            throw new FramingException("Invalid payload length: " + length);
        }
        byte[] payload = dis.readNBytes(length);
        return new PacketFrame(type, version, flags, payload);
    }
}
```

### 2.2 Payload format — JSON UTF-8

Tất cả PAYLOAD là **JSON được encode UTF-8**. Không dùng Java Serialization. Không dùng binary custom format.

### 2.3 JSON schema cố định cho từng loại packet

> ⚠️ Tên field trong JSON là **hợp đồng bất biến**. Không được tự ý đổi tên, thêm field bắt buộc, hay thay đổi kiểu dữ liệu mà không cập nhật schema này và thông báo nhóm.

**TGT Request (Client → AS):**
```json
{
  "clientId":  "string — định danh người dùng",
  "targetTgs": "string — FQDN của TGS (ví dụ: tgs.securechat.local)",
  "nonce":     "string — base64(16 random bytes)",
  "cert":      "string — base64(DER-encoded X.509 certificate của client)"
}
```

**TGT Response (AS → Client) — toàn bộ payload là JSON, tgt và response đã mã hóa:**
```json
{
  "tgt":      "string — base64(Hybrid_Encrypt(PU_TGS, tgt_json_bytes))",
  "response": "string — base64(Hybrid_Encrypt(PU_client, response_inner_json_bytes))"
}
```

**TGT inner JSON (trước khi mã hóa bằng PU_TGS):**
```json
{
  "clientId":   "string",
  "targetTgs":  "string",
  "issuedAt":   1715000000,
  "expiresAt":  1715028800,
  "sessionKey": "string — base64(K_A_TGS, 32 bytes AES-256 key)",
  "renewable":  true,
  "cv":         "string — ví dụ: ENCRYPT_ONLY|TGS_SERVICE|8H_EXPIRY"
}
```

**ST inner JSON (trước khi mã hóa bằng PU_ChatServer):**
```json
{
  "clientId":     "string",
  "clientPubKey": "string — base64(X.509 SubjectPublicKeyInfo DER của client)",
  "targetServer": "string — FQDN của Chat Server",
  "issuedAt":     1715000000,
  "expiresAt":    1715028800,
  "sessionKey":   "string — base64(K_A_Chat, 32 bytes)",
  "cv":           "string — ENCRYPT_ONLY|CHAT_SERVICE|8H_EXPIRY"
}
```

**Authenticator JSON (trước khi mã hóa bằng session key):**
```json
{
  "clientId":  "string",
  "timestamp": 1715000000,
  "nonce":     "string — base64(16 random bytes)"
}
```

**AES-GCM Message JSON (chat payload trước khi mã hóa):**
```json
{
  "senderId":  "string",
  "content":   "string — nội dung tin nhắn plaintext",
  "sentAt":    1715000000
}
```

### 2.4 Encoding convention

| Kiểu dữ liệu | Encoding trong JSON | Ghi chú |
|--------------|---------------------|---------|
| `byte[]` | Base64 Standard (RFC 4648) | Dùng `Base64.getEncoder()`, không dùng URL-safe |
| Timestamp | Unix epoch **seconds** (`long`) | **KHÔNG** dùng ISO 8601 string |
| Public Key | X.509 SubjectPublicKeyInfo DER → Base64 | Dùng `key.getEncoded()` |
| Certificate | DER encode → Base64 | **Không** dùng PEM string (không có `-----BEGIN`) |
| Control Vector | Pipe-separated string | `"ENCRYPT_ONLY\|CHAT_SERVICE\|8H_EXPIRY"` |

---

## 3. Mật mã học Lõi

### 3.1 Mã hóa dữ liệu truyền tải — AES-256 GCM

Cấu trúc chuẩn của payload sau khi mã hóa (bytes concatenated):

```
[ Nonce: 12 bytes ][ Ciphertext: N bytes ][ Auth Tag: 16 bytes ]
```

```java
// AesGcmCipher.java trong shared-lib — implementation chuẩn (BouncyCastle)
public class AesGcmCipher {

    private static final int NONCE_SIZE = 12;
    private static final int TAG_BITS   = 128; // 16 bytes

    public static byte[] encrypt(byte[] key, byte[] plaintext) throws CryptoException {
        try {
            byte[] nonce = new byte[NONCE_SIZE];
            new SecureRandom().nextBytes(nonce); // sinh ngẫu nhiên mỗi lần gọi

            GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
            cipher.init(true, new AEADParameters(new KeyParameter(key), TAG_BITS, nonce, null));

            byte[] cipherAndTag = new byte[cipher.getOutputSize(plaintext.length)];
            int len = cipher.processBytes(plaintext, 0, plaintext.length, cipherAndTag, 0);
            cipher.doFinal(cipherAndTag, len);

            // [ nonce(12) | ciphertext+tag ]
            byte[] result = new byte[NONCE_SIZE + cipherAndTag.length];
            System.arraycopy(nonce, 0, result, 0, NONCE_SIZE);
            System.arraycopy(cipherAndTag, 0, result, NONCE_SIZE, cipherAndTag.length);
            return result;

        } catch (Exception e) {
            throw new CryptoException("AES-GCM encrypt failed", e);
        }
    }

    public static byte[] decrypt(byte[] key, byte[] cipherData) throws CryptoException {
        try {
            byte[] nonce      = Arrays.copyOfRange(cipherData, 0, NONCE_SIZE);
            byte[] cipherText = Arrays.copyOfRange(cipherData, NONCE_SIZE, cipherData.length);

            GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
            cipher.init(false, new AEADParameters(new KeyParameter(key), TAG_BITS, nonce, null));

            byte[] plaintext = new byte[cipher.getOutputSize(cipherText.length)];
            int len = cipher.processBytes(cipherText, 0, cipherText.length, plaintext, 0);
            cipher.doFinal(plaintext, len);
            return plaintext;

        } catch (InvalidCipherTextException e) {
            // MAC fail → throw ngay, KHÔNG tiếp tục xử lý
            throw new MacVerificationException("GCM tag verification failed — connection must be closed");
        } catch (Exception e) {
            throw new DecryptionException("AES-GCM decrypt failed", e);
        }
    }
}
```

**Quy tắc bắt buộc:**
- Nonce phải được sinh `SecureRandom` cho **MỖI TIN NHẮN**
- **KHÔNG** tái sử dụng Nonce với cùng một Session Key (vi phạm nghiêm trọng)
- Phía nhận **PHẢI** throw `MacVerificationException` và ngắt kết nối ngay nếu tag verify fail
- **KHÔNG** fallback sang giải mã khi tag sai, dù chỉ để debug

### 3.2 Dẫn xuất Khóa Phiên — HKDF-SHA256

```java
// HkdfKeyDerivation.java trong shared-lib
public static byte[] deriveSessionKey(
        byte[] ssEcdhe,   // 32 bytes shared secret từ ECDHE
        byte[] ssKyber,   // 32 bytes shared secret từ Kyber
        byte[] sessionNonce // 16 bytes, trao đổi trong handshake
) throws KeyDerivationException {

    // Thứ tự concatenate: SS_ECDHE TRƯỚC, SS_KYBER SAU — BẤT BIẾN
    byte[] ikm = new byte[ssEcdhe.length + ssKyber.length];
    System.arraycopy(ssEcdhe, 0, ikm, 0, ssEcdhe.length);
    System.arraycopy(ssKyber, 0, ikm, ssEcdhe.length, ssKyber.length);

    byte[] info = "SecureChat-v1".getBytes(StandardCharsets.UTF_8); // cố định

    try {
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(ikm, sessionNonce, info));
        byte[] masterKey = new byte[32]; // 256-bit cho AES-256
        hkdf.generateBytes(masterKey, 0, 32);
        return masterKey;
    } catch (Exception e) {
        throw new KeyDerivationException("HKDF derivation failed", e);
    } finally {
        Arrays.fill(ikm, (byte) 0); // xóa IKM trung gian ngay sau dùng
    }
}
```

> ⚠️ **CRITICAL:** Thứ tự concatenate là `SS_ECDHE || SS_KYBER`. Hoán đổi thứ tự sẽ cho ra Master Key khác hoàn toàn. **Không được tự ý đổi thứ tự.**

Công thức tham chiếu:
```
Master_Session_Key = HKDF-SHA256(
    salt   = session_nonce  (16 bytes)
    ikm    = SS_ECDHE || SS_KYBER
    info   = "SecureChat-v1"
    length = 32 bytes
)
```

### 3.3 Dẫn xuất Khóa Database — PBKDF2

```java
// Pbkdf2KeyDerivation.java trong shared-lib
public static final int ITERATIONS  = 100_000; // cố định, không cấu hình
public static final int SALT_SIZE   = 32;       // bytes
public static final int KEY_SIZE    = 32;       // bytes (AES-256)

public static byte[] deriveDbKey(char[] password, byte[] salt)
        throws KeyDerivationException {
    // password phải là char[], KHÔNG phải String
    try {
        PKCS5S2ParametersGenerator gen =
            new PKCS5S2ParametersGenerator(new SHA256Digest());
        gen.init(PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(password),
                 salt, ITERATIONS);
        return ((KeyParameter) gen.generateDerivedParameters(KEY_SIZE * 8)).getKey();
    } catch (Exception e) {
        throw new KeyDerivationException("PBKDF2 failed", e);
    }
}

// Salt lưu ở bytes 0–31 của file DB (trước encrypted content)
// KHÔNG lưu salt vào code hay config file
```

### 3.4 Kháng lượng tử — Kyber ML-KEM-768

```java
// Sử dụng ML-KEM-768 (Kyber-768), chuẩn NIST FIPS 203, qua BouncyCastle v1.78+
// KyberKemService.java

// Key generation (phía Server, thực hiện một lần khi khởi động)
MLKEMParameterSpec spec = MLKEMParameterSpec.ml_kem_768;
KeyPairGenerator kpg = KeyPairGenerator.getInstance("ML-KEM", "BC");
kpg.initialize(spec);
KeyPair kyberPair = kpg.generateKeyPair();

// Encapsulation (phía Client — dùng public key của server)
MLKEMPublicKey serverKyberPub = (MLKEMPublicKey) kyberPair.getPublic();
// ... gửi serverKyberPub.getEncoded() cho client qua channel an toàn

// Trên Client:
KeyFactory kf = KeyFactory.getInstance("ML-KEM", "BC");
MLKEMPublicKey pub = (MLKEMPublicKey) kf.generatePublic(
    new X509EncodedKeySpec(serverKyberPubBytes));
SecretKeyWithEncapsulation encResult =
    (SecretKeyWithEncapsulation) KeyGenerator.getInstance("ML-KEM", "BC")
        .generateKey(); // BouncyCastle API — xem doc v1.78+
byte[] ciphertext  = encResult.getEncapsulation(); // gửi cho server
byte[] SS_KYBER    = encResult.getEncoded();        // 32 bytes shared secret

// Decapsulation (phía Server)
// ... nhận ciphertext từ client
byte[] SS_KYBER = kyberDecapsulate(kyberPair.getPrivate(), ciphertext);
```

### 3.5 An toàn bộ nhớ — Memory Safety

| Đối tượng nhạy cảm | Kiểu bắt buộc | Thời điểm xóa |
|--------------------|---------------|----------------|
| Password người dùng | `char[]` (KHÔNG phải `String`) | Ngay sau khi PBKDF2 chạy xong |
| ECDHE private params | `byte[]` hoặc `BCECPrivateKey` | Ngay sau khi ngắt kết nối |
| Session Key (AES) | `byte[]` | Khi socket close hoặc session expire |
| Master Session Key | `byte[]` | Khi socket close |
| Kyber private key | `byte[]` | Sau khi `SS_KYBER` đã được lấy xong |

```java
// Pattern bắt buộc — luôn đặt clear trong finally block
byte[] sessionKey = null;
char[] password   = null;
try {
    password   = getPasswordFromUI();     // char[], không phải String
    sessionKey = deriveKey(password, ...);
    // ... dùng key
} finally {
    if (password   != null) Arrays.fill(password, '\0');
    if (sessionKey != null) Arrays.fill(sessionKey, (byte) 0);
}
```

---

## 4. Hạ tầng PKI & Chứng chỉ

### 4.1 Key Usage bắt buộc (RFC 5280)

| Entity | KeyUsage flags | ExtendedKeyUsage | Thuật toán |
|--------|---------------|------------------|------------|
| Root CA | `keyCertSign` + `cRLSign` | Không có | RSA-2048 |
| AS Server | `digitalSignature` + `keyEncipherment` | `id-kp-serverAuth` | RSA-2048 |
| TGS Server | `digitalSignature` + `keyEncipherment` | `id-kp-serverAuth` | RSA-2048 |
| Chat Server | `keyEncipherment` | `id-kp-serverAuth` | RSA-2048 |
| Client | `digitalSignature` | `id-kp-clientAuth` | RSA-2048 |

```java
// CertificateBuilder.java — cách set KeyUsage đúng cho Chat Server
X509v3CertificateBuilder builder = new X509v3CertificateBuilder(...);

builder.addExtension(Extension.keyUsage, true,
    new KeyUsage(KeyUsage.keyEncipherment));  // Chat Server: chỉ keyEncipherment

builder.addExtension(Extension.extendedKeyUsage, false,
    new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
```

> ⚠️ **BẮT BUỘC:** KHÔNG được tắt validation hoặc catch exception của `ExtendedKeyUsage` để "tạm thời vượt qua lỗi". Nếu validation fail → đọc lại cert generation code, không bypass.

### 4.2 OCSP Stapling

- **Cấm** bắt Client tải toàn bộ file CRL
- Chat Server và KDC phải tự fetch OCSP response từ CA **mỗi 4 giờ** (không phải mỗi connection)
- OCSP response phải được CA ký số — Client verify chữ ký trước khi tin tưởng
- Nếu OCSP response hết hạn hoặc verify fail → ngắt kết nối, log lỗi, **KHÔNG** tiếp tục

```java
// OcspStaplingManager.java — scheduled refresh
@Scheduled(fixedRate = 4 * 60 * 60 * 1000) // mỗi 4 giờ
public void refreshOcspResponse() {
    try {
        this.cachedOcspResponse = fetchAndVerifyOcspFromCa();
        log.info("OCSP response refreshed, nextUpdate={}",
                 cachedOcspResponse.getNextUpdate());
    } catch (Exception e) {
        log.error("OCSP refresh failed — existing response still in use", e);
    }
}
```

### 4.3 Certificate chain validation

```java
// Bắt buộc validate chain từ leaf → Root CA trước khi dùng bất kỳ cert nào
public void validateChain(X509Certificate[] chain, Set<TrustAnchor> trustAnchors)
        throws ChainValidationException {
    try {
        CertPath certPath = CertificateFactory.getInstance("X.509")
            .generateCertPath(Arrays.asList(chain));
        CertPathValidator validator = CertPathValidator.getInstance("PKIX", "BC");
        PKIXParameters params = new PKIXParameters(trustAnchors);
        params.setRevocationEnabled(false); // OCSP stapling xử lý riêng
        validator.validate(certPath, params);
    } catch (CertPathValidatorException e) {
        throw new ChainValidationException("Chain validation failed: " + e.getMessage(), e);
    }
}
// Root CA cert phải được distribute cùng application — KHÔNG download runtime
```

---

## 5. Kiến trúc Kerberos & Mạng

### 5.1 Cấu hình Port — cố định trong `ServerConfig`

```java
// ServerConfig.java trong shared-lib — KHÔNG hardcode ở nơi khác
public final class ServerConfig {
    public static final String CA_HOST   = "localhost"; // thay bằng IP thực khi deploy
    public static final String AS_HOST   = "localhost";
    public static final String TGS_HOST  = "localhost";
    public static final String CHAT_HOST = "localhost";

    public static final int CA_PORT   = 8443; // CA Server (PKI + OCSP)
    public static final int AS_PORT   = 8881; // Authentication Server
    public static final int TGS_PORT  = 8882; // Ticket Granting Server
    public static final int CHAT_PORT = 8883; // Chat Server
    public static final int OCSP_PORT = 8884; // OCSP Responder endpoint

    public static final int CONNECT_TIMEOUT_MS = 10_000; //  10 giây
    public static final int READ_TIMEOUT_MS    = 30_000; //  30 giây
    public static final int NTP_TIMEOUT_MS     =  5_000; //   5 giây
    public static final int NTP_RETRY_COUNT    = 3;
}
```

### 5.2 Control Vector (CV) — định nghĩa và kiểm tra

```java
// ControlVector.java trong shared-lib
public final class ControlVector {
    public static final String ENCRYPT_ONLY = "ENCRYPT_ONLY";
    public static final String CHAT_SERVICE = "CHAT_SERVICE";
    public static final String TGS_SERVICE  = "TGS_SERVICE";
    public static final String EXPIRY_8H    = "8H_EXPIRY";
    public static final String EXPIRY_24H   = "24H_EXPIRY";

    // CV chuẩn cho ST
    public static final String ST_CV = ENCRYPT_ONLY + "|" + CHAT_SERVICE + "|" + EXPIRY_8H;

    // Chat Server PHẢI gọi hàm này trước khi dùng key từ ST
    public static void validateForChatService(String cv) throws ControlVectorException {
        if (cv == null || !cv.contains(CHAT_SERVICE)) {
            throw new ControlVectorException(
                "CV does not contain CHAT_SERVICE flag: " + cv);
        }
        if (!cv.contains(ENCRYPT_ONLY)) {
            throw new ControlVectorException(
                "CV does not contain ENCRYPT_ONLY flag: " + cv);
        }
    }
}
```

### 5.3 Anti-Replay Attack — Timestamp + Nonce

```java
// ReplayDefenseService.java
public final class CryptoConstants {
    public static final int MAX_TIME_SKEW_SECONDS = 300; // 5 phút — KHÔNG cấu hình ngoài
    public static final int NONCE_CACHE_TTL_SECONDS = 600; // 10 phút
    public static final int NONCE_SIZE_BYTES = 16;
}

// Kiểm tra trên server khi nhận Authenticator
public void validateAuthenticator(AuthenticatorJson auth)
        throws ReplayAttackException {
    long now = Instant.now().getEpochSecond();
    long skew = Math.abs(now - auth.getTimestamp());

    if (skew > CryptoConstants.MAX_TIME_SKEW_SECONDS) {
        throw new ReplayAttackException(
            "Timestamp skew " + skew + "s exceeds limit of "
            + CryptoConstants.MAX_TIME_SKEW_SECONDS + "s");
    }
    if (nonceCache.contains(auth.getNonce())) {
        throw new ReplayAttackException("Nonce already used: " + auth.getNonce());
    }
    nonceCache.put(auth.getNonce(), now); // TTL = NONCE_CACHE_TTL_SECONDS
}
```

**Quy tắc NTP:**
- Client phải sync NTP trước khi gửi Authenticator
- Timeout: `ServerConfig.NTP_TIMEOUT_MS` (5 giây), retry `ServerConfig.NTP_RETRY_COUNT` (3 lần)
- Nếu NTP fail sau 3 retry → tự động chuyển sang **Nonce-only mode**, log `WARN`

### 5.4 Session Ticket & RENEWABLE flag

- TGT và ST cấp ra phải có `"renewable": true` (xem JSON schema mục 2.3)
- **Gia hạn vé:** Client gửi TGT cũ + Authenticator mới. TGS kiểm tra vé còn trong renewable window
- **1-RTT reconnect:** Chat Server encrypt toàn bộ Session Context bằng server-only key, gửi cho Client sau handshake. Client gửi lại khi reconnect — giảm từ 4 vòng xuống 1-RTT

---

## 6. Error Handling & Exception Hierarchy

> Tất cả exception **phải** extend từ `SecureChatException`. Không throw raw `Exception`, `RuntimeException`, hay `IOException` ra ngoài module boundary.

### 6.1 Exception hierarchy (định nghĩa trong `shared-lib`)

```
SecureChatException  (checked)
├── CryptoException
│   ├── MacVerificationException    // GCM tag fail → ngắt kết nối NGAY
│   ├── KeyDerivationException      // HKDF / PBKDF2 fail
│   └── DecryptionException         // Giải mã fail (không phải MAC)
├── ProtocolException
│   ├── ReplayAttackException       // Timestamp / Nonce vi phạm
│   ├── InvalidTicketException      // Ticket hết hạn, signature sai
│   ├── ControlVectorException      // CV không hợp lệ
│   └── FramingException            // Gói tin sai format TCP frame
├── PkiException
│   ├── CertificateRevokedException
│   ├── CertificateExpiredException
│   └── ChainValidationException
└── NetworkException
    └── ConnectionTimeoutException
```

### 6.2 Hành động bắt buộc khi gặp từng loại exception

| Exception | Server action | Client action | Log level |
|-----------|--------------|---------------|-----------|
| `MacVerificationException` | Ngắt kết nối **NGAY**, không gửi response | Hiển thị lỗi generic, disconnect | `ERROR` + security audit |
| `ReplayAttackException` | Reject request, log IP + clientId | Retry với nonce mới | `WARN` + security audit |
| `InvalidTicketException` | Reject, yêu cầu xin vé mới | Xin lại TGT/ST | `WARN` |
| `CertificateRevokedException` | Ngắt kết nối, từ chối reconnect | Thông báo user, không reconnect | `ERROR` |
| `ControlVectorException` | Ngắt kết nối | Log và disconnect | `ERROR` |
| `FramingException` | Ngắt kết nối | Log và disconnect | `ERROR` |
| `ConnectionTimeoutException` | Log và đóng socket | Retry tối đa 3 lần, rồi báo user | `WARN` |

> 🚫 **CẤM:**
> - KHÔNG được `catch MacVerificationException` và tiếp tục xử lý
> - KHÔNG được log plaintext của key, password, hay sensitive `byte[]` bất kể log level
> - KHÔNG được trả lỗi chi tiết nguyên nhân fail cho phía client (dùng generic message)

```java
// Pattern đúng khi nhận message — server side
try {
    byte[] plaintext = AesGcmCipher.decrypt(sessionKey, encryptedPayload);
    handleMessage(plaintext);
} catch (MacVerificationException e) {
    log.error("[AUDIT] MAC_FAIL sessionId={} clientIp={}", sessionId, clientIp);
    socket.close(); // ngắt ngay, không gửi bất kỳ response nào
} catch (DecryptionException e) {
    log.error("Decryption error, closing connection", e);
    socket.close();
}
```

---

## 7. Logging & Audit Log

### 7.1 Framework bắt buộc

Dùng **SLF4J API** với **Logback** implementation. **KHÔNG** dùng `System.out.println`, `System.err`, hay `java.util.logging`.

```xml
<!-- logback.xml — đặt trong src/main/resources -->
<configuration>
  <!-- Application log -->
  <appender name="APP_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/app.log</file>
    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- Security audit log — file riêng biệt -->
  <appender name="AUDIT_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/audit.log</file>
    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [AUDIT] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <logger name="securechat.audit" level="INFO" additivity="false">
    <appender-ref ref="AUDIT_FILE"/>
  </logger>

  <root level="INFO">
    <appender-ref ref="APP_FILE"/>
  </root>
</configuration>
```

### 7.2 Sự kiện bắt buộc phải ghi audit log

| Sự kiện | Log level | Thông tin bắt buộc |
|---------|-----------|-------------------|
| Cấp TGT thành công | `INFO` | `clientId`, `issuedAt`, `expiresAt`, `clientIP` |
| Cấp ST thành công | `INFO` | `clientId`, `targetServer`, `issuedAt`, `expiresAt` |
| Xác thực cert thành công | `INFO` | `clientId`, `certSerial`, `certExpiry` |
| Reject Replay Attack | `WARN` | `clientId`, `clientIP`, `timestamp_received`, `reason` |
| MAC verification fail | `ERROR` | `sessionId` (không log key), `clientIP`, `timestamp` |
| Cert bị revoked | `ERROR` | `clientId`, `certSerial`, `revokedAt` |
| Chain validation fail | `ERROR` | `clientId`, `reason`, `clientIP` |
| Login thành công | `INFO` | `clientId`, `clientIP`, `timestamp` |
| Login thất bại | `WARN` | `clientId`, `clientIP`, `reason` (generic) |
| Session kết thúc | `INFO` | `sessionId`, `clientId`, `duration_seconds` |

```java
// Cách log chuẩn — dùng logger riêng cho audit
private static final Logger auditLog = LoggerFactory.getLogger("securechat.audit");

// Ví dụ đúng
auditLog.info("TGT_ISSUED clientId={} ip={} issued={} expires={}",
              clientId, clientIp, issuedAt, expiresAt);

// Ví dụ SAI — log sensitive data
// auditLog.info("TGT_ISSUED sessionKey={}", Base64.encode(sessionKey)); ← VI PHẠM
```

---

## 8. Giao diện Người dùng (UI)

### 8.1 SwingWorker — bắt buộc cho mọi tác vụ nặng

```java
// Pattern bắt buộc — MỌI crypto và network call trong UI phải qua SwingWorker
new SwingWorker<HandshakeResult, Void>() {

    @Override
    protected HandshakeResult doInBackground() throws Exception {
        // ĐẶT TẤT CẢ Ở ĐÂY: PBKDF2, Kyber KEM, ECDHE, socket connect, ...
        return chatService.performHandshake(serverConfig);
    }

    @Override
    protected void done() {
        try {
            HandshakeResult result = get();
            updateSecurityPanel(result); // chỉ update UI ở done() — EDT thread
            enableChatInput(true);
        } catch (ExecutionException e) {
            // Hiển thị generic message, KHÔNG leak exception detail ra UI
            showErrorDialog("Kết nối thất bại. Vui lòng thử lại.");
            log.error("Handshake failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}.execute();

// CẤM: không gọi crypto/network trực tiếp từ ActionListener hoặc bất kỳ EDT handler nào
```

### 8.2 Security Monitor Panel — thông tin hiển thị bắt buộc

Panel hiển thị real-time, luôn visible khi đang trong phiên chat:

```
┌─────────────────────────────────────────────┐
│  🔒 Trạng thái:  CONNECTED (E2EE)           │
│  🎟  TGT còn lại: 6h 42m                    │
│  🎟  ST còn lại:  6h 42m                    │
│  🔐  Mã hóa:     AES-256-GCM + ECDHE + Kyber-768 │
│  🪪  Chứng chỉ:  VALID (hết hạn 2027-01-01) │
│  💬  Tin nhắn:   Đã gửi 12 · Đã nhận 8     │
└─────────────────────────────────────────────┘
```

---

## 9. Testing

### 9.1 Coverage tối thiểu theo module

| Module | Line coverage | Nội dung bắt buộc |
|--------|--------------|-------------------|
| `shared-lib` crypto utils | ≥ 90% | AES-GCM encrypt/decrypt, HKDF, PBKDF2, memory zero-fill |
| CA Server | ≥ 75% | Cấp cert, chain validation, OCSP response |
| KDC (AS + TGS) | ≥ 75% | TGT issuance, ST issuance, replay reject, CV check |
| Chat Server | ≥ 70% | Handshake, E2EE message routing, session ticket |
| Client crypto layer | ≥ 80% | Handshake flow, MAC fail handling, reconnect |

### 9.2 Test vectors dùng chung

Commit file `shared-lib/src/test/resources/test-vectors.json`. **Tất cả module dùng chung file này** để verify implementation của mình cho ra kết quả đúng.

```json
{
  "aes_gcm": {
    "key":        "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
    "nonce":      "000000000000000000000000",
    "plaintext":  "SecureChat-TestVector-v1",
    "_note":      "Người làm shared-lib precompute ciphertext và tag, commit vào đây"
  },
  "hkdf": {
    "ss_ecdhe":           "aabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccdd",
    "ss_kyber":           "eeff0011eeff0011eeff0011eeff0011eeff0011eeff0011eeff0011eeff0011",
    "session_nonce":      "deadbeefdeadbeefdeadbeefdeadbeef",
    "info":               "SecureChat-v1",
    "_note":              "Người làm shared-lib precompute expected_master_key, commit vào đây"
  },
  "pbkdf2": {
    "password":   "TestPassword123",
    "salt":       "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20",
    "iterations": 100000,
    "_note":      "Người làm shared-lib precompute expected_key, commit vào đây"
  }
}
```

### 9.3 Integration test bắt buộc trước mỗi merge vào `main`

```java
// Các test case bắt buộc — đặt trong src/test/integration/

// IT-01: Replay Attack phải bị reject
@Test
void replayAttack_shouldBeRejected() {
    Authenticator auth = createAuthenticator(Instant.now().minusSeconds(400)); // 6m40s ago
    assertThrows(ReplayAttackException.class,
        () -> tgsService.validateAuthenticator(auth));
}

// IT-02: MAC tamper phải throw exception
@Test
void macTamper_shouldThrowMacVerificationException() {
    byte[] encrypted = AesGcmCipher.encrypt(key, plaintext);
    encrypted[20] ^= 0xFF; // flip 1 bit trong ciphertext
    assertThrows(MacVerificationException.class,
        () -> AesGcmCipher.decrypt(key, encrypted));
}

// IT-03: HKDF phải cho ra master key đúng (dùng test vector)
@Test
void hkdf_shouldMatchTestVector() {
    TestVector tv = loadTestVector("hkdf");
    byte[] result = HkdfKeyDerivation.deriveSessionKey(
        tv.getSsEcdhe(), tv.getSsKyber(), tv.getSessionNonce());
    assertArrayEquals(tv.getExpectedMasterKey(), result);
}

// IT-04: Revoked cert phải fail khi xin vé
@Test
void revokedCert_shouldBeRejectedByAs() {
    X509Certificate revokedCert = loadRevokedCert();
    assertThrows(CertificateRevokedException.class,
        () -> asService.issueTgt(buildTgtRequest(revokedCert)));
}
```

---

## 10. Secure Coding

### 10.1 Quy tắc bắt buộc — 8 điều không được vi phạm

| # | Quy tắc | Sai | Đúng |
|---|---------|-----|------|
| 1 | Không dùng `String` cho password / key / secret | `String pass = getInput()` | `char[] pass = getInput()` |
| 2 | Zero-fill mảng nhạy cảm trong `finally` | Không có `finally` | `Arrays.fill(buf, (byte) 0)` trong `finally` |
| 3 | Không hardcode IP/Port ngoài `ServerConfig` | `new Socket("localhost", 8883)` | `new Socket(ServerConfig.CHAT_HOST, ServerConfig.CHAT_PORT)` |
| 4 | Không log sensitive data dù ở `DEBUG` | `log.debug("key={}", key)` | `log.debug("Key derived OK")` |
| 5 | Dùng `SecureRandom`, không dùng `Random` | `new Random().nextBytes(nonce)` | `new SecureRandom().nextBytes(nonce)` |
| 6 | Dùng `MessageDigest.isEqual` để so sánh MAC | `Arrays.equals(mac1, mac2)` | `MessageDigest.isEqual(mac1, mac2)` |
| 7 | Không catch generic `Exception` để suppress crypto error | `catch (Exception e) {}` | `catch (MacVerificationException e) { closeSocket(); }` |
| 8 | Không tái sử dụng Nonce AES-GCM | Dùng lại `nonce` biến toàn cục | `new SecureRandom().nextBytes(nonce)` mỗi message |

### 10.2 CryptoConstants — tất cả magic number phải ở đây

```java
// CryptoConstants.java trong shared-lib — thêm vào đây khi cần, KHÔNG hardcode chỗ khác
public final class CryptoConstants {
    // AES-GCM
    public static final int AES_KEY_SIZE_BYTES  = 32;
    public static final int GCM_NONCE_SIZE      = 12;
    public static final int GCM_TAG_BITS        = 128;

    // PBKDF2
    public static final int PBKDF2_ITERATIONS   = 100_000;
    public static final int PBKDF2_SALT_SIZE    = 32;

    // HKDF
    public static final String HKDF_INFO        = "SecureChat-v1";

    // Kerberos
    public static final int MAX_TIME_SKEW_SECONDS   = 300;  // 5 phút
    public static final int NONCE_CACHE_TTL_SECONDS = 600;  // 10 phút
    public static final int NONCE_SIZE_BYTES        = 16;
    public static final int TGT_LIFETIME_SECONDS    = 28_800; // 8 giờ
    public static final int ST_LIFETIME_SECONDS     = 28_800; // 8 giờ

    // Kyber
    public static final String KYBER_PARAM = "ml_kem_768"; // NIST FIPS 203

    private CryptoConstants() {} // không khởi tạo
}
```

---

## 11. Checklist Pull Request

> Tác giả tự kiểm tra trước khi tạo PR. Reviewer kiểm tra lại — từ chối merge nếu bất kỳ mục nào chưa đạt.

### 11.1 Checklist kỹ thuật

- [ ] **C01** — Compile không có warning (`javac -Xlint:all`)
- [ ] **C02** — Unit test pass 100%, coverage đạt mức yêu cầu (mục 9.1)
- [ ] **C03** — Không có `System.out.println` hay `System.err.println`
- [ ] **C04** — Không hardcode IP/Port ngoài `ServerConfig`
- [ ] **C05** — Không dùng `String` cho password hay key (dùng `char[]` / `byte[]`)
- [ ] **C06** — Mọi mảng nhạy cảm có `Arrays.fill(...)` trong `finally` block
- [ ] **C07** — Nonce AES-GCM được sinh `SecureRandom` cho **mỗi** tin nhắn
- [ ] **C08** — `MacVerificationException` → ngắt kết nối, không tiếp tục xử lý
- [ ] **C09** — Exception đúng hierarchy (extends `SecureChatException`)
- [ ] **C10** — Log không chứa `byte[]` hay `char[]` nhạy cảm
- [ ] **C11** — `SwingWorker` dùng cho mọi crypto/network trong UI thread
- [ ] **C12** — TCP framing đúng format mục 2.1 (8-byte header + length-prefix)
- [ ] **C13** — JSON field names đúng schema mục 2.3 (không đổi tên field)
- [ ] **C14** — Timestamp dùng Unix epoch **seconds** (`long`), không dùng ISO string
- [ ] **C15** — Thứ tự HKDF: `SS_ECDHE || SS_KYBER` (không hoán đổi)
- [ ] **C16** — `MessageDigest.isEqual` dùng để so sánh MAC (không dùng `Arrays.equals`)
- [ ] **C17** — `ControlVector.validateForChatService()` được gọi trước khi dùng session key

### 11.2 Checklist teamwork

- [ ] **T01** — Branch name đúng convention mục 0.3
- [ ] **T02** — Tất cả commit message đúng format `<type>(<scope>): <mô tả>`
- [ ] **T03** — Không thêm dependency mới vào `pom.xml` mà chưa thông báo nhóm
- [ ] **T04** — Nếu thay đổi Interface trong `shared-lib` → đã tạo issue và thông báo trước
- [ ] **T05** — Secondary Reviewer (theo bảng 0.1) đã approve
- [ ] **T06** — Integration test với module phụ thuộc đã pass

---

> 📋 **Tài liệu này được cập nhật theo từng sprint.** Mọi thay đổi phải được cả 4 thành viên đồng ý và commit vào repository.  
> **Phiên bản:** 2.0 — **Cập nhật:** 10/05/2026