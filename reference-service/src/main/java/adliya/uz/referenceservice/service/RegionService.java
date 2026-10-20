package adliya.uz.referenceservice.service;

import adliya.uz.referenceservice.entity.Region;
import adliya.uz.referenceservice.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class RegionService {

    private final RegionRepository regionRepository;

    public List<Region> getAll() {
        return regionRepository.findAll();
    }

    public Region getById(Long id) {
        return regionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Region not found, ID: " + id));
    }
}
