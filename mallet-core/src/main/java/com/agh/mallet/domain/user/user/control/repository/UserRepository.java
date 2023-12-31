package com.agh.mallet.domain.user.user.control.repository;

import com.agh.mallet.domain.group.entity.GroupJPAEntity;
import com.agh.mallet.domain.set.entity.SetJPAEntity;
import com.agh.mallet.domain.user.user.entity.UserJPAEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserJPAEntity, Long>, JpaSpecificationExecutor<UserJPAEntity> {

    Optional<UserJPAEntity> findByEmailIgnoreCase(String email);

    long countAllByUsername(String username);

    List<UserJPAEntity> findAllByUsernameContainingIgnoreCaseAndEnabled(String username, boolean enabled);

    @Query("SELECT s FROM UserJPAEntity u INNER JOIN u.userSets s WHERE u.email = ?1")
    Page<SetJPAEntity> findAllSetsByUserEmail(String email, Pageable pageable);

    @Query("SELECT DISTINCT g FROM GroupJPAEntity g JOIN g.contributions c JOIN c.contributor u WHERE u.email = ?1")
    Page<GroupJPAEntity> findAllGroupsByUserEmail(String email, Pageable pageable);

    boolean existsByIdentifier(String identifier);

}