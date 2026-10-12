package com.suwon.festival.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.suwon.festival.dto.EventResponse;
import com.suwon.festival.dto.SyncResult;
import com.suwon.festival.service.EventService;
import com.suwon.festival.service.EventSyncService;
import com.suwon.festival.dto.xml.EventItem;
import org.springframework.web.bind.annotation.PostMapping;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

  private final EventService eventService;
  private final EventSyncService eventSyncService;

  @GetMapping(produces = "application/json")
  public List<EventResponse> getEvents() {
    return eventService.getAllEvents();
  }

  @GetMapping("/sync/raw")
  public String syncRaw() {
    return eventSyncService.fetchRawXml();
  }

  // @GetMapping("/sync/parsed")
  // public List<EventItem> syncParsed() throws Exception {
  // return eventSyncService.fetchEventItems();
  // }

  @GetMapping(value = "/sync/parsed", produces = "application/json")
  public List<EventItem> syncParsed() throws Exception {
    return eventSyncService.fetchEventItems();
  }

  @PostMapping("/sync")
  public String sync() throws Exception {
    SyncResult result = eventSyncService.syncEvents();
    return "새 행사 " + result.saved() + "개 저장 · 기존 행사 " + result.updated() + "개 갱신 완료";
  }
}