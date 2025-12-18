# Hướng dẫn Tích hợp & Chạy Payment Flow (PayOS)

Tài liệu này hướng dẫn cách cấu hình, chạy môi trường local (sử dụng **Ngrok**) và kiểm thử luồng thanh toán vé xe qua cổng **PayOS**.

---

## 1. Thiết lập tài khoản PayOS

1. Truy cập [https://payos.vn/](https://payos.vn/) và đăng nhập/đăng ký tài khoản.
2. Vào **Dashboard (Quản trị)** → **Kênh thanh toán**.
3. Tạo một kênh thanh toán mới (nếu chưa có).
4. Tại mục **Thông tin cấu hình**, lấy 3 thông số quan trọng:

   * **Client ID**
   * **API Key**
   * **Checksum Key**

---

## 2. Cấu hình Backend (Config Server)

Cập nhật 3 thông số trên vào file cấu hình của `booking-service` (trên Config Server hoặc `application.properties` local).

```properties
payos.client-id=YOUR_CLIENT_ID
payos.api-key=YOUR_API_KEY
payos.checksum-key=YOUR_CHECKSUM_KEY
```

> **Lưu ý**: `Checksum Key` dùng để tạo và xác thực chữ ký (Signature). Nếu sai key này, việc tạo link thanh toán hoặc nhận Webhook sẽ **thất bại**.

---

## 3. Khởi chạy Môi trường Local

Vì PayOS cần gọi ngược lại Backend (Webhook) khi thanh toán thành công, cần public localhost ra internet bằng **Ngrok**.

### 3.1. Chạy Backend

Khởi động service **BookingService** (mặc định port `8080`).

### 3.2. Chạy Ngrok

Mở terminal và chạy:

```bash
ngrok http 8080
```

Ngrok sẽ cấp một URL public, ví dụ:

```
https://abcd-1234.ngrok-free.app
```

### 3.3. Cấu hình Webhook trên PayOS

1. Copy domain Ngrok vừa tạo.
2. Ghép thành URL Webhook theo format:

```
{NGROK_URL}/booking/payments/payos-webhook
```

**Ví dụ:**

```
https://abcd-1234.ngrok-free.app/booking/payments/payos-webhook
```

> `/booking` là `context-path` (nếu có), `/payments/payos-webhook` là endpoint trong Backend.

3. Quay lại **PayOS Dashboard** → **Cấu hình Webhook**.
4. Dán URL trên vào và bấm **Lưu**.

⚠️ **Quan trọng**: Mỗi lần restart Ngrok, domain sẽ thay đổi → **BẮT BUỘC** cập nhật lại Webhook URL trên PayOS Dashboard.

---

## 4. Luồng thanh toán chi tiết

### 4.1. Client yêu cầu tạo Link thanh toán

Frontend (FE) gọi API tạo link thanh toán, kèm theo 2 URL để PayOS điều hướng người dùng sau khi thanh toán xong hoặc hủy.

**API**:

```
POST /booking/payments/create-link
```

**Request Body**:

```json
{
  "ticketId": "UUID_CUA_VE_VUA_DAT",
  "returnUrl": "http://localhost:3000/booking/success",
  "cancelUrl": "http://localhost:3000/booking/failed"
}
```

> `returnUrl` và `cancelUrl` là URL của **Frontend**.

---

### 4.2. Backend xử lý & Trả về Checkout URL

Backend gọi **PayOS SDK** để tạo link thanh toán.

**Response**:

```json
{
  "success": true,
  "checkoutUrl": "https://pay.payos.vn/web/xxxxxx",
  "qrCode": "...",
  "orderCode": 17123456789
}
```

---

### 4.3. Frontend điều hướng người dùng

Frontend nhận `checkoutUrl` và redirect trình duyệt:

```javascript
window.location.href = response.data.checkoutUrl;
```

---

### 4.4. Người dùng thanh toán & PayOS xử lý

* Người dùng quét QR hoặc nhập thẻ trên trang PayOS.
* **Nếu thanh toán thành công**:

  * PayOS redirect người dùng về `returnUrl` (trang Success của FE).
  * Đồng thời gửi **Webhook (POST)** về Backend:

```
/booking/payments/payos-webhook
```

* Backend xử lý:

  1. Verify Signature (Checksum Key)
  2. Update trạng thái Vé → `CONFIRMED`
  3. Update ghế → `BOOKED`
  4. Gửi Email xác nhận

---

## 5. Kiểm tra trạng thái thanh toán

Frontend có thể **chủ động kiểm tra trạng thái vé** (polling hoặc khi load trang Success) để tránh trường hợp Webhook bị trễ.

**API**:

```
GET /booking/payments/ticket/{ticketId}
```

**Response**:

```json
{
  "success": true,
  "data": {
    "paymentId": "UUID...",
    "orderCode": 17123456789,
    "amount": 200000,
    "status": "PAID",
    "paidAt": "2023-12-15T10:00:00"
  }
}
```

> Kiểm tra trường `status`: `PAID` / `PENDING`.

---

## 6. Troubleshooting (Gỡ lỗi)

| Lỗi thường gặp                | Nguyên nhân                     | Cách khắc phục                                                                          |
| ----------------------------- | ------------------------------- | --------------------------------------------------------------------------------------- |
| Webhook 404 Not Found         | URL Webhook sai path            | Kiểm tra endpoint `/booking/payments/payos-webhook` trong Controller                    |
| Webhook không bắn về          | Ngrok URL thay đổi              | Update lại Webhook URL trên PayOS Dashboard                                             |
| Signature mismatch            | Sai Checksum Key                | Kiểm tra lại `application.properties`, không có khoảng trắng, restart BE                |
| Payment not found (Order 123) | Bấm nút **Test** trên Dashboard | Nút Test gửi `orderCode` giả. Hãy tạo link thật và thanh toán thật (hoặc insert DB giả) |

---

✅ **Hoàn tất**: Sau khi hoàn thành các bước trên, bạn có thể tích hợp và test đầy đủ luồng thanh toán PayOS ở môi trường local.
