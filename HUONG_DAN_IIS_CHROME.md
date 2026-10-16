# TÀI LIỆU HƯỚNG DẪN ĐỒ ÁN LẬP TRÌNH MẠNG: HTTP SOCKET CLIENT & MÁY CHỦ IIS
**Môn học:** Lập trình mạng (Network Programming) - Đại học Sài Gòn (SGU)  
**Chủ đề:** HTTP/1.1 Protocol, Java Socket Client & IIS Web Server Hosting  

---

## 📌 1. NGUYÊN LÝ HOẠT ĐỘNG & BẢN CHẤT ĐỒ ÁN (TRẢ LỜI CHO THẦY)

### Câu hỏi cốt lõi của Giảng viên:
> *"Quy trình hoạt động từ lúc Client lấy URL ngoài internet về đến khi host lên IIS và hiển thị ra sao?"*

### Trình tự 3 bước chuẩn của đồ án:
1. **Bước 1: Get URL bằng Java Socket (Không dùng trình duyệt có sẵn)**
   - Người dùng nhập một địa chỉ website (ví dụ: `https://www.24h.com.vn` hoặc `https://google.com`).
   - `WebClient.java` sử dụng `java.net.Socket` (hoặc `javax.net.ssl.SSLSocket` đối với HTTPS) gửi HTTP Request thô (`GET / HTTP/1.1`).
   - Phân giải phản hồi HTTP/1.1: xử lý `Chunked Transfer-Encoding` ở tầng byte, loại bỏ mã hex độ dài chunk, giải mã UTF-8 và chèn thẻ `<base href="...">`.
2. **Bước 2: Xuất toàn bộ mã nguồn vào thư mục lưu trữ**
   - Mã nguồn Front-end hoàn chỉnh (HTML, CSS, JS, cấu hình IIS) được lưu vào thư mục:  
     📂 `c:\Users\Lenovo\Downloads\HTTP-app-main\HTTP-app-main\src_download\`
   - Đồng thời hệ thống tự động đồng bộ sang thư mục gốc của IIS:  
     📂 `C:\inetpub\site1\`
3. **Bước 3: IIS Host thư mục SRC và mở Port**
   - Dịch vụ Microsoft IIS (Internet Information Services) được cấu hình trỏ Physical Path vào `C:\inetpub\site1` (hoặc thư mục con trong `src_download\`).
   - IIS mở cổng **Port 8443 (HTTPS)** hoặc **Port 80 (HTTP)** để phục vụ trang web nội bộ.
   - Khi truy cập `https://localhost:8443`, IIS phục vụ file tĩnh và trình duyệt hiển thị giao diện mượt mà!

---

## 📂 2. THƯ MỤC MÃ NGUỒN TẬP TRUNG (SRC_DOWNLOAD)

