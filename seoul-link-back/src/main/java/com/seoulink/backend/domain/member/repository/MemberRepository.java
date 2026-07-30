package com.seoulink.backend.domain.member.repository;

import com.seoulink.backend.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);

    Optional<Member> findByEmail(String email);
    Optional<Member> findByEmailIgnoreCaseAndName(String email, String name);
    Optional<Member> findBySocialProviderAndSocialId(String socialProvider, String socialId);

}
