package com.studygram.repository;

import com.studygram.model.Resource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceRepository extends JpaRepository<Resource, Long> {

    List<Resource> findByCategoryOrderByUploadDateDesc(String category);
}