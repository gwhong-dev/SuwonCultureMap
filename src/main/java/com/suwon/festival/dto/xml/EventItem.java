package com.suwon.festival.dto.xml;

public class EventItem {
  public Long idx;
  public String suwonYn;
  public String category;
  public String title;
  public Photos photos;
  public Coordinate coordinate;
  public EventDate eventDate;
  public ContentInfo contentInfo;
  // description은 일부러 안 받음: 안에 이스케이프 안 된 HTML 태그(div/table/img 등)가
  // 그대로 들어있어서, String 필드로 억지로 묶으려다 바로 다음 형제 태그(link)까지
  // 파싱이 꼬이는 문제가 있었음. 매핑 자체를 빼면 Jackson이 "모르는 태그"로 보고
  // 통째로 안전하게 건너뛰어서 뒤 이어지는 link/pubDate가 정상적으로 파싱됨
  public String link;
  public String totalReceiptUrl;
  public String pubDate;
}