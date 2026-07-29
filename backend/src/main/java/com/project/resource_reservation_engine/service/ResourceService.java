package com.project.resource_reservation_engine.service;

import com.project.resource_reservation_engine.dto.CreateResourceRequest;
import com.project.resource_reservation_engine.dto.ResourceResponse;
import com.project.resource_reservation_engine.entity.Resource;
import com.project.resource_reservation_engine.exception.ResourceNotFoundException;
import com.project.resource_reservation_engine.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;

    public ResourceResponse createResource(CreateResourceRequest request) {
        Resource resource = new Resource();
        resource.setName(request.getName());
        resource.setCapacity(request.getCapacity());

        Resource saved = resourceRepository.save(resource);
        return toResponse(saved);
    }

    public ResourceResponse getAvailability(Long id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
        return toResponse(resource);
    }

    private ResourceResponse toResponse(Resource resource) {
        return new ResourceResponse(
                resource.getId(),
                resource.getName(),
                resource.getCapacity(),
                resource.getCreatedAt()
        );
    }
}