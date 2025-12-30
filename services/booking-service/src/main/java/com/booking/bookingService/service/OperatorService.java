package com.booking.bookingService.service;

import com.booking.bookingService.dto.OperatorRequest;
import com.booking.bookingService.exception.ResourceNotFoundException;
import com.booking.bookingService.model.Operator;
import com.booking.bookingService.repository.OperatorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class OperatorService {
    private final OperatorRepository operatorRepository;
    private final CloudinaryService cloudinaryService;

    public Operator createOperator(OperatorRequest request, MultipartFile file) {
        String imageUrl = null;
        
        // Upload image if provided
        if (file != null && !file.isEmpty()) {
            imageUrl = cloudinaryService.uploadImage(file);
        }

        Operator operator = Operator.builder()
                .name(request.getName())
                .image(imageUrl)
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .build();
        return operatorRepository.save(operator);
    }

    public List<Operator> getAllOperators() {
        return operatorRepository.findAll();
    }

    public Operator getOperator(UUID id) {
        return operatorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));
    }

    public Operator updateOperator(UUID id, OperatorRequest request, MultipartFile file) {
        Operator operator = getOperator(id);
        
        // 1. Check if name is provided in the request before updating
        if (request.getName() != null) {
            operator.setName(request.getName()); 
        }

        // 2. Check if email is provided
        if (request.getContactEmail() != null) {
            operator.setContactEmail(request.getContactEmail());
        }

        // 3. Check if phone is provided
        if (request.getContactPhone() != null) {
            operator.setContactPhone(request.getContactPhone());
        }

        // Only update image if a new file is provided
        if (file != null && !file.isEmpty()) {
            String imageUrl = cloudinaryService.uploadImage(file);
            operator.setImage(imageUrl);
        } else if (request.getImage() != null) {
            // Optional: Handle case where user sends URL string directly (e.g. keeping old one)
            operator.setImage(request.getImage());
        }

        return operatorRepository.save(operator);
    }

    public void deleteOperator(UUID id) {
        if (!operatorRepository.existsById(id)) {
            throw new ResourceNotFoundException("Operator not found");
        }
        operatorRepository.deleteById(id);
    }
}