Thư mục chứa toàn bộ mã nguồn website đã tải về nằm tại:  
👉 `c:\Users\Lenovo\Downloads\HTTP-app-main\HTTP-app-main\src_download\`

Bao gồm các website:
- **`src_download/src_24h/`**: Toàn bộ mã nguồn trang Tin tức Báo 24h.
- **`src_download/src_google/`**: Toàn bộ mã nguồn trang Google Search.
- **`index.html`**: File mã nguồn HTML hoàn chỉnh của trang web (đã loại bỏ sạch mã hex chunk, có thẻ `<base>` trỏ tài nguyên gốc).
- **`web.config`**: Tệp tin cấu hình chuẩn MIME types cho IIS để phục vụ font chữ, SVG, JSON.

---

## 🛠️ 3. HƯỚNG DẪN CẤU HÌNH THỦ CÔNG TRÊN IIS (ĐỂ THỰC HÀNH VÀ BÁO CÁO THẦY)

### Bước 1: Mở IIS Manager (Trình quản trị IIS)
1. Nhấn tổ hợp phím **`Windows + R`**.
2. Nhập lệnh: `inetmgr` rồi nhấn **Enter** (hoặc tìm kiếm "IIS Manager" trong Start Menu).

---

### Bước 2: Tạo chứng chỉ SSL tự ký (Nếu chạy cổng HTTPS 8443)
1. Trong cửa sổ IIS Manager, nhấp chuột vào **Tên máy tính** ở cột bên trái (*Server Home*).
2. Ở khung giữa, nhấp đúp chuột vào biểu tượng **Server Certificates**.
3. Ở cột bên phải (*Actions*), nhấp vào **Create Self-Signed Certificate...**
4. Đặt tên gợi nhớ: `IIS-Lab-SSL` rồi nhấn **OK**.

---

### Bước 3: Tạo Website mới và trỏ vào thư mục Web
1. Ở cột bên trái, mở rộng cây thư mục -> nhấp chuột phải vào mục **Sites** -> chọn **Add Website...**
2. Điền chính xác các thông số sau:
   - **Site name:** `Site1_8443`
   - **Physical path:** Bấm nút `...` và chọn thư mục:
     `C:\inetpub\site1`  
     *(hoặc chọn thư mục `c:\Users\Lenovo\Downloads\HTTP-app-main\HTTP-app-main\src_download\src_24h`)*
   - **Type:** Chọn `https` (hoặc `http` nếu muốn dùng HTTP thường).
   - **IP address:** Để mặc định `All Unassigned`.
   - **Port:** Nhập `8443` (nếu chọn `http` thì nhập `8080` hoặc `8888`).
   - **SSL certificate:** Chọn chứng chỉ vừa tạo ở bước trên: `IIS-Lab-SSL`.
3. Nhấn **OK** để hoàn tất việc tạo Website.

---

### Bước 4: Phân quyền truy cập thư mục cho IIS (Tránh lỗi 401 / 403 Forbidden)
1. Nhấp chuột phải vào Website `Site1_8443` vừa tạo -> chọn **Edit Permissions...**
2. Chuyển sang thẻ **Security** -> bấm nút **Edit...**
3. Bấm **Add...** -> gõ vào ô: `Users` (hoặc `IIS_IUSRS`) -> bấm **Check Names** -> bấm **OK**.
4. Tích chọn quyền: **Read & execute**, **List folder contents**, **Read**.
5. Bấm **Apply** -> **OK**.

---

### Bước 5: Mở Port trên Windows Firewall (Tường lửa)
Để cổng 8443 hoặc 8080 không bị tường lửa chặn khi thầy kiểm tra kết nối:
1. Mở PowerShell với quyền Administrator.
2. Chạy lệnh mở port sau:
```powershell
New-NetFirewallRule -DisplayName "IIS Lab Port 8443" -Direction Inbound -LocalPort 8443 -Protocol TCP -Action Allow
```

---

### Bước 6: Khởi động Site và Kiểm tra
1. Ở cột bên phải của IIS Manager (*Manage Website*), bấm **Start** (hoặc **Restart**).
2. Mở ứng dụng **SGU Web Browser**:
   - Bấm nút **`🌐 Mở IIS (8443)`** trên thanh công cụ.
   - Hoặc gõ địa chỉ: `https://localhost:8443` rồi bấm **Đi**.
3. Trang web trong thư mục `web_full_src` sẽ lập tức được kết xuất hiển thị hoàn hảo!

---

## ⚡ 4. DEMO 2 CHẾ ĐỘ: TỰ ĐỘNG & THỦ CÔNG (SHOW CHO THẦY XEM)

