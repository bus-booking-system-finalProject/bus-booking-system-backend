package com.booking.bookingService.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisLockService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Thử lấy lock cho một key.
     * @param key Key định danh (ví dụ: lock:seat:trip_1:A1)
     * @param value Giá trị (thường là userId hoặc transactionId)
     * @param timeout Thời gian giữ lock (giây)
     * @return true nếu lấy được lock, false nếu đã có người giữ
     */
    public boolean tryLock(String key, String value, long timeout) {
        // Lệnh SETNX: Chỉ set nếu key chưa tồn tại
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, value, timeout, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    public void unlock(String key) {
        redisTemplate.delete(key);
    }

    // Lấy ID người đang giữ lock (để check xem có phải chính user đó không)
    public String getLockOwner(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // Gia hạn thời gian lock (dùng khi user đang điền form hoặc vừa tạo vé xong)
    public void refreshLock(String key, long timeoutSeconds) {
        redisTemplate.expire(key, timeoutSeconds, TimeUnit.SECONDS);
    }
}