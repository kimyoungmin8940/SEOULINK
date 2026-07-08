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

    @Column(name = "EMAIL", nullable = false, unique = true)
    private String email;

    @Column(name = "PASSWORD")
    private String password;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "NICKNAME", unique = true)
    private String nickname;

    @Column(name = "PHONE")
    private String phone;

    @Column(name = "STATUS", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "LOGIN_TYPE", nullable = false)
    private String loginType = "LOCAL";

    @Column(name = "SOCIAL_PROVIDER")
    private String socialProvider;

    @Column(name = "SOCIAL_ID")
    private String socialId;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Member(String email, String password, String name, String nickname, String phone) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.nickname = nickname;
        this.phone = phone;
        this.status = "ACTIVE";
        this.loginType = "LOCAL";
    }
}