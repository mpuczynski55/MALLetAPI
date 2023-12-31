package com.agh.mallet.domain.user.user.control.repository;

import com.agh.mallet.domain.user.user.entity.ConfirmationTokenJPAEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface ConfirmationTokenRepository extends CrudRepository<ConfirmationTokenJPAEntity, Long> {

    Optional<ConfirmationTokenJPAEntity> findByToken(String token);
}
