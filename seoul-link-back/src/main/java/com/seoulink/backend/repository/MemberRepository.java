package com.seoulink.backend.repository;

import com.seoulink.backend.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);

    Optional<Member> findByEmail(String email);
    Optional<Member> findBySocialProviderAndSocialId(String socialProvider, String socialId);
}
