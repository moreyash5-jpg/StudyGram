
package com.studygram.repository;

import com.studygram.model.ResourceLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceLikeRepository
        extends JpaRepository<ResourceLike, Long> {

    boolean existsByUserEmailAndResourceId(
            String userEmail,
            Long resourceId
    );
}
