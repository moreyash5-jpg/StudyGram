
package com.studygram.model;

import jakarta.persistence.*;

@Entity
@Table(
    name = "resource_likes",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "unique_user_resource_like",
            columnNames = {"user_email", "resource_id"}
        )
    }
)
public class ResourceLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    public ResourceLike() {
    }

    public ResourceLike(String userEmail, Long resourceId) {
        this.userEmail = userEmail;
        this.resourceId = resourceId;
    }
}
