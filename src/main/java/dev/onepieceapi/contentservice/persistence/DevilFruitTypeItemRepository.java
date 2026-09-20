package dev.onepieceapi.contentservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DevilFruitTypeItemRepository extends JpaRepository<DevilFruitTypeItemEntity, UUID> {

}
