package com.suwon.festival.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
회원번호 id
가입일 createdAt
로그인은 소셜에게 맡기고, 로그인에 필요한 최소값(LoginMethod 의 식별값)만 보관 (V4)
*/

@Entity
@Table(name = "member")
@Getter
@EntityListeners(AuditingEntityListener.class) // ??
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 회원번호

    public static Member create() {
        return new Member();
    }

    @CreatedDate // 저장될때 자동으로 시간 기입
    @Column(nullable = false) // 공란 금지
    private LocalDateTime createdAt;

}
