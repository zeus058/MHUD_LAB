# SecureChat E2EE - Dự án Mã hóa Ứng dụng (CSC15003)

## 1. Thông tin nhóm và Đề tài
*   **Tên đề tài**: Hệ thống Nhắn tin Bảo mật Đầu cuối (E2EE) dựa trên Kiến trúc Kerberos và hạ tầng PKI.
*   **Giảng viên hướng dẫn**: ThS. Mai Anh Tuấn.
*   **Danh sách thành viên & Tỷ lệ đóng góp**:

| STT | Họ và Tên |   MSSV   | Vai trò chính | Tỷ lệ đóng góp |
|:---:|:---|:--------:|:---|:---:|
| 1 | **Gia Hiển** | 23120123 | Nhóm trưởng, Core Crypto, KDC, Shared-lib | 25% |
| 2 | **Anh Tuấn** | 23120184 | CA Server, PKI Workflow, OCSP | 25% |
| 3 | **Phú Thọ** | 23120169 | Chat Server, Network Protocol, Routing | 25% |
| 4 | **Trúc Ngọc** | 23120148 | UI/UX Swing, Client Integration, Testing | 25% |

---

## 2. Tổng kết chức năng (Checklist)

Hệ thống đã hoàn thiện đầy đủ các yêu cầu từ mức Cơ bản đến Nâng cao:

### 2.1. Mức Cơ bản (100%)
- [x] **Mã hóa đối xứng**: Sử dụng AES-256-GCM đảm bảo tính bí mật và toàn vẹn dữ liệu.
- [x] **Hybrid Encryption / KDC**: Sử dụng ECDHE để trao đổi khóa phiên E2EE; Sử dụng KDC (Kerberos) để cấp vé truy cập dịch vụ.
- [x] **Key Lifecycle**: Quy trình sinh khóa (ECDSA/ECDHE), phân phối (Cert/Ticket), thời hạn (Ticket Lifetime), và xoay khóa (Double Ratchet).
- [x] **Xác thực người dùng**: Định danh (Username) và xác thực (Mật khẩu/Chữ ký số) qua dịch vụ AS.
- [x] **Chống Replay**: Sử dụng kết hợp Nonce, Timestamp và cơ chế Challenge-Response.
- [x] **Xác thực khóa công khai**: Sử dụng Chứng chỉ số (Certificate) được ký bởi Root CA tin cậy.

### 2.2. Mức Khá (100%)
- [x] **Tách biệt Master Key & Session Key**: Khóa dài hạn của KDC/User dùng để phái sinh/giải mã khóa phiên trong vé (TGT/ST).
- [x] **KDC/KMS tập trung**: Triển khai `kdc-server` quản lý khóa và cấp phát vé tập trung.
- [x] **Mutual Authentication**: Client và Server xác thực lẫn nhau thông qua việc giải mã các thành phần trong Service Ticket.
- [x] **Phân quyền (RBAC)**: Gán vai trò `USER`, `ADMIN`, `MODERATOR` vào trường Control Vector của vé để kiểm soát tài nguyên.
- [x] **Chứng chỉ X.509**: Triển khai đầy đủ định dạng chứng chỉ X.509 cho người dùng và các server.
- [x] **Revocation**: Cơ chế thu hồi chứng chỉ qua giao thức OCSP và quy trình Revoke Request từ Client.
- [x] **Chống MITM**: Mọi hoạt động trao đổi khóa công khai đều được ký số và xác thực qua chuỗi chứng chỉ.

### 2.3. Mức Nâng cao (100%)
- [x] **Hạ tầng PKI đầy đủ**: Bao gồm CA (Cấp phát), Repository (Lưu trữ DB), và quy trình Đăng ký/Thu hồi.
- [x] **Certificate Chain Validation**: Kiểm tra chuỗi tin cậy từ chứng chỉ người dùng lên Root CA.
- [x] **Kerberos-like Ticketing**: Cơ chế TGT (để SSO) và ST (để truy cập Chat Service) hoàn chỉnh.
- [x] **Secure Audit Log**: Ghi nhật ký các sự kiện nhạy cảm (cấp khóa, đăng nhập) vào Hash-Chain có gắn HMAC chống sửa đổi.

---

## 3. Các tình huống tấn công & Cơ chế bảo vệ

| Tình huống tấn công | Cơ chế bảo vệ hiện tại |
|:---|:---|
| **Nghe lén trên đường truyền** | Mã hóa E2EE (AES-GCM) khiến Chat Server cũng không đọc được nội dung. |
| **Tấn công phát lại (Replay)** | Kiểm tra Timestamp (lệch tối đa 300s) và bộ đệm Nonce Cache để loại bỏ gói tin lặp. |
| **Giả mạo Server (MITM)** | Client xác thực chứng chỉ của Server qua PKI; Server phải chứng minh sở hữu khóa bí mật qua vé. |
| **Đánh cắp File khóa bí mật** | Khóa được lưu trong Windows KeyStore (SunMSCAPI), bảo vệ bởi DPAPI gắn liền với User Windows. |
| **Sửa đổi nhật ký hệ thống** | Audit Log được tổ chức dạng Hash-Chain; mỗi bản ghi phụ thuộc vào bản ghi trước và có HMAC. |
| **Trộm vé (Ticket Theft)** | Vé gắn liền với IP/ID của Client và có thời hạn ngắn; giải mã vé yêu cầu khóa phiên chỉ Client có. |