### 🔹 Chế độ 1: Tải Tự Động (Auto Crawler via Pure Socket)
Khi thầy muốn xem cơ chế cào mã nguồn qua giao thức mạng:
1. Nhập URL trang web bất kỳ vào thanh địa chỉ (ví dụ: `https://www.24h.com.vn`).
2. Bấm nút **`[⚡ Tải Tự Động]`**.
3. Xuất hiện hộp thoại hỏi xác nhận ➔ Bấm **Có / Đồng ý**.
4. Chương trình gửi Java Socket, bóc tách header HTTP/1.1, gỡ mã chunk hex, gắn `<base href="...">`, lưu vào `src_download/src_<name>/`, tự động đẩy sang IIS và chuyển trang hiển thị ngay.

### 🔹 Chế độ 2: Thêm Thủ Công (Manual Mode - Show làm bằng tay)
Khi thầy yêu cầu thao tác thủ công hoặc muốn host một trang web do chính tay bạn viết:
1. Bấm nút **`[✍ Thêm Thủ Công]`** trên thanh công cụ (hoặc nút `+ Thêm Website Thủ Công` tại Trang chủ `about:home`).
2. Chọn 1 trong 3 tính năng:
   * **Tự tạo trang mới (HTML):** Nhập mã ID (ví dụ: `bai_tap_1`) + Tiêu đề + Nội dung HTML tùy ý. Bấm **Lưu & Đưa lên IIS** ➔ Ứng dụng tự tạo `index.html` và IIS phục vụ ngay!
   * **Import thư mục Web có sẵn:** Chọn thư mục mã nguồn HTML/CSS/JS có sẵn trên máy tính để nạp vào hệ thống.
   * **Mở thư mục trên Windows Explorer:** Bấm nút mở `src_download` hoặc `C:\inetpub\site1` để copy/paste file bằng tay trực tiếp trước mặt thầy.

### 🔹 Tính năng Quản Lý & Xóa Website (Delete Site)
Khi muốn dọn dẹp các trang web đã thêm:
* **Cách 1 (Nhanh nhất):** Trên trang chủ `about:home`, rê chuột vào thẻ website bất kỳ, nhấp vào nút **`×` màu đỏ** ở góc trên bên phải ➔ Xác nhận xóa. Website sẽ bị xóa hoàn toàn khỏi đĩa và IIS.
* **Cách 2:** Bấm menu **`≡`** trên thanh công cụ ➔ Chọn **Quản lý & Xóa Website** ➔ Chọn website muốn xóa.


---

## 🎯 5. BỘ CÂU HỎI & CÂU TRẢ LỜI NHANH KHI BẢO VỆ ĐỒ ÁN

| Câu hỏi của Thầy | Câu trả lời chuẩn kỹ thuật |
|---|---|
| **Tại sao không dùng luôn WebView để lướt web mà phải dùng Socket GET URL?** | "Dạ thưa Thầy, yêu cầu của môn Lập trình mạng là lập trình Socket tầng Transport/Application thuần. Em dùng `Socket`/`SSLSocket` gửi request HTTP/1.1 thô, đọc byte stream, xử lý Chunked Transfer-Encoding và bóc tách dữ liệu để hiểu sâu giao thức mạng, sau đó nạp source vào IIS để mô phỏng mô hình Web Server chuyên nghiệp ạ." |
| **Tại sao lại có thẻ `<base href="...">` trong file HTML?** | "Dạ, vì khi host mã nguồn trên localhost của IIS, các đường dẫn CSS và ảnh tương đối (`/images/logo.png`) sẽ trỏ về localhost và bị 404. Em inject thẻ `<base href='url_gốc'>` để trình duyệt tự động resolve tài nguyên tĩnh về đúng server gốc, giúp giao diện hiển thị trọn vẹn ạ." |
| **Mở port trên IIS khác gì so với Java ServerSocket?** | "Dạ, `ServerSocket` trong Java phải tự lắng nghe và quản lý ThreadPool thủ công. Còn IIS là web server công nghiệp của Microsoft trên nhân hệ điều hành (`http.sys`), hỗ trợ quản lý worker process (`w3wp.exe`), gán chứng chỉ SSL trực tiếp qua Schannel và quản lý cổng mạng tối ưu hơn ạ." |
