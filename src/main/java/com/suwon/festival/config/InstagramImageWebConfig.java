package com.suwon.festival.config;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.suwon.festival.service.instagram.InstagramImageStore;

// 저장한 인스타 사진을 /media/insta/{게시물id}.jpg 로 내보냄 (설계 docs/instagram-images.md)
//  - 저장 폴더가 설정됐을 때만 (instagram.image-dir). 폴더 밖 파일은 Spring 이 막음 (../ 등)
//  - 사진 확장자만 내보냄 (받는 중인 .part 등 다른 파일은 404)
//  - 같은 이름이면 같은 사진(게시물 id) → 브라우저가 7일 동안 다시 받지 않게
@Configuration
public class InstagramImageWebConfig implements WebMvcConfigurer {

  private final InstagramImageStore imageStore;

  public InstagramImageWebConfig(InstagramImageStore imageStore) {
    this.imageStore = imageStore;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    imageStore.directory().ifPresent(dir -> registry.addResourceHandler(InstagramImageStore.PUBLIC_PREFIX + "*.jpg",
            InstagramImageStore.PUBLIC_PREFIX + "*.png", InstagramImageStore.PUBLIC_PREFIX + "*.webp")
        .addResourceLocations(dir.toUri().toString())
        .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic()));
  }
}
