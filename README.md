# SecureChat E2EE — Phiên bản 2.0

<!-- Các Badges minh họa -->
[![Java Version](https://img.shields.io/badge/Java-25%20LTS-orange.svg)](https://openjdk.org/)
[![Build Tool](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![OS Support](https://img.shields.io/badge/Platform-Windows%20Only%20(SunMSCAPI)-teal.svg)](#quyết-định-nền-tảng-windows-only)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)

Hệ thống Nhắn tin Bảo mật Đầu cuối (E2EE) được xây dựng trên mô hình Zero-Trust dựa trên Kiến trúc Kerberos V5 (Ticketing), Hạ tầng Khóa Công khai (PKI) và Mật mã Kháng Lượng tử (Kyber ML-KEM / Dilithium ML-DSA).

---

## Mục lục
1. [Giới thiệu & Tính năng](#giới-thiệu--tính-năng)
2. [Công nghệ sử dụng](#công-nghệ-sử-dụng)
3. [Hướng dẫn khởi chạy cục bộ](#hướng-dẫn-khởi-chạy-cục-bộ)
4. [Cấu trúc thư mục](#cấu-trúc-thư-mục)
5. [Quy trình đóng góp (Git Flow)](#quy-trình-đóng-góp-git-flow)
6. [Quyết định nền tảng (Windows-only)](#quyết-định-nền-tảng-windows-only)
7. [Tác giả](#tác-giả)
8. [Giấy phép](#giấy-phép)

---

## Giới thiệu & Tính năng

Hệ thống nhắn tin chạy trên mạng WAN đối mặt với nhiều rủi ro (nghe lén, phát lại, giả mạo server/user, rò rỉ cơ sở dữ liệu). **SecureChat E2EE 2.0** giải quyết các bài toán này bằng cách phân tách rõ hai lớp bảo mật:
*   **Lớp truy cập hạ tầng**: Kerberos-like ticketing (TGT/ST), PKI, OCSP, Replay Cache, Audit Log và Rate Limiting kiểm soát truy cập và xác thực dịch vụ.
*   **Lớp nội dung E2EE**: Hai client tự thỏa thuận khóa nội dung thông qua ECDHE kết hợp Kyber (kháng lượng tử), sau đó chạy Double Ratchet xoay khóa cho từng tin nhắn. Chat Server hoàn toàn bị mù trước nội dung tin nhắn của client.

### Các tính năng cốt lõi:
*   **Mã hóa đầu cuối (E2EE)**: Mã hóa AES-256-GCM bảo vệ nội dung, kết hợp nhãn dữ liệu bổ sung (AAD) để chống tráo đổi metadata.
*   **Kháng lượng tử (PQC Readiness)**: Tích hợp ML-KEM-768 (Kyber-768) cho thỏa thuận khóa và thư viện dịch vụ ML-DSA-65 (Dilithium-3) cho chữ ký lai trong tương lai.
*   **Cấp vé kiểm soát dịch vụ (Kerberos-like)**: Hệ thống cấp Ticket-Granting Ticket (TGT) và Service Ticket (ST) với nhãn **Control Vector** để phân quyền chặt chẽ.
*   **Chống tấn công phát lại (Anti-Replay)**: Đồng bộ hóa NTP, giới hạn cửa sổ thời gian 300s và bộ đệm Nonce Cache (thread-safe, auto-cleanup).
*   **Lưu trữ cục bộ bảo mật**: Cơ sở dữ liệu SQLite và Ticket Cache được mã hóa bằng khóa dẫn xuất từ mật khẩu thông qua hàm băm memory-hard **Argon2id**.
*   **Nhật ký an toàn (Secure Audit Log)**: Ghi nhật ký hệ thống độc lập sử dụng liên kết Hash-Chain kèm HMAC để phát hiện các hành vi sửa/xóa log.
*   **Giao diện Desktop mượt mà**: Ứng dụng client viết bằng Java Swing, toàn bộ tác vụ mạng/mã hóa nặng được thực hiện bất đồng bộ qua `SwingWorker` để không gây đơ giao diện (EDT).

---

## Công nghệ sử dụng

Hệ thống được tổ chức dưới dạng Multi-Module Maven với các công nghệ chính:

*   **Ngôn ngữ & Thư viện Core**: Java 25 LTS, BouncyCastle 1.78.1 (Mật mã học chính), SunMSCAPI (Windows KeyStore).
*   **Database**: SQLite JDBC (Dữ liệu cục bộ của Client).
*   **Serialization**: Jackson Databind 2.17.1 (Payload JSON UTF-8).
*   **Logging**: SLF4J + Logback (Application log & Security audit log).
*   **Desktop UI**: Java Swing + SwingWorker.
*   **Testing**: JUnit 5, Mockito.

---

## Hướng dẫn khởi chạy cục bộ

### 1. Yêu cầu hệ thống tiên quyết
Đảm bảo máy tính của bạn đã cài đặt sẵn các công cụ:
*   **Java Development Kit (JDK)**: Phiên bản **Java 25 LTS** (OpenJDK hoặc Temurin).
*   **Build Tool**: **Apache Maven 3.9+**.
*   **Hệ điều hành**: **Windows 10/11** (Do bắt buộc sử dụng Windows DPAPI / SunMSCAPI để bảo vệ Private Key).

#### Hướng dẫn cấu hình biến môi trường trên Windows:
Nếu gõ lệnh `mvn -v` trong Terminal báo lỗi, hãy thiết lập biến hệ thống (System variables):
1.  **Cấu hình `JAVA_HOME`**: Tạo mới biến `JAVA_HOME` với đường dẫn tới thư mục JDK (ví dụ: `C:\Program Files\Java\jdk-25`).
2.  **Cài đặt Maven**: Tải Apache Maven `.zip` từ trang chủ, giải nén vào ổ C (ví dụ: `C:\Program Files\apache-maven-3.9.6`).
3.  **Cấu hình `MAVEN_HOME`**: Tạo mới biến `MAVEN_HOME` trỏ tới thư mục giải nén Maven.
4.  **Cấu hình `Path`**: Chọn biến `Path` và bấm Edit, thêm 2 dòng mới:
    *   `%JAVA_HOME%\bin`
    *   `%MAVEN_HOME%\bin`
5.  Khởi động lại hoàn toàn terminal và kiểm tra bằng lệnh: `mvn -v` và `java -version`.

### 2. Biên dịch (Build) Hệ thống
Trước khi khởi chạy các Server hoặc Client, bạn bắt buộc phải biên dịch thư viện lõi `shared-lib` để đưa vào Local Maven Repository.
Di chuyển vào thư mục `src` và chạy lệnh:
```bash
cd d:\MHUD\PROJECT\src
mvn clean install
```
Lệnh này sẽ biên dịch mã nguồn, chạy toàn bộ bộ kiểm thử tự động (Unit Tests) và cài đặt `shared-lib` cho các module khác liên kết.

### 3. Khởi chạy các Module
Sau khi biên dịch thành công, bạn có thể khởi chạy từng dịch vụ riêng biệt. Khuyên dùng chạy theo đúng thứ tự sau:

```bash
# 1. Khởi chạy CA Server (mặc định cổng 8443)
mvn exec:java -pl ca-server

# 2. Khởi chạy KDC Server (AS cổng 8881 + TGS cổng 8882)
mvn exec:java -pl kdc-server

# 3. Khởi chạy Chat Server (cổng 8883)
mvn exec:java -pl chat-server

# 4. Khởi chạy Client Application (Swing GUI Desktop)
mvn exec:java -pl client-app
```

---

## Cấu trúc thư mục

Kiến trúc cây thư mục của dự án SecureChat:
```text
├── doc/                           # Tài liệu thiết kế & phân tích hệ thống
│   ├── BaoCao_SecureChat.md       #   Báo cáo luồng thuật toán & giao thức mật mã
│   └── Contrains.md               #   Quy chuẩn lập trình & ràng buộc kỹ thuật bắt buộc
├── src/                           # Thư mục chứa mã nguồn chính
│   ├── pom.xml                    #   Parent POM quản lý dependencies chung
│   ├── shared-lib/                #   [Module] Thư viện dùng chung của hệ thống
│   │   └── src/main/java/.../common/
│   │       ├── config/            #     Cấu hình ServerConfig tập trung
│   │       ├── crypto/            #     Các dịch vụ mã hóa lõi (AesGcm, Kyber, DoubleRatchet...)
│   │       ├── db/                #     DatabaseManager cục bộ (SQLite)
│   │       ├── exception/         #     Phân cấp ngoại lệ bảo mật SecureChatException
│   │       └── protocol/          #     Length-Prefix PacketFrame, JsonSerializer & DTOs
│   ├── ca-server/                 #   [Module] Máy chủ PKI và OCSP Responder
│   ├── kdc-server/                #   [Module] Máy chủ Kerberos KDC (AS + TGS)
│   ├── chat-server/               #   [Module] Máy chủ trung chuyển tin nhắn & xác thực ST
│   └── client-app/                #   [Module] Ứng dụng Desktop Swing Client
├── .gitignore                     # Cấu hình bỏ qua tệp build, logs, sqlite db, local keystore
└── README.md                      # Hướng dẫn này
```

---

## Quy trình đóng góp (Git Flow)

Để đảm bảo mã nguồn hoạt động nhất quán, tất cả 4 thành viên trong nhóm bắt buộc phải tuân thủ quy chuẩn sau:

### 1. Phân chia Module & Ownership
*   **CA Server**: Owner: **Anh Tuấn** (Branch: `feature/ca-server`)
*   **KDC Server (AS/TGS)**: Owner: **Gia Hiển** (Branch: `feature/kdc-server`)
*   **Chat Server**: Owner: **Phú Thọ** (Branch: `feature/chat-server`)
*   **Client App**: Owner: **Trúc Ngọc** (Branch: `feature/client-app`)
*   **Shared Library**: Owner: **Gia Hiển** (Branch: `feature/shared-lib`)

### 2. Quy tắc Đặt tên Nhánh & Commit
*   Tạo nhánh mới từ `main` theo định dạng: `feature/<module>-<tên-chức-năng>` hoặc `fix/<module>-<tên-lỗi>`.
*   Ví dụ: `feature/kdc-tgt-issuance`, `fix/ca-ocsp-stapling`.
*   Commit message tuân thủ *Conventional Commits*:
    *   `feat(<scope>): <mô tả ngắn>` (VD: `feat(kdc): add TGT issuance with CV`)
    *   `fix(<scope>): <mô tả ngắn>`
    *   `test(<scope>): <mô tả ngắn>`
    *   `docs(<scope>): <mô tả ngắn>`

### 3. Nguyên tắc An toàn Lập trình (Secure Coding)
*   **Không dùng String lưu mật khẩu**: Bắt buộc dùng mảng `char[]` và xóa trắng mảng bằng `Arrays.fill(..., '\0')` trong khối `finally` ngay sau khi sử dụng.
*   **Không log thông tin nhạy cảm**: Tuyệt đối không in khóa bí mật, khóa phiên, mật khẩu của người dùng ra log file hay terminal.
*   **Length-Prefix TCP**: Giao tiếp socket bắt buộc qua lớp `PacketFrame` đóng khung theo định dạng `Length-Prefix`.
*   **Gia hạn vé (Renewable)**: TGT và ST cấp ra phải chứa cờ `"renewable": true` để client có thể xin gia hạn.

---

## Quyết định nền tảng (Windows-only)

Dự án sử dụng cơ chế lưu trữ và bảo vệ khóa riêng tư (Private Key) nghiêm ngặt nhất:
*   Mặc định chạy trên hệ điều hành **Windows 10/11**.
*   Khóa riêng tư của Client được lưu trữ và bảo vệ trực tiếp bởi **Windows DPAPI** thông qua nhà cung cấp **SunMSCAPI** (`Windows-MY` KeyStore).
*   Không xuất hiện khóa riêng tư dưới dạng Plaintext trên ổ đĩa hay bộ nhớ RAM của Application Layer.
*   *Lưu ý*: Dự án không hỗ trợ cơ chế fallback chạy trên Linux hay macOS trong phiên bản phát hành thương mại này.

---

## Tác giả

Hệ thống được phát triển bởi các thành viên nhóm **Mã hóa ứng dụng (CSC15003 - GVHD: ThS. Mai Anh Tuấn)**:

*   **Gia Hiển** - *Nhóm trưởng / Core Backend & Shared Crypto Developer*
*   **Anh Tuấn** - *CA & PKI Developer*
*   **Phú Thọ** - *Chat Server & Message Router Developer*
*   **Trúc Ngọc** - *Swing Desktop UI & Client Integration Developer*

---

## Giấy phép

Dự án này được phân phối công khai cho mục đích học tập và nghiên cứu học thuật dưới Giấy phép **MIT License**. Chi tiết xem thêm tại tệp `LICENSE` trong thư mục gốc của dự án.
