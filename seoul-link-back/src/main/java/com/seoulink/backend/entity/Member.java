package com.seoulink.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "MEMBER")
@Getter
@Setter
@NoArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MEMBER_ID")
    private Long memberId;

    @Column(name = "EMAIL", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "PASSWORD", length = 255)
    private String password;

    @Column(name = "NAME", nullable = false, length = 50)
    private String name;

    @Column(name = "NICKNAME", unique = true, length = 50)
    private String nickname;

    @Column(name = "PHONE", length = 20)
    private String phone;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "LOGIN_TYPE", nullable = false, length = 20)
    private String loginType = "LOCAL";

    @Column(name = "SOCIAL_PROVIDER", length = 30)
    private String socialProvider;

    @Column(name = "SOCIAL_ID", length = 100)
    private String socialId;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Member(String email, String password, String name, String nickname) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.nickname = nickname;
        this.status = "ACTIVE";
        this.loginType = "LOCAL";
    }
}
