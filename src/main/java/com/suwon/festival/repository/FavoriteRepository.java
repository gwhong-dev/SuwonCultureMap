package com.suwon.festival.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suwon.festival.entity.Favorite;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
  boolean existsByMemberIdAndEventId(Long memberId, Long eventId);

  Optional<Favorite> findByMemberIdAndEventId(Long memberId, Long eventId);

  List<Favorite> findByMemberId(Long memberId);

  void deleteByMemberId(Long memberId); // 회원 탈퇴

  List<Favorite> findByEventId(Long eventId);
}