---

## 4. Hướng dẫn khởi chạy (Local & LAN)

### 1. Yêu cầu tiên quyết
*   **Java 25 LTS** và **Maven 3.9+**.
*   Hệ điều hành: **Windows 10/11** (Bắt buộc để chạy SunMSCAPI).

### 2. Biên dịch hệ thống
```bash
cd src
mvn clean install
```

### 3. Thứ tự khởi chạy các Server
Mở các terminal riêng biệt để chạy:
1.  **CA Server**: `mvn exec:java -pl ca-server`
2.  **KDC Server**: `mvn exec:java -pl kdc-server`
3.  **Chat Server**: `mvn exec:java -pl chat-server`
4.  **Client**: `mvn exec:java -pl client-app`

### 4. Lưu ý khi chạy trên mạng LAN
Nếu Client và Server nằm trên 2 máy khác nhau, dùng tham số `-D` để trỏ IP:
```bash
mvn exec:java -pl client-app -Dca.host=<IP_SERVER> -Das.host=<IP_SERVER> -Dchat.host=<IP_SERVER>
```

### 5. Hướng dẫn chạy mạng WAN (Tailscale)
Khi các thành viên ở các địa điểm khác nhau, sử dụng **Tailscale** để tạo mạng nội bộ ảo (VPN Mesh):

1.  **Cài đặt**: Tất cả thành viên cài đặt Tailscale và đăng nhập cùng một tài khoản hoặc cùng một tổ chức (Tailnet).
2.  **Xác định IP**: Mỗi máy sẽ được cấp một IP nội bộ dạng `100.x.y.z`.
3.  **Mô hình triển khai đề xuất**:
    *   **Máy 1 (CA + Client 1)**: Chạy `ca-server` và `client-app`.
    *   **Máy 2 (KDC + Client 2)**: Chạy `kdc-server` và `client-app`.
    *   **Máy 3 (Chat-Server + Client 3)**: Chạy `chat-server` và `client-app`.
    *   **Máy 4 (Client 4)**: Chỉ chạy `client-app`.
4.  **Khởi chạy với IP Tailscale**:
    Thay `<IP_CA>`, `<IP_KDC>`, `<IP_CHAT>` bằng IP Tailscale tương ứng của các máy:
    ```bash
    mvn exec:java -pl client-app -Dca.host=<IP_CA> -Das.host=<IP_KDC> -Dtgs.host=<IP_KDC> -Dchat.host=<IP_CHAT>
    ```

### 6. Hướng dẫn chạy qua mạng Internet (Cloud VPS)
Nếu bạn đã triển khai Backend lên Cloud (Azure, Oracle, DigitalOcean,...):
1. Đảm bảo đã mở cổng mạng (Tường lửa/Security Group) cho các port `8443, 8888, 9090` trên máy chủ Cloud.
2. Tại máy khách, trỏ trực tiếp các biến môi trường tới Public IP của máy chủ Cloud:
    ```bash
    mvn exec:java -pl client-app -Dca.host=<Public_IP> -Das.host=<Public_IP> -Dtgs.host=<Public_IP> -Dchat.host=<Public_IP>
    ```
*(Lưu ý: Hoặc bạn có thể sửa cố định các giá trị IP trong file `ServerConfig.java` rồi build ra ứng dụng `client-app.jar` độc lập để gửi cho người dùng).*

---

## 6. Cấu trúc và Lưu ý quan trọng
*   **Thư mục dữ liệu**: Toàn bộ DB và Keystore nằm tại `src/data/`. Tuyệt đối không xóa thư mục này nếu muốn giữ tài khoản.
*   **Tính di động**: Nhờ `PathUtil`, bạn có thể chép folder dự án sang máy khác, chạy Server là có thể dùng lại dữ liệu cũ ngay lập tức.
*   **An toàn**: Khóa bí mật không bao giờ rời khỏi máy của người dùng (Zero-Knowledge).

---

## Quyết định nền tảng (Windows-only)
Dự án tận dụng Windows Certificate Store để bảo vệ khóa, đảm bảo an toàn cấp độ hệ điều hành. Nếu chạy trên môi trường khác, hệ thống sẽ tự động dùng file `.p12` dự phòng trong `data/keys/`.

---

## Tác giả
Nhóm Mã hóa ứng dụng - CSC15003. Bản quyền thuộc về các thành viên nhóm.
