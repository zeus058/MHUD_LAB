# Bảng Màu & Ràng Buộc Giao Diện (Frontend UI Constraints)

Tài liệu này định nghĩa hệ thống bảng màu (Color Palette) bắt buộc sử dụng cho giao diện của dự án **SecureChat E2EE** (đặc biệt là đối với module `client-app`). 

Thiết kế dựa trên bộ chủ đề **"SECURE TEAL & SILVER"** (Dành cho Ứng dụng Nhắn tin Bảo mật Zero-Trust) nhằm tạo ra cảm giác chuyên nghiệp, an toàn, hỗ trợ chế độ tối (Dark Mode) và tuân thủ các nguyên tắc tương phản văn bản.

---

## 🎨 Bảng Màu Chi Tiết

Tất cả các thành phần UI (nút bấm, nền, chữ, thông báo) **PHẢI** tuân thủ chính xác các mã HEX dưới đây. Không sử dụng các màu mặc định của framework UI (ví dụ: màu xanh mặc định của Swing).

### 1. Màu Nhấn Chủ Đạo (Primary Accent)
*   **Màu sắc:** SECURE TEAL (Xanh ngọc Bảo mật)
*   **Mã HEX:** `#00A19C`
*   **Cách dùng:** Dùng cho Nút Bấm chính (Call-to-Action), Trạng thái An toàn (Connected, Secure), viền input khi được chọn (focus), và màu nền của Bong bóng tin nhắn gửi đi.

### 2. Màu Nền (Background)
*   **Màu sắc:** DEEP CARBON (Đen Carbon Sâu)
*   **Mã HEX:** `#101820`
*   **Cách dùng:** Màu nền chính của toàn bộ giao diện ứng dụng. Đảm bảo độ tương phản tốt với các thành phần nổi.

### 3. Màu Bề Mặt Nổi (Surface / Cards)
*   **Màu sắc:** DARK SILVER (Bạc Tối Kỹ thuật)
*   **Mã HEX:** `#3A4750`
*   **Cách dùng:** Dùng cho các Panel chứa Danh sách (ví dụ: danh sách user online), Khu vực Cấu hình, các ô thẻ nổi (Cards) hoặc nền của bong bóng tin nhắn nhận được.

### 4. Màu Cảnh Báo (Alert / Warning)
*   **Màu sắc:** SIGNAL RED (Đỏ Tín hiệu Cảnh báo)
*   **Mã HEX:** `#FF4C4C`
*   **Cách dùng:** Lỗi Bảo mật (ví dụ: sai chứng chỉ, phát hiện Replay Attack), cảnh báo mất kết nối mạng, hoặc các nút hành động mang tính phá hủy/nguy hiểm.

### 5. Màu Chữ (Typography & Text)
Màu chữ được thiết kế để vượt qua tiêu chuẩn tương phản AA/AAA trên nền tối.
*   **Màu sắc 1:** TRẮNG (White)
    *   **Mã HEX:** `#FFFFFF`
    *   **Cách dùng:** Dành cho Tiêu đề Chính (Headers), hoặc chữ nằm trên nền có màu nhấn (ví dụ: chữ trắng trên nút Secure Teal hoặc nút Signal Red).
*   **Màu sắc 2:** BẠC SÁNG (Light Silver)
    *   **Mã HEX:** `#E2E8F0`
    *   **Cách dùng:** Nội dung tin nhắn thông thường, nội dung đoạn văn, và chữ hiển thị trong cửa sổ nhật ký (logs).

### 6. Màu Kính mờ & Viền gương (Glassmorphism & Mirror Effects)
*   **Màu sắc 1:** GLASS_CARD (Nền kính mờ)
    *   **Mã RGBA:** `rgba(36, 47, 56, 0.35)`
    *   **Cách dùng:** Dùng cho các thẻ, panel biểu mẫu đăng nhập/đăng ký với độ trong suốt 35%.
*   **Màu sắc 2:** GLASS_BORDER (Viền gương phản chiếu)
    *   **Mã RGBA:** `rgba(255, 255, 255, 0.08)`
    *   **Cách dùng:** Viền siêu mỏng bao quanh các card, nút bấm, sidebar để tạo hiệu ứng phản chiếu ánh sáng ở mép kính.
*   **Màu sắc 3:** GLASS_SIDEBAR (Thanh biên trong suốt)
    *   **Mã RGBA:** `rgba(12, 20, 28, 0.45)`
    *   **Cách dùng:** Nền thanh menu biên trái bán trong suốt.

---

## 🛠 Hướng dẫn Triển khai Code (Java Swing)

Đối với người phụ trách `client-app` (Trúc Ngọc), vui lòng tạo một class chứa các hằng số màu này (ví dụ `UIConstants.java`) để tái sử dụng xuyên suốt ứng dụng, thay vì hardcode trực tiếp mã HEX nhiều lần. Khi vẽ các panel bán trong suốt dạng kính, hãy đảm bảo đặt `setOpaque(false)` cho panel và override `paintComponent(Graphics g)` để vẽ hình chữ nhật bo tròn với màu bán trong suốt (RGBA).

**Ví dụ cấu trúc code Java:**

```java
import java.awt.Color;

public class UIConstants {
    // Colors based on Secure Teal & Silver Palette
    public static final Color SECURE_TEAL = Color.decode("#00A19C");
    public static final Color DEEP_CARBON = Color.decode("#101820");
    public static final Color DARK_SILVER = Color.decode("#3A4750");
    public static final Color SIGNAL_RED  = Color.decode("#FF4C4C");
    
    // Typography Colors
    public static final Color TEXT_WHITE  = Color.decode("#FFFFFF");
    public static final Color TEXT_SILVER = Color.decode("#E2E8F0");

    // Glassmorphism (RGBA Colors)
    public static final Color GLASS_CARD    = new Color(36, 47, 56, 90);    // ~35% alpha
    public static final Color GLASS_BORDER  = new Color(255, 255, 255, 20);  // ~8% alpha
    public static final Color GLASS_SIDEBAR = new Color(12, 20, 28, 115);   // ~45% alpha
}
```

*Tuân thủ đúng bảng màu này sẽ giúp ứng dụng đạt được tiêu chuẩn "Wow" về thẩm mỹ và cảm giác an toàn mà hệ thống SecureChat hướng đến.*
