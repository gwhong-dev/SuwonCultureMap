package com.suwon.festival.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suwon.festival.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
