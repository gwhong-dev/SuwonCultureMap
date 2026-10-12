package com.suwon.festival.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suwon.festival.entity.LoginMethod;

public interface LoginMethodRepository extends JpaRepository<LoginMethod, Long> {
  Optional<LoginMethod> findByProviderAndProviderId(String provider, String providerId);

  List<LoginMethod> findByMemberId(Long memberId); // 마이페이지에서 "연동된 소셜 계정 목록" 보여줄 때 사용
}