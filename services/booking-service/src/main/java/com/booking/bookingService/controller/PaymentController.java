package com.booking.bookingService.controller;

import com.booking.bookingService.service.PaymentService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.webhooks.WebhookData;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PayOS payOS;

    // API tạo link thanh toán
    @PostMapping("/create-link")
    public ResponseEntity<?> createPaymentLink(@RequestBody Map<String, String> request) {
        try {
            if (!request.containsKey("ticketId")) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing ticketId"));
            }

            UUID ticketId = UUID.fromString(request.get("ticketId"));
            String returnUrl = request.getOrDefault("returnUrl", "http://localhost:3000/success");
            String cancelUrl = request.getOrDefault("cancelUrl", "http://localhost:3000/cancel");

            // Gọi service trả về Response chuẩn của SDK 2.x
            CreatePaymentLinkResponse data = paymentService.createPaymentLink(ticketId, returnUrl, cancelUrl);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "checkoutUrl", data.getCheckoutUrl(),
                "qrCode", data.getQrCode(),
                "orderCode", data.getOrderCode()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // API Webhook (PayOS gọi vào đây)
    @PostMapping("/payos-webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody ObjectNode body) {
        try {
            // SDK 2.x hỗ trợ verify trực tiếp từ Object/ObjectNode
            // Hàm này sẽ ném Exception nếu Signature không khớp
            WebhookData data = payOS.webhooks().verify(body);

            if (data.getOrderCode() == 123) {
                System.out.println("Webhook Test (123) received - Ignored DB processing");
                return ResponseEntity.ok(Map.of("error", 0, "message", "Webhook test success", "data", data));
            }
            // Xử lý nghiệp vụ
            paymentService.processWebhook(data);

            return ResponseEntity.ok(Map.of("error", 0, "message", "Webhook processed", "data", data));

        } catch (Exception e) {
            e.printStackTrace();
            // Luôn trả về 200 OK để PayOS không retry, nhưng kèm message lỗi
            return ResponseEntity.ok(Map.of("error", -1, "message", e.getMessage(), "data", null));
        }
    }

    @GetMapping("/ticket/{ticketId}")
    public ResponseEntity<?> getPaymentInfo(@PathVariable UUID ticketId) {
        try {
            // 1. Lấy entity Payment từ DB
            com.booking.bookingService.model.Payment payment = paymentService.getPaymentByTicketId(ticketId);

            // 2. Map sang DTO hoặc Map đơn giản để trả về JSON (Tránh lỗi vòng lặp Jackson nếu trả entity gốc)
            Map<String, Object> responseData = new java.util.HashMap<>();
            responseData.put("paymentId", payment.getId());
            responseData.put("ticketCode", payment.getTicket().getTicketCode());
            responseData.put("amount", payment.getAmount());
            responseData.put("status", payment.getStatus()); // QUAN TRỌNG: PENDING | PAID | PAID_LATE
            responseData.put("createdAt", payment.getCreatedAt());
            responseData.put("paidAt", payment.getPaidAt());
            
            // Nếu cần thiết, FE có thể dùng orderCode này để gọi PayOS check chéo (tùy chọn)

            return ResponseEntity.ok(Map.of(
                "success", true, 
                "data", responseData
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}