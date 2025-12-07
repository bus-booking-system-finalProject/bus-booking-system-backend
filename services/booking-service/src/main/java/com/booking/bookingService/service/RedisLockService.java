package com.booking.bookingService.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
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

    public boolean isLocked(String key) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(key));
}
    
    // Gia hạn lock nếu cần (cho bước thanh toán)
    public void extendLock(String key, long timeout) {
        redisTemplate.expire(key, timeout, TimeUnit.SECONDS);
    }
}