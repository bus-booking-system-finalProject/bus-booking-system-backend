package com.booking.bookingService.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.booking.bookingService.model.Ticket;

@Service
@RequiredArgsConstructor
public class EmailService {

    private String formatCurrency(BigDecimal amount) {
        if (amount == null)
            return "0";
        return NumberFormat.getNumberInstance(Locale.of("vi", "VN")).format(amount);
    }

    // Helper: Format DateTime (e.g. 20:30 18/12/2025)
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null)
            return "";
        return dateTime.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
    }

    private final JavaMailSender emailSender;

    public void sendTicketEmail(String to, String subject, String body) {
        try {
            MimeMessage message = emailSender.createMimeMessage();
            // true = multipart (for attachments)
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true); // true = html content

            emailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public String generateTicketConfirmationHtml(Ticket ticket) {
        // --- 1. EXTRACT DATA FROM TICKET ENTITY ---
        String contactName = ticket.getContactName();
        String contactEmail = ticket.getContactEmail();
        String contactPhone = ticket.getContactPhone();
        String ticketCode = ticket.getTicketCode();
        String totalAmount = formatCurrency(ticket.getTotalAmount());
        
        // Handle Seats (List<String>)
        String seatNumbers = (ticket.getSeats() != null) ? String.join(", ", ticket.getSeats()) : "N/A";
        int quantity = (ticket.getSeats() != null) ? ticket.getSeats().size() : 0;

        // --- 2. EXTRACT DATA FROM RELATIONS (TRIP / ROUTE) ---
        // You might need to adjust these getters based on your Trip entity
        String operatorName = "Vexesieure Partner"; // Or ticket.getTrip().getBusCompany().getName();
        String routeName = "Tuyến xe";              // Or ticket.getTrip().getRoute().getName();
        String departureTime = formatDateTime(ticket.getPickupTime());
        
        // Dummy values for missing fields in Ticket (Implement these based on your Trip entity)
        String pickupTime = formatDateTime(ticket.getPickupTime());
        String pickupLocation = ticket.getPickupTripStop().getStation().getName();
        String pickupAddress = ticket.getPickupTripStop().getFullAddress();
        String dropoffTime = formatDateTime(ticket.getDropoffTime());
        String dropoffLocation = ticket.getDropoffTripStop().getStation().getName();
        String dropoffAddress = ticket.getDropoffTripStop().getFullAddress();

        if (ticket.getTrip() != null) {
             // Example assumptions:
             operatorName = ticket.getTrip().getOperator().getName();
             routeName = ticket.getTrip().getRoute().getOrigin() + " - " + ticket.getTrip().getRoute().getDestination();
        }

        String paymentMethod = "Thanh toán trực tuyến";
        String paymentStatus = "Đã thanh toán";

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; background-color: #f4f4f4; margin: 0; padding: 20px; }
                        .container { max-width: 600px; margin: 0 auto; background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }
                        .header { text-align: center; margin-bottom: 20px; border-bottom: 2px solid #0056b3; padding-bottom: 10px; }
                        .logo { font-size: 28px; font-weight: bold; color: #0056b3; letter-spacing: 2px; }
                        .title { font-size: 20px; font-weight: bold; color: #28a745; margin-top: 10px; }
                        .section-title { background-color: #f8f9fa; padding: 8px; font-weight: bold; border-left: 4px solid #0056b3; margin-top: 20px; margin-bottom: 10px; }
                        .info-row { display: flex; justify-content: space-between; margin-bottom: 5px; }
                        .info-label { color: #666; width: 140px; font-weight: 500; }
                        .info-value { font-weight: bold; flex: 1; text-align: right; color: #000; }
                        .highlight { color: #d9534f; font-weight: bold; }
                        .instruction { background-color: #e9ecef; padding: 10px; border-radius: 4px; font-size: 14px; color: #495057; }
                        .footer { text-align: center; margin-top: 30px; font-size: 12px; color: #888; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <div class="logo">VEXESIEURE</div>
                            <div class="title">Xác nhận thanh toán thành công</div>
                        </div>

                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Cảm ơn quý khách đã tin tưởng sử dụng dịch vụ của Vexesieure.<br>
                        Vexesieure xác nhận quý khách đã thanh toán thành công đơn hàng xe khách của nhà xe <strong>%s</strong>.</p>

                        <div class="section-title">Thông tin thanh toán</div>
                        <table width="100%%" cellpadding="5">
                            <tr><td class="info-label">Trạng thái:</td><td class="info-value" style="color: green;">%s</td></tr>
                            <tr><td class="info-label">Phương thức:</td><td class="info-value">%s</td></tr>
                            <tr><td colspan="2"><hr style="border: 0; border-top: 1px dashed #ccc;"></td></tr>
                            <tr><td class="info-label"><strong>Tổng tiền:</strong></td><td class="info-value highlight">%s VND</td></tr>
                        </table>

                        <div class="section-title">Hướng dẫn trước chuyến đi</div>
                        <div class="instruction">
                            <strong>Quý khách hãy ra điểm đón trước 30 phút,</strong> đưa vé điện tử trên website/ứng dụng Vexesieure hoặc email xác nhận thanh toán cho nhân viên phòng vé để đổi chứng từ giấy (nếu có) và xuất trình cho tài xế hoặc phụ xe khi lên xe.
                        </div>

                        <div class="section-title">Thông tin vé</div>
                        <table width="100%%" cellpadding="5">
                            <tr><td class="info-label">Mã vé:</td><td class="info-value">%s</td></tr>
                            <tr><td class="info-label">Hãng xe:</td><td class="info-value">%s</td></tr>
                            <tr><td class="info-label">Số lượng:</td><td class="info-value">%d vé</td></tr>
                            <tr><td class="info-label">Số ghế/giường:</td><td class="info-value">%s</td></tr>
                            <tr><td class="info-label">Tuyến đường:</td><td class="info-value">%s</td></tr>
                            <tr><td class="info-label">Giờ khởi hành:</td><td class="info-value">%s</td></tr>
                        </table>

                        <div class="section-title">Thông tin lộ trình</div>
                        <table width="100%%" cellpadding="5">
                            <tr>
                                <td class="info-label">Giờ đón (dự kiến):</td>
                                <td class="info-value">%s</td>
                            </tr>
                            <tr>
                                <td class="info-label">Điểm đón:</td>
                                <td class="info-value">%s<br><span style="font-weight: normal; font-size: 13px; color: #555;">%s</span></td>
                            </tr>
                            <tr>
                                <td colspan="2" style="height: 10px;"></td>
                            </tr>
                            <tr>
                                <td class="info-label">Giờ trả (dự kiến):</td>
                                <td class="info-value">%s</td>
                            </tr>
                            <tr>
                                <td class="info-label">Điểm trả:</td>
                                <td class="info-value">%s<br><span style="font-weight: normal; font-size: 13px; color: #555;">%s</span></td>
                            </tr>
                        </table>

                        <div class="section-title">Thông tin hành khách</div>
                        <table width="100%%" cellpadding="5">
                            <tr><td class="info-label">Họ và tên:</td><td class="info-value">%s</td></tr>
                            <tr><td class="info-label">Email:</td><td class="info-value">%s</td></tr>
                            <tr><td class="info-label">Số điện thoại:</td><td class="info-value">%s</td></tr>
                        </table>

                        <div class="footer">
                            <p>&copy; 2025 Vexesieure. All rights reserved.</p>
                            <p>Đây là email tự động, vui lòng không trả lời email này.</p>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(
                        contactName,
                        operatorName,
                        paymentStatus,
                        paymentMethod,
                        totalAmount,
                        ticketCode,
                        operatorName,
                        quantity,
                        seatNumbers,
                        routeName,
                        departureTime,
                        pickupTime,
                        pickupLocation,
                        pickupAddress,
                        dropoffTime,
                        dropoffLocation,
                        dropoffAddress,
                        contactName,
                        contactEmail,
                        contactPhone
                    );
    }
}