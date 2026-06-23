# Mã hóa ứng dụng

### Các kỹ thuật mật mã nền tảng
* **Hàm băm (Hash):** Một chiều, không dùng để giải mã ngược. Ứng dụng: kiểm tra toàn vẹn, lưu mật khẩu, tạo digest cho chữ ký số.
* **Mã hóa đối xứng:** Cùng một khóa cho encrypt/decrypt. Nhanh, phù hợp mã hóa dữ liệu khối lượng lớn. Ứng dụng: bảo vệ file, message, session data.
* **Mã hóa bất đối xứng:** Dùng cặp public key / private key. Chậm hơn đối xứng. Ứng dụng: phân phối khóa, chữ ký số, certificate, xác thực.

---

### Các nhu cầu bảo mật khi áp dụng mã hóa vào ứng dụng
* **Confidentiality:** Bảo vệ bí mật dữ liệu.
* **Integrity:** Phát hiện sửa đổi dữ liệu.
* **Authentication:** Xác thực thực thể/giao tiếp.
* **Identification + Verification:** Khai báo danh tính và chứng minh danh tính.
* **Key distribution / key management:** Phân phối, lưu trữ, thay thế, thu hồi khóa.
* **Access control / authorization:** Sau khi xác thực, ai được làm gì.
* **Replay protection:** Chống phát lại gói tin.
* **Trust establishment:** Thiết lập niềm tin bằng certificate / CA/PKI.

---

### Mức cơ bản: triển khai chiến lược phân phối khóa
* Mã hóa dữ liệu bằng symmetric encryption.
* Dùng hybrid encryption để phân phối khóa phiên hoặc KDC ở mức cơ bản.
* Có key lifecycle: sinh khóa, phân phối, thời hạn, thay khóa.
* Có xác thực người dùng: identification + verification.
* Có chống replay bằng nonce/timestamp/challenge-response.
* Có xác thực nguồn khóa công khai tối thiểu qua trusted public key / certificate đơn giản.

---

### Mức tốt: kiến trúc an toàn thực tế hơn
* Tách rõ master key và session key.
* Có KDC/KMS hoặc dịch vụ quản lý khóa tập trung.
* Có mutual authentication client-server.
* Có phân quyền truy cập dựa trên identity đã xác thực.
* Dùng X.509 certificate.
* Có revocation CRL hoặc cơ chế tương đương.
* Có cơ chế bảo vệ khỏi MITM khi trao đổi khóa công khai.

---

### Mức nâng cao: hạ tầng tin cậy và SSO
* Có PKI tương đối đầy đủ: CA, RA, repository, quy trình đăng ký / cấp / thu hồi certificate.
* Có certificate chain validation.
* Có Kerberos-like ticketing hoặc SSO cho nhiều dịch vụ nội bộ.
* Có audit log cho các sự kiện: cấp khóa, cấp cert, đăng nhập / xác thực / truy cập tài nguyên.

---

### Yêu cầu Giao diện & Trải nghiệm Người dùng (UI/UX Glassmorphism)
* **Phong cách Kính mờ (Frosted Glassmorphism):** Các thành phần giao diện chính như thanh menu bên (sidebar), thẻ thông tin (cards), và các nút bấm (buttons) phải được thiết kế bán trong suốt với hiệu ứng kính mờ (sử dụng màu nền có kênh alpha như `rgba(...)`).
* **Đường viền gương khúc xạ (Reflective Mirror Borders):** Các góc cạnh của các thành phần kính mờ được bo viền siêu mỏng và sáng mờ (`rgba(255, 255, 255, 0.08)`) để mô phỏng khúc xạ ánh sáng trên mặt kính.
* **Chuyển động & Phản chiếu dạng gương (Mirror Glow & Shine Transitions):**
  * Khi hover chuột vào các nút nhấn hoặc các ô nhập liệu, một dải phản sáng mờ hoặc sự chuyển đổi độ sáng mượt mà sẽ diễn ra để mô tả ánh sáng phản chiếu trên kính.
  * Các thành phần tương tác hoạt động (active/focus) như ô nhập liệu sẽ có hiệu ứng viền phát sáng (glowing border) màu xanh ngọc (`#00A19C`) lan tỏa nhẹ.
  * Đèn LED báo trạng thái trực tuyến của người dùng phải có chu kỳ nhấp nháy phát sáng mờ (glowing pulse animation) từ độ mờ 50% đến 100% để tạo cảm giác giao diện "sống động".