# CA-Server Module: Báo Cáo Cài Đặt & Hoàn Thành Sprint
**Dự Án: SecureChat E2EE**
**Cập nhật cuối:** 10/05/2026
**Trạng thái:** ✅ Hoàn thành (Production Ready)

---

## 1. Sơ Đồ Cấu Trúc File (Project Structure)

Cấu trúc các file chính trong module `ca-server` và các thành phần liên quan tại `shared-lib`:

```text
c:\SECURETALK\src\ca-server\src\main\java\vn\edu\hcmus\securechat\ca\
├── CaServerMain.java               # Entry point, xử lý mạng & điều phối request
├── service/
│   ├── CertificateAuthority.java   # Logic cấp chứng chỉ X.509 v3 (BouncyCastle)
│   └── OcspResponder.java          # Xử lý trạng thái thu hồi & Ký OCSP Response
└── storage/
    └── CertificateStorage.java     # Quản lý Persistence (SQLite) cho chứng chỉ

c:\SECURETALK\src\shared-lib\src\main\java\vn\edu\hcmus\securechat\common\
├── protocol\dto\                   # Các Data Transfer Objects dùng chung
│   ├── CertificateSigningRequest.java
│   ├── CertificateResponse.java
│   ├── OcspRequest.java
│   └── OcspResponse.java
└── crypto\
    └── KeyStoreManager.java        # Quản lý Windows-MY KeyStore (SunMSCAPI)
```

---

## 2. Logic Cài Đặt Chi Tiết

### 2.1 Luồng Xử Lý Cấp Chứng Chỉ (CSR Flow)
1. **Tiếp nhận**: `CaServerMain` nhận gói tin `TYPE_CSR_REQUEST` qua Socket.
2. **Giải mã**: Sử dụng `JsonSerializer` để chuyển payload byte[] sang đối tượng `CertificateSigningRequest`.
3. **Xác thực (Validation)**:
    - Kiểm tra các trường bắt buộc (SubjectDN, PublicKey, Nonce, Signature).
    - **Verify Signature**: Sử dụng Public Key của client để xác thực chữ ký trên dữ liệu CSR, chống giả mạo.
4. **Cấp phát (Issuance)**: 
    - `CertificateAuthority` sử dụng BouncyCastle để tạo chứng chỉ X.509 v3.
    - Thiết lập các extension bắt buộc: `KeyUsage` (Digital Signature, Key Encipherment), `BasicConstraints` (CA=False).
    - Ký bằng **CA Private Key** lấy từ Windows KeyStore.
5. **Lưu trữ**: Lưu thông tin cert (serial, fingerprint, DER bytes) vào SQLite qua `CertificateStorage`.
6. **Phản hồi**: Trả về `CertificateResponse` chứa chứng chỉ mới và chuỗi CA (CA Chain).

### 2.2 Luồng Kiểm Tra Trạng Thái (OCSP Flow)
1. **Tiếp nhận**: Nhận gói tin `TYPE_OCSP_REQUEST`.
2. **Tra cứu**: `OcspResponder` truy vấn database SQLite để kiểm tra trạng thái của `serial` (GOOD, REVOKED, hoặc UNKNOWN).
3. **Ký số**: Tạo `OcspResponse` và ký bằng khóa riêng của CA (hoặc OCSP Signer) để đảm bảo tính toàn vẹn.
4. **Phản hồi**: Trả về gói tin `TYPE_OCSP_RESPONSE`.

---

## 3. Các File Thay Đổi & Hoàn Thiện

| File | Loại thay đổi | Mô tả chi tiết |
| :--- | :--- | :--- |
| `CaServerMain.java` | **Hoàn thiện** | Cài đặt logic handle CSR/OCSP, thêm verify signature cho CSR, đồng bộ Serial Number. |
| `CertificateAuthority.java` | **Hoàn thiện** | Kết nối Windows KeyStore, cài đặt logic cấp cert X.509 v3 với BouncyCastle. |
| `OcspResponder.java` | **Hoàn thiện** | Logic kiểm tra trạng thái và ký OCSP response theo chuẩn RFC 6960 (simplified). |
| `CertificateStorage.java` | **Hoàn thiện** | Cài đặt schema SQLite, lưu trữ và truy vấn trạng thái chứng chỉ. |
| `KeyStoreManager.java` | **Sử dụng** | Cung cấp interface an toàn để truy cập Windows-MY mà không lộ Private Key. |

---

## 4. Các Công Việc Đã Hoàn Thành (Definition of Done)

- [x] **Hạ tầng mạng**: Server chạy đa luồng, xử lý TCP Framing đúng chuẩn 8-byte header.
- [x] **An toàn khóa**: Khóa CA được lưu trong Windows KeyStore, không bao giờ xuất hiện dưới dạng plaintext trên đĩa.
- [x] **Xác thực CSR**: Chống tấn công giả mạo bằng cách kiểm tra chữ ký trên mỗi yêu cầu cấp cert.
- [x] **Đúng chuẩn PKI**: Chứng chỉ cấp ra có đầy đủ KeyUsage và ExtendedKeyUsage theo yêu cầu hệ thống.
- [x] **OCSP Responder**: Phản hồi trạng thái chứng chỉ thời gian thực, hỗ trợ thu hồi (Revocation).
- [x] **Persistence**: Lưu trữ bền vững bằng SQLite, hỗ trợ tra cứu theo Serial hoặc Fingerprint.
- [x] **Audit Logging**: Ghi lại mọi sự kiện quan trọng (Cấp cert, Thu hồi, Lỗi xác thực) vào `audit.log`.

---

## 5. Hướng Dẫn Thiết Lập Môi Trường (Setup Guide)

Để server có thể khởi chạy thành công trên Windows, máy tính cần có chứng chỉ CA với alias chính xác là `securechat-ca` trong Personal Store.

**Lệnh khởi tạo nhanh (PowerShell Admin):**
```powershell
New-SelfSignedCertificate -Type Custom -Subject "CN=SecureChat Root CA, O=Hcmus, C=VN" -KeyUsage CertSign, CRLSign, DigitalSignature -FriendlyName "securechat-ca" -CertStoreLocation "Cert:\CurrentUser\My" -NotAfter (Get-Date).AddYears(5)
```

---

## 6. Kết Quả Chạy Thực Tế (Execution Results)

Server đã được kiểm thử và chạy thành công với các thông số sau:
- **Port**: 8443 (Listening)
- **Database**: `data/ca-server.db` (Initialized)
- **KeyStore**: Đã nạp thành công chứng chỉ từ `Windows-MY`.
- **Log xác nhận**: `CA Server is READY — listening on port 8443`.

---

## 7. Lưu Ý Cho Thành Viên (Team Notes)

- **Cấu hình**: Mọi thông số Port và Host phải lấy từ `ServerConfig`.
- **Database**: File database được lưu tại `data/ca-server.db`. Tuyệt đối không xóa file này khi đang chạy server.
- **KeyStore**: Máy chạy server CA phải có chứng chỉ CA được cài đặt vào **Personal Store** (Windows-MY) với alias là `securechat-ca`.
- **Lỗi**: Khi gặp lỗi `MacVerificationException` ở các module khác, hãy kiểm tra lại tính toàn vẹn của gói tin gửi từ CA.

---
*Báo cáo này thay thế cho bản phân tích skeleton trước đó.*
