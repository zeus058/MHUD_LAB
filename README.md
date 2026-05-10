# SecureChat E2EE — Phiên bản 2.0

Chào mừng đến với dự án **SecureChat E2EE 2.0**. Hệ thống Nhắn tin Bảo mật Đầu cuối dựa trên Kiến trúc Kerberos V5, Hạ tầng Khóa Công khai (PKI) và Mật mã Kháng Lượng tử (Kyber ML-KEM).

## 1. Môi trường phát triển bắt buộc

- **Java Development Kit (JDK)**: Bắt buộc phiên bản **Java 17 LTS**.
- **Build Tool**: **Apache Maven 3.9+**
- **IDE**: IntelliJ IDEA, Eclipse hoặc VSCode (Khuyến nghị dùng IntelliJ).

### 🛠 Hướng dẫn Cài đặt Môi trường (Trên Windows) để chạy lệnh `mvn`

Nếu bạn gõ `mvn -v` trong Terminal và nhận được lỗi *“mvn is not recognized…”* hoặc lỗi *“The JAVA_HOME environment variable is not defined correctly”*, hãy làm theo các bước sau:

**Bước 1: Cài đặt và cấu hình JAVA_HOME (Bắt buộc)**
1. Tải và cài đặt **Java JDK 17** (nếu chưa cài). Mặc định Java sẽ được cài vào thư mục như `C:\Program Files\Java\jdk-17`.
2. Nhấn phím `Windows`, gõ "Environment Variables" và chọn **Edit the system environment variables**.
3. Bấm vào nút **Environment Variables...** ở góc dưới cùng.
4. Ở phần **System variables**, bấm **New** để tạo biến mới:
   - Tên biến (Variable name): `JAVA_HOME`
   - Giá trị (Variable value): Đường dẫn đến thư mục cài đặt JDK 17 (ví dụ: `C:\Program Files\Java\jdk-17`).

**Bước 2: Cài đặt Maven**
1. Truy cập trang chủ Maven: [https://maven.apache.org/download.cgi](https://maven.apache.org/download.cgi)
2. Tải file nén `.zip` (ví dụ: `apache-maven-3.9.6-bin.zip`) và giải nén vào ổ C (ví dụ: `C:\Program Files\apache-maven-3.9.6`).

**Bước 3: Cấu hình biến Path**
1. Cũng trong cửa sổ **Environment Variables**, tạo thêm một System variable mới:
   - Tên biến: `MAVEN_HOME`
   - Giá trị: `C:\Program Files\apache-maven-3.9.6`
2. Tìm biến **Path** trong danh sách **System variables**, bấm đúp vào nó (hoặc chọn và bấm Edit).
3. Bấm **New** và thêm dòng: `%JAVA_HOME%\bin`.
4. Bấm **New** và thêm dòng: `%MAVEN_HOME%\bin`.
5. Nhấn OK trên tất cả các cửa sổ để lưu lại.

**Bước 4: Khởi động lại Terminal**
Đóng hoàn toàn Terminal hiện tại đang mở và mở lại. Sau đó gõ lệnh `mvn -v`, bạn sẽ thấy thông tin của Apache Maven và phiên bản Java!


---

## 2. Cấu trúc Dự án

Dự án được xây dựng theo kiến trúc Multi-module Maven để tách biệt rõ ràng các thành phần hệ thống:

```text
Project/
├── .gitignore                     # Ignore build output, IDE, logs, DB, .env
├── README.md                      # Tài liệu hướng dẫn này
├── doc/                           # Tài liệu dự án
│   ├── BaoCao_SecureChat.md       #   Báo cáo luồng thuật toán & giao thức
│   └── Contrains.md               #   Ràng buộc kỹ thuật BẮT BUỘC
├── src/                           # Thư mục mã nguồn gốc
│   ├── pom.xml                    # Root POM (Quản lý chung Dependencies)
│   ├── shared-lib/                # [✅ HOÀN THÀNH] Thư viện lõi dùng chung
│   │   └── src/main/java/.../common/
│   │       ├── config/            #   ServerConfig
│   │       ├── crypto/            #   AesGcmCipher, HKDF, PBKDF2, KeyStoreManager,
│   │       │                      #   NonceCache, ReplayDefenseService
│   │       ├── db/                #   DatabaseManager (SQLite)
│   │       ├── exception/         #   SecureChatException hierarchy (16 classes)
│   │       └── protocol/          #   PacketFrame, ControlVector, MessageType,
│   │                              #   JsonSerializer, DTOs (TgtRequest, StInner, ...)
│   ├── ca-server/                 # Máy chủ PKI — Owner: Chị Bee
│   ├── kdc-server/                # Máy chủ Kerberos (AS+TGS) — Owner: Gia Hiển
│   ├── chat-server/               # Máy chủ chat — Owner: Phú Thọ
│   └── client-app/                # Ứng dụng Desktop — Owner: Trúc Ngọc
```

---

## 3. Cách biên dịch (Build) Hệ thống

Trước khi chạy hoặc code tiếp, bạn cần biên dịch thư viện dùng chung `shared-lib` để các module khác có thể nhận diện được nó.

1. Mở Terminal và di chuyển vào thư mục `src`:
   ```bash
   cd d:\MHUD\Project\src
   ```
2. Chạy lệnh build:
   ```bash
   mvn clean install
   ```
   Lệnh này sẽ dọn dẹp thư mục build cũ, biên dịch mã nguồn, chạy Unit Tests và cài đặt thư viện `shared-lib` vào kho lưu trữ nội bộ (Local Maven Repository).

---

## 4. Cách chạy từng Module

Sau khi `mvn install` thành công, bạn có thể chạy từng server riêng lẻ bằng lệnh `mvn exec:java`:

```bash
# CA Server (port 8443)
mvn exec:java -pl ca-server

# KDC Server — AS (port 8881) + TGS (port 8882)
mvn exec:java -pl kdc-server

# Chat Server (port 8883)
mvn exec:java -pl chat-server

# Client Application (Swing GUI)
mvn exec:java -pl client-app
```

> ⚠️ **Thứ tự khởi động khuyến nghị:** CA Server → KDC Server → Chat Server → Client App

---

## 5. Quyết định Nền tảng

**Phương án A — Windows-only (ĐÃ CHỌN):**
- Toàn bộ develop và demo trên **Windows 10/11**
- Private Key được bảo vệ bởi **Windows DPAPI** thông qua SunMSCAPI
- **Không có fallback** cho macOS/Linux
- Xem `KeyStoreManager.java` trong `shared-lib` để biết chi tiết

---

## 6. Hướng dẫn Dành cho các Thành viên

Tất cả các thành viên BẮT BUỘC phải đọc kỹ hai file trong thư mục `doc/` trước khi code:
1. `BaoCao_SecureChat.md`: Hiểu về luồng thuật toán mật mã học và giao thức.
2. `Contrains.md`: Quy tắc đặt tên branch, commit, kiến trúc gói tin JSON và danh sách các Exception.

**Phân công Công việc:**
- **Nhánh gốc**: `main` (chứa toàn bộ `shared-lib` chuẩn làm nền tảng).
- Mọi người tạo nhánh (Branch) mới để làm việc dựa trên quy tắc trong `Contrains.md` (ví dụ: `feature/ca-server`).
- Module `shared-lib` đã bao gồm toàn bộ chức năng mã hóa AES-GCM, PBKDF2, HKDF, các lớp Exception và giao thức `PacketFrame`. Vui lòng `import` từ module này và **tuyệt đối không code lại** các logic mã hóa ở module của bạn.
- Bất kỳ thay đổi nào liên quan đến Interface, Exception, Config trong `shared-lib` đều phải có sự thống nhất chung qua group chat.

### Các class quan trọng trong shared-lib (đã sẵn sàng dùng):

| Class | Package | Mô tả |
|-------|---------|-------|
| `AesGcmCipher` | `crypto` | Mã hóa/giải mã AES-256-GCM |
| `HkdfKeyDerivation` | `crypto` | Dẫn xuất Master Session Key từ ECDHE + Kyber |
| `Pbkdf2KeyDerivation` | `crypto` | Dẫn xuất key mã hóa database |
| `ReplayDefenseService` | `crypto` | Kiểm tra timestamp skew + nonce uniqueness |
| `NonceCache` | `crypto` | Cache nonce chống replay (thread-safe, auto-cleanup) |
| `KeyStoreManager` | `crypto` | Load Windows KeyStore (SunMSCAPI) |
| `DatabaseManager` | `db` | Quản lý kết nối SQLite |
| `PacketFrame` | `protocol` | Đọc/ghi TCP frame (Length-prefix 8-byte header) |
| `JsonSerializer` | `protocol` | Serialize/deserialize JSON ↔ byte[] |
| `MessageType` | `protocol` | Enum cho packet type codes |
| `ControlVector` | `protocol` | Validate Control Vector flags |
| DTOs | `protocol.dto` | TgtRequest, TgtResponse, TgtInner, StInner, AuthenticatorJson, ChatMessage, ErrorResponse |

### Cần lưu ý đặc biệt:
- Không dùng `String` để lưu mật khẩu, bắt buộc dùng `char[]`. Mảng `byte[]` hoặc mảng `char[]` dùng xong phải được xóa trắng (`Arrays.fill()`) trong khối lệnh `finally`.
- Giao tiếp Socket TCP bắt buộc sử dụng lớp `PacketFrame` để đọc/ghi với format `Length-Prefix`.
- Serialize JSON payload bằng `JsonSerializer.toBytes()` / `JsonSerializer.fromBytes()`.
- Xem các class trong thư mục `src/shared-lib/src/main/java/vn/edu/hcmus/securechat/common/` để nắm rõ các hàm đang có sẵn hỗ trợ.

